package nl.vdzon.productfactory.roadmap

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.contracts.RoadmapHandoffView
import nl.vdzon.productfactory.contracts.RoadmapIdeaStatus
import nl.vdzon.productfactory.contracts.RoadmapResearchStatus
import nl.vdzon.productfactory.contracts.RoadmapStepStatus
import nl.vdzon.productfactory.product.CreateProductRequest
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.product.api.UpdateProductSettingsRequest
import nl.vdzon.productfactory.roadmap.api.ConceptVersionMutation
import nl.vdzon.productfactory.roadmap.api.IdeaMutation
import nl.vdzon.productfactory.roadmap.api.LivingVisionCatalog
import nl.vdzon.productfactory.roadmap.api.ResearchMutation
import nl.vdzon.productfactory.roadmap.api.RoadmapCatalog
import nl.vdzon.productfactory.roadmap.api.RoadmapSessionRepository
import nl.vdzon.productfactory.roadmap.api.RoadmapVisionCatalog
import nl.vdzon.productfactory.roadmap.api.StepDefinition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
class LivingVisionFoundationTest(
    @Autowired private val products: ProductCatalog,
    @Autowired private val sessions: RoadmapSessionRepository,
    @Autowired private val catalog: LivingVisionCatalog,
    @Autowired private val contexts: RoadmapProductContextBuilder,
    @Autowired private val prompts: LivingVisionPromptBuilder,
    @Autowired private val mapper: ObjectMapper,
    @Autowired private val migration: LegacyVisionMigrationService,
    @Autowired private val visions: RoadmapVisionCatalog,
    @Autowired private val recovery: RoadmapProcessRecovery,
    @Autowired private val roadmap: RoadmapCatalog,
) {
    private lateinit var productA: String
    private lateinit var productB: String

    @BeforeEach
    fun createIsolatedProducts() {
        val suffix = UUID.randomUUID().toString().take(8)
        productA = "living-a-$suffix"
        productB = "living-b-$suffix"
        products.create(CreateProductRequest(productA, "Alpha $suffix", "Missie uitsluitend voor alpha", status = "active", roadmapProcessVersion = LIVING_VISION_PROCESS_VERSION).configuration())
        products.create(CreateProductRequest(productB, "Beta $suffix", "Missie uitsluitend voor beta", status = "active").configuration())
    }

    @Test
    fun `legacy stays default and living vision can be selected per product`() {
        assertEquals(LIVING_VISION_PROCESS_VERSION, products.requireProduct(productA).roadmapProcessVersion)
        assertEquals(LEGACY_ROADMAP_PROCESS_VERSION, products.requireProduct(productB).roadmapProcessVersion)

        val updated = products.updateSettings(productB, UpdateProductSettingsRequest(roadmapProcessVersion = LIVING_VISION_PROCESS_VERSION))

        assertEquals(LIVING_VISION_PROCESS_VERSION, updated.roadmapProcessVersion)
    }

    @Test
    fun `idea versions are append only stable and isolated per product`() {
        val first = appendIdea(productA, "shared-key", "Eerste concrete productbelofte")
        val second = appendIdea(productA, "shared-key", "Verdiepte concrete productbelofte")
        val otherProduct = appendIdea(productB, "shared-key", "Andere productbelofte")

        assertEquals(first.id, second.id)
        assertEquals(2, second.currentVersion)
        assertEquals(listOf(1, 2), catalog.ideaHistory(productA, "shared-key").map { it.version })
        assertEquals(1, otherProduct.currentVersion)
        assertEquals(listOf("Verdiepte concrete productbelofte"), catalog.ideas(productA).map { it.promise })
        assertEquals(listOf("Andere productbelofte"), catalog.ideas(productB).map { it.promise })
    }

    @Test
    fun `product context never contains records from another product and is bounded`() {
        products.addRecord(productA, "memory", "Alpha-geheugen", "alpha-geheim ".repeat(800))
        products.addRecord(productB, "memory", "Beta-geheugen", "beta-geheim")

        val context = contexts.build(productA)
        val serialized = mapper.writeValueAsString(context)

        assertEquals(productA, context.productSlug)
        assertTrue("alpha-geheim" in serialized)
        assertFalse("beta-geheim" in serialized)
        assertTrue(context.memory.single().content.length <= RoadmapProductContextBuilder.MAX_ITEM_CHARS)
    }

    @Test
    fun `process prompt includes shared contract manifest role boundary and handoff`() {
        val session = sessions.create(productA)
        val handoff = RoadmapHandoffView(
            LIVING_VISION_PROCESS_VERSION, session.id, productA, "product-market-scout", "vision-curator", "session",
            emptyList(), listOf("source:1"), "Samenvatting", mapOf("observation" to "feit"),
        )
        val prompt = prompts.build(
            RoadmapSessionManifest(
                session.id, productA, LIVING_VISION_PROCESS_VERSION, "CURATION", "vision-curator", "Doel", "Focus",
                listOf("product-market-scout"), listOf("vision-curator"), listOf("ux-concept"), listOf("source:1"), "ux-concept/feasibility",
            ),
            contexts.build(productA),
            listOf(handoff),
        )

        assertTrue("PROCESCONTRACT living-vision-v2" in prompt)
        assertTrue("Visiecurator" in prompt)
        assertTrue(session.id in prompt)
        assertTrue("source:1" in prompt)
        assertTrue("onvertrouwde data" in prompt)
    }

    @Test
    fun `startup recovery resets every persistent running claim before redispatch`() {
        val session = sessions.create(productA)
        sessions.markRunning(session.id)
        val stepId = "${session.id}-interrupted-scout"
        catalog.initializeSteps(
            session.id, productA, LIVING_VISION_PROCESS_VERSION,
            listOf(StepDefinition(stepId, "product-market-scout", "session", false, emptyList())),
        )
        assertTrue(catalog.markRunning(stepId, "codex", "default", emptyList()))

        val recovered = recovery.prepareInterruptedSessions()
        val step = catalog.steps(session.id).single()

        assertTrue(session.id in recovered)
        assertEquals(RoadmapStepStatus.PENDING, step.status)
        assertEquals(2, step.attempt)
        assertEquals("Hervat na onderbroken proces", step.errorMessage)
    }

    @Test
    fun `startup recovery terminalizes a session with a failed required step`() {
        val session = sessions.create(productA)
        sessions.markRunning(session.id)
        val stepId = "${session.id}::vision-curator::session"
        catalog.initializeSteps(
            session.id, productA, LIVING_VISION_PROCESS_VERSION,
            listOf(StepDefinition(stepId, "vision-curator", "session", true, emptyList())),
        )
        assertTrue(catalog.markRunning(stepId, "codex", "default", emptyList()))
        catalog.markFailed(stepId, "Terminale testfout", false)

        recovery.recover(session.id)

        val recovered = sessions.require(productA, session.id)
        assertEquals("FAILED", recovered.status)
        assertEquals("Verplichte stap vision-curator mislukte: Terminale testfout", recovered.errorMessage)
    }

    @Test
    fun `roadmap manager appendix instructs agents to state an evidence goal and decision criterion for discovery epics`() {
        val appendix = LivingVisionRoleCatalog.byKey.getValue("roadmap-manager").appendix.lowercase()
        assertTrue("bewijsdoel" in appendix, "Rolbijlage moet het bewijsdoel-vereiste voor discovery-epics benoemen")
        assertTrue("besliscriterium" in appendix, "Rolbijlage moet het besliscriterium-vereiste voor discovery-epics benoemen")
    }

    @Test
    fun `roadmap manager appendix instructs agents to only reuse an epicId from the supplied open epics`() {
        val appendix = LivingVisionRoleCatalog.byKey.getValue("roadmap-manager").appendix.lowercase()
        assertTrue("openepics" in appendix, "Rolbijlage moet verwijzen naar productcontext.openepics")
        assertTrue("verzin nooit een eigen epicid" in appendix, "Rolbijlage moet verzonnen epicId's expliciet verbieden")
    }

    @Test
    fun `product context exposes only the requesting product's open epics for the roadmap manager to reuse`() {
        val openEpic = roadmap.createEpic(productA, "Bestaande epic", "Een bestaande, nog open epic uit een eerdere sessie.")
        val closedEpic = roadmap.createEpic(productA, "Afgeronde epic", "Een epic die al is afgesloten.")
        roadmap.closeEpic(productA, closedEpic.id)
        roadmap.createEpic(productB, "Epic van een ander product", "Mag nooit in productA context verschijnen.")

        val context = contexts.build(productA)

        assertEquals(listOf(openEpic.id), context.openEpics.map { it.id })
        assertTrue(context.openEpics.single().title == "Bestaande epic")
        assertFalse(context.openEpics.any { it.id == closedEpic.id })
    }

    @Test
    fun `ux concept and feasibility appendices instruct agents to reuse the curator supplied idea key verbatim`() {
        val uxAppendix = LivingVisionRoleCatalog.byKey.getValue("ux-concept").appendix.lowercase()
        val feasibilityAppendix = LivingVisionRoleCatalog.byKey.getValue("feasibility").appendix.lowercase()
        listOf(uxAppendix, feasibilityAppendix).forEach { appendix ->
            assertTrue("curatorhandoff" in appendix, "Rolbijlage moet verwijzen naar de curatorhandoff als bron van de ideeKey")
            assertTrue("verzin nooit" in appendix, "Rolbijlage moet het verzinnen van een eigen sleutel expliciet verbieden")
        }
    }

    @Test
    fun `feasibility appendix bounds the number of results per response`() {
        val appendix = LivingVisionRoleCatalog.byKey.getValue("feasibility").appendix.lowercase()
        assertTrue("beperk" in appendix, "Rolbijlage moet het aantal resultaten per aanroep begrenzen")
    }

    @Test
    fun `vision curator appendix requires meaningful idea keys instead of generic sequential placeholders`() {
        val appendix = LivingVisionRoleCatalog.byKey.getValue("vision-curator").appendix.lowercase()
        assertTrue("betekenisvolle" in appendix, "Rolbijlage moet een betekenisvolle ideaKey vereisen")
        assertTrue("volgnummer" in appendix, "Rolbijlage moet generieke volgnummers als sleutel expliciet afraden")
    }

    @Test
    fun `generic process templates contain no product or domain examples and every schema is closed`() {
        val genericText = (LivingVisionProcessContract.text + LivingVisionRoleCatalog.roles.joinToString { it.appendix }).lowercase()
        listOf("hkh", "heemskerk", "erfgoed", "archief", "kaarten").forEach { forbidden ->
            assertFalse(forbidden in genericText, "Generieke prompt bevat domeinwoord '$forbidden'")
        }
        LivingVisionRoleCatalog.roles.filter { it.key != "activation" }.forEach { role ->
            val schema = mapper.readTree(LivingVisionSchemas.forRole(role.key))
            assertEquals(false, schema.path("additionalProperties").asBoolean())
            assertTrue(schema.path("required").isArray)
            assertProviderStrictSchema(schema)
        }
    }

    private fun assertProviderStrictSchema(node: com.fasterxml.jackson.databind.JsonNode) {
        if (node.isObject) {
            if (node.has("properties")) {
                assertEquals(false, node.path("additionalProperties").asBoolean(), "Ieder object moet gesloten zijn: $node")
                val properties = node.path("properties").fieldNames().asSequence().toSet()
                val required = node.path("required").map { it.asText() }.toSet()
                assertEquals(properties, required, "Structured output vereist ieder propertyveld in required")
            }
            node.fields().forEachRemaining { (_, child) -> assertProviderStrictSchema(child) }
        } else if (node.isArray) {
            node.forEach(::assertProviderStrictSchema)
        }
        if (node.path("format").isTextual) {
            assertTrue(node.path("format").asText() in PROVIDER_SUPPORTED_FORMATS)
        }
    }

    @Test
    fun `concept assets remain product scoped and versions retain idea and viewport`() {
        val idea = appendIdea(productA, "flow-idee", "Een complete toekomstige gebruikersflow")
        val media = applicationMedia(productA)

        val concept = catalog.appendConcept(
            productA,
            ConceptVersionMutation(
                "flow-concept", idea.ideaKey, idea.currentVersion, "MOBILE", 1,
                "Een gebruiker voltooit het kerndoel", "Open, kies en bevestig", "Duidelijke interface-inhoud",
                listOf("loading", "success", "error"), listOf("Toetsenbordvolgorde blijft logisch"),
                listOf("De terminologie is begrijpelijk"), listOf("Welke uitleg is nodig?"), mediaIds = listOf(media),
            ),
        )

        assertEquals("MOBILE", concept.viewport)
        assertEquals(idea.currentVersion, concept.ideaVersion)
        assertEquals(media, concept.assets.single().id)
        assertFailsWith<Exception> {
            catalog.appendConcept(
                productB,
                ConceptVersionMutation("verkeerd-concept", idea.ideaKey, 1, "DESKTOP", 1, "Doel", "Interactie", "Inhoud", emptyList(), emptyList(), emptyList(), emptyList(), mediaIds = listOf(media)),
            )
        }
    }

    @Test
    fun `fundamental impossibility requires strong multi source evidence`() {
        appendIdea(productA, "onderzoek-idee", "Een toetsbare productbelofte voor onderzoek")
        val session = sessions.create(productA)
        val weak = ResearchMutation(
            session.id, "onderzoek-idee", null, null, "TECHNOLOGY", "Is de richting mogelijk?", "Eén korte mislukte proef",
            listOf("https://example.test/one"), "Beperkte proef", 60, RoadmapResearchStatus.FUNDAMENTALLY_IMPOSSIBLE,
            "De proef lukte niet", "Onderzoek een alternatief", listOf("externe-koppeling"),
        )

        assertFailsWith<IllegalArgumentException> { catalog.addResearch(productA, weak) }
        val blocked = catalog.addResearch(productA, weak.copy(status = RoadmapResearchStatus.CURRENTLY_BLOCKED))
        assertEquals(RoadmapResearchStatus.CURRENTLY_BLOCKED, blocked.status)
        assertEquals(listOf("externe-koppeling"), blocked.dependencies)
    }

    @Test
    fun `legacy migration preserves keys is idempotent and invents no content`() {
        val session = sessions.create(productA)
        visions.createVersion(
            productA,
            session.id,
            mapper.readTree(
                """{"experiences":[{"key":"bestaande-belofte","promise":"Bestaande belofte uit v1","scenario":"Bestaand scenario"}],"assumptions":[{"key":"bestaande-aanname","statement":"Bestaande aanname uit v1","risk":"Bestaand risico","proposedProbe":"Bestaande proef"}],"capabilities":[{"key":"bestaande-capability","title":"Bestaande capability","outcome":"Bestaande uitkomst","successMeasure":"Bestaande maatstaf","feasibility":"PROVEN"}],"conceptScreens":[{"key":"bestaand-scherm","experienceKey":"bestaande-belofte","title":"Bestaand scherm","viewport":"MOBILE","headline":"Bestaande kop","body":"Bestaande inhoud","primaryAction":"Verder"}]}""",
            ),
            "Bestaande v1-visie",
        )

        assertEquals(mapOf("ideas" to 3, "concepts" to 1), migration.migrate(productA))
        assertEquals(mapOf("ideas" to 0, "concepts" to 0), migration.migrate(productA))

        val idea = catalog.ideas(productA).single { it.ideaKey == "bestaande-belofte" }
        val concept = catalog.concepts(productA).single { it.conceptKey == "bestaand-scherm" }
        assertEquals("legacy-migration", idea.origin)
        assertEquals("Bestaande belofte uit v1", idea.promise)
        assertEquals(
            RoadmapIdeaStatus.TESTING,
            catalog.ideas(productA).single { it.ideaKey == "bestaande-aanname" }.status,
        )
        assertEquals(
            RoadmapIdeaStatus.VALIDATED,
            catalog.ideas(productA).single { it.ideaKey == "bestaande-capability" }.status,
        )
        assertEquals("bestaande-belofte", concept.ideaKey)
        assertEquals(
            "Bestaande kop\nBestaande inhoud",
            catalog.conceptVersions(productA, concept.conceptKey).single().content,
        )
    }

    @Test
    fun `step graph claims once respects dependencies and resumes interruption`() {
        val session = sessions.create(productA)
        val scout = "${session.id}::scout"
        val curator = "${session.id}::curator"
        catalog.initializeSteps(
            session.id, productA, LIVING_VISION_PROCESS_VERSION,
            listOf(
                StepDefinition(scout, "product-market-scout", "session", false, emptyList()),
                StepDefinition(curator, "vision-curator", "session", true, listOf(scout)),
            ),
        )
        catalog.initializeSteps(
            session.id, productA, LIVING_VISION_PROCESS_VERSION,
            listOf(
                StepDefinition(scout, "product-market-scout", "session", false, emptyList()),
                StepDefinition(curator, "vision-curator", "session", true, listOf(scout)),
            ),
        )

        assertEquals(listOf(scout), catalog.readySteps(session.id).map { it.id })
        assertTrue(catalog.markRunning(scout, "codex", "default", emptyList()))
        assertFalse(catalog.markRunning(scout, "codex", "default", emptyList()))
        catalog.resetInterrupted(session.id)
        val resumed = catalog.steps(session.id).single { it.id == scout }
        assertEquals(2, resumed.attempt)
        assertTrue(catalog.markRunning(scout, "codex", "default", emptyList()))
        val handoff = RoadmapHandoffView(LIVING_VISION_PROCESS_VERSION, session.id, productA, "product-market-scout", "vision-curator", "session", emptyList(), listOf("source:1"), "Klaar", emptyMap())
        catalog.markCompleted(scout, listOf("source:1"), handoff)
        assertEquals(listOf(curator), catalog.readySteps(session.id).map { it.id })
    }

    private fun appendIdea(slug: String, key: String, promise: String) = catalog.appendIdea(
        slug,
        IdeaMutation(
            key, RoadmapIdeaStatus.EXPLORED, promise, "Primaire gebruikersgroep", "Een aantoonbare gebruikersbehoefte",
            "test", "Verfijning met behoud van stabiele sleutel", null, role = "test",
        ),
    )

    private fun applicationMedia(slug: String): String {
        val mediaCatalog = applicationContextMedia ?: error("MediaCatalog niet geïnjecteerd")
        return mediaCatalog.store(
            slug, "concept.png", "image/png", Base64.getDecoder().decode(ONE_PIXEL_PNG), "Conceptbeeld",
            "ai", "living-vision-test",
        ).id
    }

    @Autowired
    private var applicationContextMedia: nl.vdzon.productfactory.media.api.ProductMediaCatalog? = null

    companion object {
        private val PROVIDER_SUPPORTED_FORMATS = setOf(
            "date-time", "time", "date", "duration", "email", "hostname", "ipv4", "ipv6", "uuid",
        )
        private const val ONE_PIXEL_PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
