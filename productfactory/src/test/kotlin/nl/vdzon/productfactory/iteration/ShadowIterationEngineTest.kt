package nl.vdzon.productfactory.iteration

import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.WorkspacePublicationView
import nl.vdzon.productfactory.roadmap.api.RoadmapCatalog
import nl.vdzon.productfactory.workspace.api.WorkspaceArtifact
import nl.vdzon.productfactory.workspace.api.WorkspacePublicationPort
import nl.vdzon.productfactory.workspace.api.WorkspaceVisionPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@Import(ShadowIterationEngineTest.Fakes::class)
class ShadowIterationEngineTest(
    @Autowired private val repository: ShadowIterationRepository,
    @Autowired private val engine: ShadowIterationEngine,
    @Autowired private val bridge: FakeShadowAgentBridge,
    @Autowired private val workspace: FakeWorkspacePublicationPort,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val roadmap: RoadmapCatalog,
) {
    // De fakes zijn Spring-singletons die alle testmethoden in deze klasse delen; zonder reset
    // lekt de workspace-artefactenlijst (en het scenario) van de ene test naar de volgende.
    @BeforeEach
    fun resetFakes() {
        workspace.artifacts.clear()
        bridge.scenario = Scenario.ACCEPT
        bridge.themeIdToEmit = null
    }

    @Test
    fun `a candidate is linked to the roadmap theme the story writer chose`() {
        bridge.scenario = Scenario.THEME_LINKED
        val theme = roadmap.createTheme("hkh-autopilot", "Brontransparantie", "Toon rechten- en bronvermelding overal.", "HIGH")
        bridge.themeIdToEmit = theme.id
        val iteration = repository.create("hkh-autopilot", "Koppel de kandidaat aan een open roadmapthema")
        engine.run(iteration.id)

        assertEquals("ACCEPTED", repository.require("hkh-autopilot", iteration.id).status)
        assertEquals(
            theme.id,
            jdbc.queryForObject("select theme_id from story_candidate where iteration_id = ?", String::class.java, iteration.id),
        )
    }

    @Test
    fun `an unknown themeId from the story writer is ignored instead of persisted`() {
        bridge.scenario = Scenario.THEME_LINKED
        bridge.themeIdToEmit = "theme-die-niet-bestaat"
        val iteration = repository.create("hkh-autopilot", "Negeer een verzonnen themaId")
        engine.run(iteration.id)

        assertEquals("ACCEPTED", repository.require("hkh-autopilot", iteration.id).status)
        assertNull(
            jdbc.queryForObject("select theme_id from story_candidate where iteration_id = ?", String::class.java, iteration.id),
        )
    }

    @Test
    fun `candidate from a failed workspace publication does not block a retry`() {
        val iteration = repository.create("hkh-autopilot", "Simuleer een mislukte workspace-publicatie")
        repository.saveCandidate(
            iteration.id,
            "hkh-autopilot",
            "Herstelbare kandidaat",
            "Een kandidaat waarvan de workspace-publicatie is mislukt.",
            "- Publicatie kan opnieuw worden geprobeerd",
            "failed-publication-fingerprint",
            "ACCEPT",
            "Inhoudelijk geaccepteerd",
            null,
        )
        repository.markFailed(iteration.id, "Workspace tijdelijk niet beschikbaar")

        assertNull(repository.findDuplicate("hkh-autopilot", "failed-publication-fingerprint"))
    }

    @Test
    fun `critic accepts rejects and returns complete isolated shadow iterations`() {
        bridge.scenario = Scenario.ACCEPT
        val accepted = repository.create("hkh-autopilot", "Vind de beste eerste bronervaring")
        engine.run(accepted.id)
        assertEquals("ACCEPTED", repository.require("hkh-autopilot", accepted.id).status)
        assertEquals("Dit is een korte, voor-dummies samenvatting van de testcyclus.", repository.require("hkh-autopilot", accepted.id).summary)
        assertEquals(6, repository.steps("hkh-autopilot", accepted.id).size)
        // 6 rolartefacten (research/product_owner/ux_designer/story_writer/critic/summary) plus de
        // dependson_resolution-mapping die persistValidatedResults altijd na afloop van de batch vastlegt.
        assertEquals(7, repository.artifacts("hkh-autopilot", accepted.id).size)
        assertTrue(repository.artifacts("hkh-autopilot", accepted.id).any { it.artifactType == "product_owner" })
        assertTrue(repository.artifacts("hkh-autopilot", accepted.id).any { it.artifactType == "dependson_resolution" })
        assertEquals(1, workspace.artifacts.size)
        assertTrue(workspace.artifacts.single().content.contains("Rechtenindicatie"))
        assertTrue(workspace.artifacts.single().content.contains("run_id: ${accepted.id}"))

        bridge.scenario = Scenario.DUPLICATE
        val duplicate = repository.create("hkh-autopilot", "Controleer een mogelijk dubbel voorstel")
        engine.run(duplicate.id)
        assertEquals("NO_CHANGE", repository.require("hkh-autopilot", duplicate.id).status)
        assertEquals("ACCEPT", repository.require("hkh-autopilot", duplicate.id).criticVerdict)
        assertEquals(1, workspace.artifacts.size)
        assertEquals(
            1,
            jdbc.queryForObject(
                "select count(*) from story_candidate where iteration_id = ? and status = 'DUPLICATE' and duplicate_of_id is not null",
                Int::class.java,
                duplicate.id,
            ),
        )

        bridge.scenario = Scenario.REVISE
        val revision = repository.create("hkh-autopilot", "Laat de criticus een te breed voorstel terugsturen")
        engine.run(revision.id)
        assertEquals("NEEDS_REVISION", repository.require("hkh-autopilot", revision.id).status)
        assertEquals("REVISE", repository.require("hkh-autopilot", revision.id).criticVerdict)
        assertEquals("Dit is een korte, voor-dummies samenvatting van de testcyclus.", repository.require("hkh-autopilot", revision.id).summary)
        assertEquals(12, repository.steps("hkh-autopilot", revision.id).size)
        assertEquals(1, workspace.artifacts.size)

        bridge.scenario = Scenario.REVISE_THEN_ACCEPT
        val selfCorrected = repository.create("hkh-autopilot", "Verwerk criticusfeedback autonoom")
        engine.run(selfCorrected.id)
        assertEquals("ACCEPTED", repository.require("hkh-autopilot", selfCorrected.id).status)
        assertEquals(8, repository.steps("hkh-autopilot", selfCorrected.id).size)
        assertEquals(2, workspace.artifacts.size)

        bridge.scenario = Scenario.AUTONOMY_REVISE_THEN_ACCEPT
        val autonomousCorrection = repository.create("hkh-autopilot", "Verwijder handmatige afhankelijkheden", "autonomous")
        engine.run(autonomousCorrection.id)
        assertEquals("ACCEPTED", repository.require("hkh-autopilot", autonomousCorrection.id).status)
        assertEquals(8, repository.steps("hkh-autopilot", autonomousCorrection.id).size)
        assertEquals(3, workspace.artifacts.size)

        bridge.scenario = Scenario.WARNING_ONLY_REVISE
        val warningOnly = repository.create("hkh-autopilot", "Laat waarschuwingen de levering niet blokkeren")
        engine.run(warningOnly.id)
        assertEquals("ACCEPTED", repository.require("hkh-autopilot", warningOnly.id).status)
        assertEquals("ACCEPT", repository.require("hkh-autopilot", warningOnly.id).criticVerdict)
        assertEquals(6, repository.steps("hkh-autopilot", warningOnly.id).size)
        assertEquals(4, workspace.artifacts.size)

        assertEquals(
            12,
            jdbc.queryForObject(
                "select count(*) from research_source where iteration_id in (?, ?, ?, ?, ?, ?)",
                Int::class.java,
                accepted.id, duplicate.id, revision.id, selfCorrected.id, autonomousCorrection.id, warningOnly.id,
            ),
        )
        assertEquals(
            0,
            jdbc.queryForObject(
                "select count(*) from story_candidate where iteration_id in (?, ?, ?) and status = 'PUBLISHED'",
                Int::class.java,
                accepted.id,
                duplicate.id,
                revision.id,
            ),
        )
    }

    @Test
    fun `a researcher validation failure is retried with the rejection reason instead of failing the cycle`() {
        bridge.scenario = Scenario.RESEARCH_RETRY_THEN_ACCEPT
        val iteration = repository.create("hkh-autopilot", "Herstel een onvolledige eerste onderzoekspoging")
        engine.run(iteration.id)

        assertEquals("ACCEPTED", repository.require("hkh-autopilot", iteration.id).status)
        val researcherSteps = repository.steps("hkh-autopilot", iteration.id).filter { it.role == "RESEARCHER" }
        assertEquals(2, researcherSteps.size)
        assertEquals("FAILED", researcherSteps[0].status)
        assertTrue(researcherSteps[0].errorMessage!!.contains("twee bronnen"))
        assertEquals("COMPLETED", researcherSteps[1].status)
    }

    @Test
    fun `story output repair does not consume a critic revision round`() {
        bridge.scenario = Scenario.STORY_OUTPUT_REPAIR_THEN_ACCEPT
        val iteration = repository.create("hkh-autopilot", "Herstel redactionele modeltekst")
        engine.run(iteration.id)

        val stored = repository.require("hkh-autopilot", iteration.id)
        assertEquals("ACCEPTED", stored.status)
        assertEquals(0, stored.revisionRounds)
        val storySteps = repository.steps("hkh-autopilot", iteration.id).filter { it.role == "STORY_WRITER" }
        assertEquals(listOf("FAILED", "COMPLETED"), storySteps.map { it.status })
        assertTrue(storySteps.first().errorMessage!!.contains("modeltekst"))
    }

    @Test
    fun `negated human control does not trigger the autonomy gate`() {
        assertTrue(!engine.requiresOwnerAction("De widgettest bewijst dit zonder browser of menselijke controle."))
        assertTrue(engine.requiresOwnerAction("Een menselijke controle wordt door de eigenaar uitgevoerd."))
    }

    @Test
    fun `an accepted candidate is published while an independent batch peer still needs revision`() {
        bridge.scenario = Scenario.PARTIAL_ACCEPT
        val iteration = repository.create("hkh-autopilot", "Lever het veilige deel van een gemengde batch")
        engine.run(iteration.id)

        val stored = repository.require("hkh-autopilot", iteration.id)
        assertEquals("ACCEPTED", stored.status)
        assertEquals(1, stored.acceptedCandidateCount)
        assertEquals("PARTIAL_ACCEPT", stored.outcomeReason)
        assertTrue(workspace.artifacts.single().content.contains("Direct leverbare bronnenkaart"))
        assertTrue(!workspace.artifacts.single().content.contains("Nog te brede import"))
    }

    @Test
    fun `a needs revision cycle can reuse context and resume at the story writer`() {
        bridge.scenario = Scenario.REVISE
        val source = repository.create("hkh-autopilot", "Bewaar een herstelbaar concept")
        engine.run(source.id)
        assertEquals("NEEDS_REVISION", repository.require("hkh-autopilot", source.id).status)

        bridge.scenario = Scenario.RESUME_THEN_ACCEPT
        val resumed = repository.create(
            "hkh-autopilot", "Hervat het concept", resumeFromIterationId = source.id,
        )
        engine.run(resumed.id)

        val stored = repository.require("hkh-autopilot", resumed.id)
        assertEquals("ACCEPTED", stored.status)
        assertEquals(source.id, stored.resumedFromIterationId)
        assertTrue(repository.steps("hkh-autopilot", resumed.id).none { it.role in setOf("RESEARCHER", "PRODUCT_OWNER", "UX_DESIGNER") })
    }

    @Test
    fun `two candidates referencing each other by candidateKey resolve correctly regardless of batch order`() {
        bridge.scenario = Scenario.CROSS_KEY_DEPENDENCY
        val iteration = repository.create("hkh-autopilot", "Koppel twee onderling afhankelijke kandidaten via hun candidateKey")
        engine.run(iteration.id)

        assertEquals("ACCEPTED", repository.require("hkh-autopilot", iteration.id).status)
        val dossier = workspace.artifacts.single().content
        // De eerste kandidaat in de batch (index 0, "locatie-broncontrole") hangt af van de kandidaat die
        // ná hem in de array staat (index 1, "locatie-verhaal-basis"): dit kan alleen correct oplossen via
        // een candidateKey-lookup, niet via een op arrayvolgorde/positie gebaseerde koppeling.
        assertTrue(dossier.contains("Sleutel: `locatie-broncontrole`"))
        assertTrue(dossier.contains("Sleutel: `locatie-verhaal-basis`"))
        assertTrue(dossier.contains("Afhankelijkheden (candidateKey): locatie-verhaal-basis (binnen deze batch herkend als: locatie-verhaal-basis)"))
        assertTrue(dossier.contains("Afhankelijkheden (candidateKey): locatie-broncontrole (binnen deze batch herkend als: locatie-broncontrole)"))
    }

    @Test
    fun `resolveDependencyReferences looks up by candidateKey regardless of map insertion order`() {
        val a = ReviewedCandidate(
            0, "kandidaat-a", "Kandidaat A", "Omschrijving A", listOf("criterium"), listOf("https://bron.example/"),
            listOf("kandidaat-b"), listOf(), "ACCEPT", "ok", "fingerprint-a", null,
        )
        val b = ReviewedCandidate(
            1, "kandidaat-b", "Kandidaat B", "Omschrijving B", listOf("criterium"), listOf("https://bron.example/"),
            listOf("kandidaat-a"), listOf(), "ACCEPT", "ok", "fingerprint-b", null,
        )
        val byPosition = listOf(a, b)

        val forwardOrder = linkedMapOf(a.candidateKey to a, b.candidateKey to b)
        val reverseOrder = linkedMapOf(b.candidateKey to b, a.candidateKey to a)

        assertEquals(listOf(DependencyResolution("kandidaat-b", "kandidaat-b", false)), resolveDependencyReferences(forwardOrder, byPosition, a.dependsOn))
        assertEquals(listOf(DependencyResolution("kandidaat-b", "kandidaat-b", false)), resolveDependencyReferences(reverseOrder, byPosition, a.dependsOn))
        assertEquals(listOf(DependencyResolution("kandidaat-a", "kandidaat-a", false)), resolveDependencyReferences(forwardOrder, byPosition, b.dependsOn))
        assertEquals(listOf(DependencyResolution("kandidaat-a", "kandidaat-a", false)), resolveDependencyReferences(reverseOrder, byPosition, b.dependsOn))
    }

    @Test
    fun `resolveDependencyReferences falls back to the batch position for the legacy Kandidaat N format`() {
        val a = ReviewedCandidate(
            0, "locatie-basisverhaal", "Locatie basisverhaal", "Omschrijving A", listOf("criterium"), listOf("https://bron.example/"),
            listOf(), listOf(), "ACCEPT", "ok", "fingerprint-a", null,
        )
        val b = ReviewedCandidate(
            1, "locatie-detailverhaal", "Locatie detailverhaal", "Omschrijving B", listOf("criterium"), listOf("https://bron.example/"),
            listOf("Kandidaat 0"), listOf(), "ACCEPT", "ok", "fingerprint-b", null,
        )
        val byPosition = listOf(a, b)
        val byKey = byPosition.associateBy(ReviewedCandidate::candidateKey)

        val resolutions = resolveDependencyReferences(byKey, byPosition, b.dependsOn)

        assertEquals(listOf(DependencyResolution("Kandidaat 0", "locatie-basisverhaal", viaLegacyFallback = true)), resolutions)
    }

    @Test
    fun `resolveDependencyReferences leaves an unknown key and an out-of-range legacy position unresolved`() {
        val a = ReviewedCandidate(
            0, "locatie-basisverhaal", "Locatie basisverhaal", "Omschrijving A", listOf("criterium"), listOf("https://bron.example/"),
            listOf("niet-bestaande-sleutel"), listOf(), "ACCEPT", "ok", "fingerprint-a", null,
        )
        val byPosition = listOf(a)
        val byKey = byPosition.associateBy(ReviewedCandidate::candidateKey)

        val unknownKey = resolveDependencyReferences(byKey, byPosition, a.dependsOn)
        val outOfRangeLegacy = resolveDependencyReferences(byKey, byPosition, listOf("Kandidaat 5"))

        assertTrue(unknownKey.single().let { !it.resolved && !it.viaLegacyFallback })
        assertTrue(outOfRangeLegacy.single().let { !it.resolved })
    }

    @Test
    fun `a dependsOn value that cannot be resolved to a backlog-ID blocks only that candidate`() {
        bridge.scenario = Scenario.UNKNOWN_DEPENDSON_KEY
        val iteration = repository.create("hkh-autopilot", "Blokkeer alleen de kandidaat met een onvertaalbare dependsOn-sleutel")
        engine.run(iteration.id)

        assertEquals("ACCEPTED", repository.require("hkh-autopilot", iteration.id).status)
        val persisted = jdbc.query(
            "select title, status from story_candidate where iteration_id = ?",
            { row, _ -> row.getString("title") to row.getString("status") },
            iteration.id,
        )
        assertEquals(listOf("Locatie basisverhaal" to "INTERNAL"), persisted)

        val dossier = workspace.artifacts.single().content
        assertTrue(dossier.contains("Locatie basisverhaal"))
        assertTrue(!dossier.contains("Locatie detailverhaal"))

        val mappingLog = repository.artifact(iteration.id, "dependson_resolution")!!
        assertTrue(mappingLog.contains("\"blocked\":true"))
        assertTrue(mappingLog.contains("niet-bestaande-sleutel"))
    }

    @Test
    fun `a dependsOn value in the legacy Kandidaat N format resolves via the positional fallback and is marked as such`() {
        bridge.scenario = Scenario.LEGACY_POSITIONAL_DEPENDSON
        val iteration = repository.create("hkh-autopilot", "Vertaal een legacy positionele dependsOn-verwijzing automatisch")
        engine.run(iteration.id)

        assertEquals("ACCEPTED", repository.require("hkh-autopilot", iteration.id).status)
        assertEquals(
            2,
            jdbc.queryForObject("select count(*) from story_candidate where iteration_id = ?", Int::class.java, iteration.id),
        )

        val mappingLog = repository.artifact(iteration.id, "dependson_resolution")!!
        assertTrue(mappingLog.contains("\"viaLegacyFallback\":true"))
        assertTrue(mappingLog.contains("\"resolvedCandidateKey\":\"locatie-basisverhaal\""))
        assertTrue(mappingLog.contains("\"blocked\":false"))
    }

    enum class Scenario {
        ACCEPT, DUPLICATE, REVISE, REVISE_THEN_ACCEPT, AUTONOMY_REVISE_THEN_ACCEPT, WARNING_ONLY_REVISE,
        RESEARCH_RETRY_THEN_ACCEPT, CROSS_KEY_DEPENDENCY, LEGACY_POSITIONAL_DEPENDSON, UNKNOWN_DEPENDSON_KEY,
        THEME_LINKED, STORY_OUTPUT_REPAIR_THEN_ACCEPT, PARTIAL_ACCEPT, RESUME_THEN_ACCEPT,
    }

    class FakeShadowAgentBridge : AgentDispatchPort {
        var scenario = Scenario.ACCEPT
        var themeIdToEmit: String? = null
        override fun execute(task: AgentTask): AgentResult {
            val today = LocalDate.now(ZoneId.of("Europe/Amsterdam"))
            val firstAttempt = task.runId.endsWith("-1")
            val different = scenario == Scenario.REVISE ||
                (scenario in setOf(Scenario.REVISE_THEN_ACCEPT, Scenario.RESUME_THEN_ACCEPT) && firstAttempt)
            val json = when (task.taskType.removePrefix("shadow-")) {
                "researcher" -> if (scenario == Scenario.RESEARCH_RETRY_THEN_ACCEPT && task.runId.endsWith("-researcher-1")) """{
                    "summary":"Open erfgoedbronnen kunnen een controleerbare eerste zoekervaring ondersteunen.",
                    "findings":[{"title":"Open collecties","finding":"Noord-Hollands Archief biedt een publiek beschreven collectie met herleidbare objectpagina's.","sourceUrls":["https://noord-hollandsarchief.nl/"]}],
                    "sources":[
                      {"url":"https://noord-hollandsarchief.nl/","consultedOn":"$today","rightsIndication":"Rechten verschillen per object en moeten op de objectpagina worden gecontroleerd.","rationale":"Regionale bron voor Noord-Hollandse archiefcollecties."}
                    ],
                    "currentState":{"purpose":"De applicatie helpt bewoners lokale geschiedenis herleidbaar te ontdekken.","gaps":["Geen brontransparantie bij zoekresultaten"]},
                    "improvementOpportunities":["Toon rechten- en broninformatie direct bij ieder resultaat"],
                    "inspiration":[]
                }""" else """{
                    "summary":"Open erfgoedbronnen kunnen een controleerbare eerste zoekervaring ondersteunen.",
                    "findings":[{"title":"Open collecties","finding":"Noord-Hollands Archief en Rijksmuseum bieden publiek beschreven collecties met herleidbare objectpagina's.","sourceUrls":["https://noord-hollandsarchief.nl/","https://www.rijksmuseum.nl/nl/rijksstudio"]}],
                    "sources":[
                      {"url":"https://noord-hollandsarchief.nl/","consultedOn":"$today","rightsIndication":"Rechten verschillen per object en moeten op de objectpagina worden gecontroleerd.","rationale":"Regionale bron voor Noord-Hollandse archiefcollecties."},
                      {"url":"https://www.rijksmuseum.nl/nl/rijksstudio","consultedOn":"$today","rightsIndication":"Beschikbaarheid en rechten staan per object vermeld.","rationale":"Voorbeeld van een doorzoekbare Nederlandse erfgoedcollectie."}
                    ],
                    "currentState":{"purpose":"De applicatie helpt bewoners lokale geschiedenis herleidbaar te ontdekken.","gaps":["Geen brontransparantie bij zoekresultaten"]},
                    "improvementOpportunities":["Toon rechten- en broninformatie direct bij ieder resultaat"],
                    "inspiration":[{"name":"Rijksstudio","url":"https://www.rijksmuseum.nl/nl/rijksstudio","relevance":"Toont hoe broninformatie naast beeldmateriaal gepresenteerd kan worden."}]
                }"""
                "product_owner" -> """{
                    "productDirection":"Begin met een brontransparante verkenning van historische locaties.",
                    "rationale":"Een kleine bronervaring toetst vertrouwen voordat beeldherkenning of reconstructie wordt gebouwd.",
                    "priorities":["Herleidbare bron", "Eenvoudige locatieflow"],
                    "decisions":[{"decision":"Toon broncontext bij ieder resultaat","rationale":"Dit ondersteunt betrouwbaarheid en vervolgonderzoek.","sourceUrls":["https://noord-hollandsarchief.nl/"]}],
                    "rejectedOptions":["Direct een volledige 3D-reconstructie bouwen"]
                }"""
                "ux_designer" -> """{
                    "flowName":"Bronnenkaart","userGoal":"Een bewoner ontdekt vanuit een locatie een controleerbaar historisch verhaal.",
                    "steps":["Kies een locatie", "Bekijk een korte samenvatting", "Open bron en rechteninformatie"],
                    "wireframe":"[Locatie]\n  [Verhaal]\n  [Bronnen en rechten]",
                    "hypotheses":["Bronvermelding naast het verhaal vergroot vertrouwen."],
                    "accessibility":["Alle onderdelen zijn met toetsenbord bereikbaar."],
                    "privacyConsiderations":["De flow vereist geen locatiehistorie of gebruikersprofiel."]
                }"""
                "story_writer" -> if (scenario == Scenario.PARTIAL_ACCEPT) """{
                    "candidates":[
                      {"candidateKey":"direct-leverbare-bronnenkaart","title":"Direct leverbare bronnenkaart","description":"Toon één bron met rechtenmetadata zonder gegevens te kopiëren.","acceptanceCriteria":["De gebruiker ziet de bron-URL"],"sourceUrls":["https://noord-hollandsarchief.nl/"],"dependsOn":[],"risks":[]},
                      {"candidateKey":"nog-te-brede-import","title":"Nog te brede import","description":"Importeer ineens alle persoonsgegevens uit iedere externe collectie.","acceptanceCriteria":["Alle externe persoonsgegevens worden opgeslagen"],"sourceUrls":["https://noord-hollandsarchief.nl/"],"dependsOn":[],"risks":["Privacygrondslag ontbreekt"]}
                    ]
                }""" else if (scenario == Scenario.CROSS_KEY_DEPENDENCY) """{
                    "candidates":[
                      {
                        "candidateKey":"locatie-broncontrole",
                        "title":"Locatie broncontrole",
                        "description":"Controleer per historische locatie of de bron- en rechteninformatie klopt voordat die getoond wordt.",
                        "acceptanceCriteria":["De rechtenindicatie staat naast de bron"],
                        "sourceUrls":["https://noord-hollandsarchief.nl/"],
                        "dependsOn":["locatie-verhaal-basis"],
                        "risks":["Bronrechten kunnen per object verschillen"]
                      },
                      {
                        "candidateKey":"locatie-verhaal-basis",
                        "title":"Locatie verhaal basis",
                        "description":"Toon voor één historische locatie een kort verhaal met herleidbare bron- en rechteninformatie.",
                        "acceptanceCriteria":["De gebruiker ziet de bron-URL"],
                        "sourceUrls":["https://noord-hollandsarchief.nl/"],
                        "dependsOn":["locatie-broncontrole"],
                        "risks":["Bronrechten kunnen per object verschillen"]
                      }
                    ]
                }""" else if (scenario == Scenario.LEGACY_POSITIONAL_DEPENDSON) """{
                    "candidates":[
                      {
                        "candidateKey":"locatie-basisverhaal",
                        "title":"Locatie basisverhaal",
                        "description":"Toon voor één historische locatie een kort verhaal met herleidbare bron- en rechteninformatie.",
                        "acceptanceCriteria":["De gebruiker ziet de bron-URL"],
                        "sourceUrls":["https://noord-hollandsarchief.nl/"],
                        "dependsOn":[],
                        "risks":["Bronrechten kunnen per object verschillen"]
                      },
                      {
                        "candidateKey":"locatie-detailverhaal",
                        "title":"Locatie detailverhaal",
                        "description":"Toon aanvullende details bij het basisverhaal van diezelfde historische locatie.",
                        "acceptanceCriteria":["De gebruiker ziet de bron-URL", "De rechtenindicatie staat naast de bron"],
                        "sourceUrls":["https://noord-hollandsarchief.nl/"],
                        "dependsOn":["Kandidaat 0"],
                        "risks":["Bronrechten kunnen per object verschillen"]
                      }
                    ]
                }""" else if (scenario == Scenario.UNKNOWN_DEPENDSON_KEY) """{
                    "candidates":[
                      {
                        "candidateKey":"locatie-basisverhaal",
                        "title":"Locatie basisverhaal",
                        "description":"Toon voor één historische locatie een kort verhaal met herleidbare bron- en rechteninformatie.",
                        "acceptanceCriteria":["De gebruiker ziet de bron-URL"],
                        "sourceUrls":["https://noord-hollandsarchief.nl/"],
                        "dependsOn":[],
                        "risks":["Bronrechten kunnen per object verschillen"]
                      },
                      {
                        "candidateKey":"locatie-detailverhaal",
                        "title":"Locatie detailverhaal",
                        "description":"Toon aanvullende details bij het basisverhaal van diezelfde historische locatie.",
                        "acceptanceCriteria":["De gebruiker ziet de bron-URL", "De rechtenindicatie staat naast de bron"],
                        "sourceUrls":["https://noord-hollandsarchief.nl/"],
                        "dependsOn":["niet-bestaande-sleutel"],
                        "risks":["Bronrechten kunnen per object verschillen"]
                      }
                    ]
                }""" else if (scenario == Scenario.THEME_LINKED) """{
                    "candidates":[{
                      "candidateKey":"themagekoppelde-bronnenkaart",
                      "title":"Themagekoppelde bronnenkaart (${themeIdToEmit ?: "geen-thema"})",
                      "description":"Toon voor één historische locatie een verhaal dat expliciet aan roadmapthema ${themeIdToEmit ?: "geen-thema"} is gekoppeld.",
                      "acceptanceCriteria":["De gebruiker ziet de bron-URL"],
                      "sourceUrls":["https://noord-hollandsarchief.nl/"],"dependsOn":[],"risks":["Bronrechten kunnen per object verschillen"],
                      "themeId":${themeIdToEmit?.let { "\"$it\"" } ?: "null"}
                    }]
                }""" else """{
                    "candidates":[{
                      "candidateKey":"bronnenkaart-voor-locatie",
                      "title":"${when {
                          different -> "Brede erfgoedportal"
                          scenario == Scenario.REVISE_THEN_ACCEPT -> "Herziene bronnenkaart voor één locatie"
                          scenario == Scenario.RESUME_THEN_ACCEPT -> "Unieke hervatte bronnenkaart"
                          scenario == Scenario.AUTONOMY_REVISE_THEN_ACCEPT && firstAttempt -> "Handmatig geteste bronnenkaart"
                          scenario == Scenario.AUTONOMY_REVISE_THEN_ACCEPT -> "Automatisch geteste bronnenkaart"
                          scenario == Scenario.WARNING_ONLY_REVISE -> "Toegankelijke bronnenkaart met waarschuwing"
                          scenario == Scenario.RESEARCH_RETRY_THEN_ACCEPT -> "Bronnenkaart na onderzoekscorrectie"
                          scenario == Scenario.STORY_OUTPUT_REPAIR_THEN_ACCEPT && firstAttempt -> "TODO for model"
                          scenario == Scenario.STORY_OUTPUT_REPAIR_THEN_ACCEPT -> "Bronnenkaart na outputherstel"
                          else -> "Bronnenkaart voor één locatie"
                      }}",
                      "description":"${when {
                          different -> "Bouw in één keer zoeken, kaarten, tijdlijnen, beeldherkenning en reconstructies voor alle bronnen."
                          scenario == Scenario.REVISE_THEN_ACCEPT -> "Toon voor één historische locatie een herzien verhaal met herleidbare bron- en rechteninformatie."
                          scenario == Scenario.WARNING_ONLY_REVISE -> "Toon één historische locatie en bewaar een niet-blokkerende toegankelijkheidswaarschuwing."
                          scenario == Scenario.RESEARCH_RETRY_THEN_ACCEPT -> "Toon voor één historische locatie een verhaal na een herstelde onderzoekspoging."
                          else -> "Toon voor één historische locatie een verhaal met herleidbare bron- en rechteninformatie."
                      }}",
                      "acceptanceCriteria":[${if (scenario == Scenario.AUTONOMY_REVISE_THEN_ACCEPT && firstAttempt) {
                          "\"Een handmatige toets met VoiceOver wordt door de eigenaar uitgevoerd\", \"De rechtenindicatie staat naast de bron\""
                      } else {
                          "\"De gebruiker ziet de bron-URL\", \"De rechtenindicatie staat naast de bron\""
                      }}],
                      "sourceUrls":["https://noord-hollandsarchief.nl/"],"dependsOn":[],"risks":["Bronrechten kunnen per object verschillen"]
                    }]
                }"""
                "critic" -> if (scenario == Scenario.PARTIAL_ACCEPT) """{
                    "overallVerdict":"REVISE","summary":"De eerste kandidaat is veilig; de tweede mist een privacygrondslag.",
                    "issues":[{"severity":"BLOCKING","category":"PRIVACY","description":"Opslag van alle persoonsgegevens mist een actieve grondslag; beperk de story tot bronmetadata.","candidateIndex":1}],
                    "candidateReviews":[
                      {"candidateIndex":0,"verdict":"ACCEPT","reason":"Kleine veilige scope zonder gegevenskopie."},
                      {"candidateIndex":1,"verdict":"REVISE","reason":"Privacygrondslag ontbreekt."}
                    ],
                    "requiredChanges":["Beperk kandidaat 1 tot bronmetadata zonder persoonsgegevens te kopiëren."]
                }""" else if (scenario == Scenario.CROSS_KEY_DEPENDENCY || scenario == Scenario.LEGACY_POSITIONAL_DEPENDSON || scenario == Scenario.UNKNOWN_DEPENDSON_KEY) """{
                    "overallVerdict":"ACCEPT","summary":"Beide kandidaten zijn klein en herleidbaar.",
                    "issues":[],
                    "candidateReviews":[
                      {"candidateIndex":0,"verdict":"ACCEPT","reason":"Kleine toetsbare scope met expliciete broninformatie."},
                      {"candidateIndex":1,"verdict":"ACCEPT","reason":"Kleine toetsbare scope met expliciete broninformatie."}
                    ],
                    "requiredChanges":[]
                }""" else if (scenario == Scenario.WARNING_ONLY_REVISE) """{
                    "overallVerdict":"REVISE","summary":"De kandidaat is veilig, maar kan later preciezer.",
                    "issues":[{"severity":"WARNING","category":"ACCESSIBILITY","description":"Controleer de aankondiging ook handmatig.","candidateIndex":0}],
                    "candidateReviews":[{"candidateIndex":0,"verdict":"REVISE","reason":"Leg de waarschuwing vast voor vervolgwerk."}],
                    "requiredChanges":["Controleer later met een schermlezer."]
                }""" else if (scenario == Scenario.REVISE ||
                    (scenario in setOf(Scenario.REVISE_THEN_ACCEPT, Scenario.RESUME_THEN_ACCEPT) && firstAttempt)) """{
                    "overallVerdict":"REVISE","summary":"Het voorstel is te breed voor één toetsbare iteratie.",
                    "issues":[{"severity":"BLOCKING","category":"SCOPE","description":"De kandidaat combineert vijf zelfstandige productrisico's.","candidateIndex":0}],
                    "candidateReviews":[{"candidateIndex":0,"verdict":"REVISE","reason":"Beperk tot één brontransparante locatieflow."}],
                    "requiredChanges":["Splits het voorstel in één kleine locatieflow."]
                }""" else """{
                    "overallVerdict":"ACCEPT","summary":"De richting is klein, herleidbaar en behandelt rechten, privacy en toegankelijkheid.",
                    "issues":[{"severity":"INFO","category":"RIGHTS","description":"Controleer rechten later ook per geïmporteerd object.","candidateIndex":0}],
                    "candidateReviews":[{"candidateIndex":0,"verdict":"ACCEPT","reason":"Kleine toetsbare scope met expliciete broninformatie."}],
                    "requiredChanges":[]
                }"""
                "summary" -> """{"summary":"Dit is een korte, voor-dummies samenvatting van de testcyclus."}"""
                else -> error("Onbekende testrol ${task.taskType}")
            }
            return AgentResult(task.runId, "COMPLETED", json)
        }
    }

    class FakeWorkspacePublicationPort : WorkspacePublicationPort, WorkspaceVisionPort {
        val artifacts = mutableListOf<WorkspaceArtifact>()
        var vision: String? = null
        override fun readVision(productSlug: String): String? = vision
        override fun publish(artifact: WorkspaceArtifact): WorkspacePublicationView {
            artifacts += artifact
            return WorkspacePublicationView(
                artifact.runId,
                artifact.productSlug,
                artifact.relativePath,
                sha256(artifact.content),
                "COMMITTED_LOCAL",
                null,
                "test-commit-${artifacts.size}",
            )
        }

        private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @TestConfiguration
    class Fakes {
        @Bean @Primary fun fakeAgentBridge() = FakeShadowAgentBridge()
        @Bean @Primary fun fakeWorkspacePublicationPort() = FakeWorkspacePublicationPort()
    }
}
