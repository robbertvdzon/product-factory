package nl.vdzon.productfactory.roadmap

import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.product.CreateProductRequest
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.roadmap.api.LivingVisionCatalog
import nl.vdzon.productfactory.roadmap.api.RoadmapSessionRepository
import nl.vdzon.productfactory.roadmap.api.RoadmapVisionCatalog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(properties = ["product-factory.roadmap.v2-parallelism=4", "product-factory.roadmap.v2-max-attempts=2"])
@Import(RoadmapProcessOrchestratorTest.Fakes::class)
class RoadmapProcessOrchestratorTest(
    @Autowired private val products: ProductCatalog,
    @Autowired private val sessions: RoadmapSessionRepository,
    @Autowired private val orchestrator: RoadmapProcessOrchestrator,
    @Autowired private val visions: RoadmapVisionCatalog,
    @Autowired private val catalog: LivingVisionCatalog,
    @Autowired private val dispatch: TrackingDispatch,
) {
    private lateinit var slug: String

    @BeforeEach
    fun createProduct() {
        slug = "orchestrator-${UUID.randomUUID().toString().take(8)}"
        products.create(
            CreateProductRequest(
                slug, "Orchestratortest", "Maak een generieke productvisie aantoonbaar concreet",
                status = "active", roadmapProcessVersion = LIVING_VISION_PROCESS_VERSION,
            ).configuration(),
        )
        dispatch.reset()
    }

    @Test
    fun `full graph runs scouts in parallel respects dependencies and activates once`() {
        val session = sessions.create(slug)

        orchestrator.run(session.id)

        val steps = catalog.steps(session.id)
        assertEquals(13, steps.size)
        assertTrue(steps.all { it.status.name in setOf("COMPLETED", "SKIPPED") })
        assertTrue(dispatch.maxActive.get() >= 3, "De drie scouts moeten aantoonbaar overlappen")
        val latestScoutEnd = dispatch.ended.filterKeys { it in SCOUT_TASKS }.values.max()
        assertTrue(dispatch.started.getValue("roadmap-vision-curator") >= latestScoutEnd)
        assertNotNull(visions.current(slug))
        assertEquals(2, catalog.ideas(slug).size)
        assertTrue(catalog.concepts(slug).isNotEmpty())
        catalog.concepts(slug).forEach { concept ->
            val frames = catalog.conceptVersions(slug, concept.conceptKey)
            assertEquals(2, frames.size)
            assertEquals(setOf("MOBILE", "DESKTOP"), frames.map { it.viewport }.toSet())
            assertEquals(setOf(1), frames.map { it.version }.toSet())
        }
        assertTrue(catalog.research(slug).isNotEmpty())
        val uxHandoffs = steps.filter { it.role == "ux-concept" }.mapNotNull { it.handoff }
        assertTrue(uxHandoffs.all { "base64Content" !in it.payload.toString() })
        val directorPrompt = dispatch.tasks.single { it.taskType == "roadmap-ux-director" }.prompt
        assertTrue(ONE_PIXEL_PNG !in directorPrompt, "Downstream prompts mogen geen opgeslagen beeldbytes bevatten")

        val callsAfterFirstRun = dispatch.calls.get()
        orchestrator.run(session.id)
        assertEquals(callsAfterFirstRun, dispatch.calls.get(), "Een dubbel event mag geen tweede agentrun starten")
        assertEquals(1, visions.history(slug).size)
    }

    @Test
    fun `required failure retries and leaves active vision untouched`() {
        dispatch.failRole.set("roadmap-vision-critic")
        val session = sessions.create(slug)

        assertFailsWith<IllegalStateException> { orchestrator.run(session.id) }

        assertNull(visions.current(slug))
        val critic = catalog.steps(session.id).single { it.role == "vision-critic" }
        assertEquals("FAILED", critic.status.name)
        assertEquals(2, critic.attempt)
        assertTrue(catalog.steps(session.id).single { it.role == "activation" }.status.name == "PENDING")
    }

    @Test
    fun `director revises one concept from research and preserves the other flow`() {
        dispatch.reviseConcept.set(1)
        val session = sessions.create(slug)

        orchestrator.run(session.id)

        val revised = catalog.concepts(slug).single { it.conceptKey == "concept-1" }
        val unchanged = catalog.concepts(slug).single { it.conceptKey == "concept-2" }
        assertEquals(2, revised.currentVersion)
        assertEquals(1, unchanged.currentVersion)
        val revisionFrames = catalog.conceptVersions(slug, revised.conceptKey).filter { it.version == 2 }
        assertEquals(setOf("MOBILE", "DESKTOP"), revisionFrames.map { it.viewport }.toSet())
        assertTrue(revisionFrames.all { "Onderzoeksbewijs" in it.review.orEmpty() })
        assertTrue(revisionFrames.all { it.assets.size == 1 })
    }

    @Test
    fun `director supplied correction is applied before an initial disapproval can block`() {
        dispatch.reviseConcept.set(2)
        val session = sessions.create(slug)

        orchestrator.run(session.id)

        assertEquals("COMPLETED", sessions.require(slug, session.id).status)
        assertEquals(2, catalog.concepts(slug).single { it.conceptKey == "concept-1" }.currentVersion)
    }

    @Test
    fun `critic gets one bounded strategy correction and approves the corrected vision`() {
        dispatch.criticRequiresCorrection.set(1)
        val session = sessions.create(slug)

        orchestrator.run(session.id)

        assertEquals("COMPLETED", sessions.require(slug, session.id).status)
        val critic = catalog.steps(session.id).single { it.role == "vision-critic" }.handoff
        assertEquals(1, critic?.payload?.get("correctionRound"))
        assertNotNull(critic?.payload?.get("correctedStrategy"))
        assertEquals(14, dispatch.calls.get(), "De correctieronde mag exact één strateeg- en één criticusaanroep toevoegen")
    }

    @TestConfiguration
    class Fakes {
        @Bean
        @Primary
        fun trackingDispatch() = TrackingDispatch()
    }

    class TrackingDispatch : AgentDispatchPort {
        val active = AtomicInteger()
        val maxActive = AtomicInteger()
        val calls = AtomicInteger()
        val reviseConcept = AtomicInteger()
        val criticRequiresCorrection = AtomicInteger()
        val failRole = AtomicReference<String?>(null)
        val started = ConcurrentHashMap<String, Long>()
        val ended = ConcurrentHashMap<String, Long>()
        val tasks = ConcurrentLinkedQueue<AgentTask>()

        fun reset() {
            active.set(0)
            maxActive.set(0)
            calls.set(0)
            reviseConcept.set(0)
            criticRequiresCorrection.set(0)
            failRole.set(null)
            started.clear()
            ended.clear()
            tasks.clear()
        }

        override fun execute(task: AgentTask): AgentResult {
            tasks.add(task)
            calls.incrementAndGet()
            started.putIfAbsent(task.taskType, System.nanoTime())
            val nowActive = active.incrementAndGet()
            maxActive.accumulateAndGet(nowActive, ::maxOf)
            try {
                if (task.taskType in SCOUT_TASKS) Thread.sleep(100)
                if (failRole.get() == task.taskType) return AgentResult(task.runId, "FAILED", "Opzettelijke verplichte fout")
                return AgentResult(task.runId, "COMPLETED", response(task))
            } finally {
                active.decrementAndGet()
                ended[task.taskType] = System.nanoTime()
            }
        }

        private fun response(task: AgentTask): String = when (task.taskType) {
            "roadmap-product-market-scout", "roadmap-domain-source-scout", "roadmap-wild-ideas" -> discovery(task.taskType)
            "roadmap-vision-curator" -> curator()
            "roadmap-ux-concept" -> ux(if ("selection-2" in task.prompt) 2 else 1)
            "roadmap-feasibility" -> feasibility(if ("selection-2" in task.prompt) 2 else 1)
            "roadmap-ux-director" -> director()
            "roadmap-future-strategist" -> strategy()
            "roadmap-vision-critic" -> if (criticRequiresCorrection.get() == 1 && "critic-rereview" !in task.runId) {
                review(false, "Een kernidee ontbreekt en vereist één correctieronde.")
            } else {
                review(true, "De visie behoudt kernideeën, bewijs en onzekerheid.")
            }
            "roadmap-manager" -> manager()
            else -> error("Onverwacht taaktype ${task.taskType}")
        }

        private fun discovery(role: String) = """{"summary":"Onderzoek met herleidbare bronnen door $role.","sources":[{"url":"https://example.test/${role.removePrefix("roadmap-")}","title":"Externe inspiratie","accessedAt":"${Instant.parse("2026-08-17T10:00:00Z")}","observation":"De bron toont een concreet interactiepatroon.","interpretation":"Het patroon kan een relevante richting aanwijzen.","relevance":"Sluit aan op de aangeleverde missie.","limitations":"Nog niet met gebruikers getoetst.","confidence":70}],"ideas":[],"surprisingPossibilities":["Een onverwachte maar productpassende mogelijkheid die nader onderzocht kan worden."]}"""

        private fun curator() = """{"summary":"Twee stabiele ideeën zijn geselecteerd voor verdieping.","ideas":[${idea(1)},${idea(2)}],"surprisingPossibilities":["Een aanvullende mogelijkheid blijft als vonk bewaard."]}"""

        private fun idea(index: Int) = """{"action":"${if (index == 1) "REFINE" else "CREATE"}","ideaKey":"idee-$index","promise":"Een concrete en onderscheidende productbelofte voor richting $index.","primaryAudience":"De primaire gebruikersgroep van het geselecteerde product.","need":"Een aantoonbare behoefte die onafhankelijk blijft van de eerste oplossing.","reason":"Geselecteerd op productfit, bewijs en leerwaarde.","evidence":"De scouts leverden relevante maar nog onzekere aanwijzingen.","mergedIdeaKeys":[],"selectUx":true,"researchQuestions":["Welke randvoorwaarde bepaalt de haalbaarheid?"]}"""

        private fun ux(index: Int) = """{"summary":"Mobiele en desktopflow voor idee $index.","ideaKey":"idee-$index","conceptKey":"concept-$index","userGoal":"De gebruiker voltooit zelfstandig een betekenisvol kerndoel.","interaction":"De flow biedt een duidelijke start, keuze, bevestiging en hersteltoestand.","content":"Deterministische interface-tekst met begrijpelijke labels en terugkoppeling.","states":["loading","empty","success","error"],"decisions":["Toetsenbordvolgorde volgt de visuele volgorde."],"assumptions":["De gekozen woorden zijn begrijpelijk."],"openQuestions":["Welke uitleg is in gebruikersonderzoek nodig?"],"generatedImages":[${image(index, "MOBILE", 0)},${image(index, "DESKTOP", 1)}]}"""

        private fun image(index: Int, viewport: String, position: Int) = """{"base64Content":"$ONE_PIXEL_PNG","mediaType":"image/png","filename":"concept-$index-${viewport.lowercase()}.png","altText":"$viewport concept voor idee $index","viewport":"$viewport","flowPosition":$position}"""

        private fun feasibility(index: Int) = """{"summary":"Gericht haalbaarheidsonderzoek voor idee $index.","results":[{"ideaKey":"idee-$index","conceptKey":null,"capabilityKey":null,"researchType":"TECHNOLOGY","question":"Welke technische randvoorwaarde bepaalt een veilige proef?","evidence":"Een beperkte technische verkenning toont een uitvoerbare richting met resterende onzekerheid.","sources":["https://example.test/evidence-$index"],"limitations":"Nog niet op productieschaal getest.","confidence":75,"status":"TESTING","conclusion":"De richting is testbaar en niet fundamenteel onmogelijk.","recommendedNextStep":"Voer een begrensde productproef uit.","dependencies":["prototype-omgeving"]}]}"""

        private fun review(approved: Boolean, summary: String) = """{"summary":"$summary","approved":$approved,"revisionRequests":[]}"""

        private fun director(): String {
            val revisions = if (reviseConcept.get() > 0) {
                """[{"conceptKey":"concept-1","reason":"Onderzoek vraagt zichtbare onzekerheid in de flow.","evidenceImpact":"Onderzoeksbewijs maakt een herstelroute en onzekerheidslabel noodzakelijk.","userGoal":"De gebruiker voltooit het kerndoel en begrijpt resterende onzekerheid.","interaction":"De flow biedt keuze, bevestiging, zichtbare onzekerheid en een herstelroute.","content":"Begrijpelijke interface-inhoud benoemt resultaat, onzekerheid en vervolgstap.","states":["loading","success","uncertain","error"],"decisions":["Maak onderzoeksbeperking zichtbaar."],"assumptions":["De uitleg is begrijpelijk."],"openQuestions":["Welke formulering geeft voldoende vertrouwen?"]}]"""
            } else {
                "[]"
            }
            val approved = reviseConcept.get() != 2
            return """{"summary":"De flows vormen één consistente en toegankelijke ervaring.","approved":$approved,"revisionRequests":${if (approved) "[]" else "[\"Pas de concrete gerichte revisie toe.\"]"},"conceptRevisions":$revisions}"""
        }

        private fun strategy(): String {
            val experiences = (1..8).joinToString(",") { """{"key":"ervaring-$it","title":"Ervaring $it","promise":"Een concrete toekomstige productbelofte met aantoonbare waarde.","scenario":"Een gebruiker doorloopt een complete, begrijpelijke flow en bereikt zelfstandig een betekenisvol resultaat.","wowFactor":"De ervaring verbindt behoeften en mogelijkheden op een onderscheidende manier."}""" }
            val capabilities = (1..6).joinToString(",") { """{"key":"capability-$it","title":"Capability $it","outcome":"De gebruiker bereikt aantoonbaar het bedoelde productresultaat.","successMeasure":"Een meetbare end-to-endroute slaagt in de gebruiksproef.","horizon":"${listOf("NOW", "NEXT", "LATER", "HORIZON")[(it - 1) % 4]}","experienceKeys":["ervaring-$it"],"feasibility":"${if (it == 1) "PROVEN" else "UNKNOWN"}"}""" }
            val screens = (1..3).joinToString(",") { """{"key":"scherm-$it","title":"Conceptscherm $it","viewport":"DESKTOP","eyebrow":"Ontdek","headline":"Een heldere toekomstige ervaring","body":"De gebruiker ziet begrijpelijke inhoud, acties en zichtbare terugkoppeling in één complete flow.","primaryAction":"Ga verder","secondaryAction":"Bekijk uitleg","visualDescription":"Een rustig en toegankelijk scherm met duidelijke hiërarchie en consistente navigatie.","highlights":["Duidelijke status","Toegankelijke bediening"]}""" }
            return """{"northStarTitle":"Een levende en concrete producthorizon","northStar":"Iedere gebruiker kan vanuit een herkenbare behoefte een betrouwbare en betekenisvolle productervaring voltooien.","futureNarrative":"De toekomstige ervaring begint bij een helder doel en bouwt stap voor stap vertrouwen op. Inhoud, interactie en bewijs blijven zichtbaar verbonden, onzekerheid wordt eerlijk getoond en iedere route biedt begrijpelijk herstel. Zo groeit het product zonder zijn kernbelofte of eerder geleerde lessen te verliezen.","experiences":[$experiences],"capabilities":[$capabilities],"assumptions":[{"key":"begrijpelijke-flow","statement":"De gekozen flow en terminologie zijn begrijpelijk voor de primaire gebruikersgroep.","risk":"Onduidelijkheid verhindert dat gebruikers zelfstandig het resultaat bereiken.","probeType":"UX_PROTOTYPE","proposedProbe":"Test de volledige mobiele en desktopflow met representatieve gebruikers en leg uitval vast.","capabilityKeys":["capability-1"],"feasibility":"TESTING"}],"conceptScreens":[$screens],"visionChangeSummary":"Twee gecureerde ideeën zijn met UX en onderzoek tot één bewijsbare horizon samengebracht."}"""
        }

        private fun manager() = """{"summary":"De gevalideerde capability is atomair vertaald naar de roadmap.","epicUpdates":[{"action":"CREATE","epicId":null,"title":"Complete kernflow","description":"Lever de gevalideerde end-to-end gebruikersflow met toegankelijke toestanden.","processRank":1,"dependencyIds":[],"horizon":"NOW","kind":"DELIVERY","capabilityKey":"capability-1"}],"settledQuestions":[],"bugUpdates":[]}"""
    }

    companion object {
        private val SCOUT_TASKS = setOf("roadmap-product-market-scout", "roadmap-domain-source-scout", "roadmap-wild-ideas")
        private const val ONE_PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
