package nl.vdzon.productfactory.iteration

import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.contracts.WorkspacePublicationView
import nl.vdzon.productfactory.workspace.api.WorkspaceArtifact
import nl.vdzon.productfactory.workspace.api.WorkspacePublicationPort
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
) {
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
        assertEquals(6, repository.artifacts("hkh-autopilot", accepted.id).size)
        assertTrue(repository.artifacts("hkh-autopilot", accepted.id).any { it.artifactType == "product_owner" })
        assertEquals(1, workspace.artifacts.size)
        assertTrue(workspace.artifacts.single().content.contains("Rechtenindicatie"))
        assertTrue(workspace.artifacts.single().content.contains("run_id: ${accepted.id}"))

        bridge.scenario = Scenario.DUPLICATE
        val duplicate = repository.create("hkh-autopilot", "Controleer een mogelijk dubbel voorstel")
        engine.run(duplicate.id)
        assertEquals("REJECTED", repository.require("hkh-autopilot", duplicate.id).status)
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
        assertEquals(10, repository.steps("hkh-autopilot", revision.id).size)
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

    enum class Scenario { ACCEPT, DUPLICATE, REVISE, REVISE_THEN_ACCEPT, AUTONOMY_REVISE_THEN_ACCEPT, WARNING_ONLY_REVISE }

    class FakeShadowAgentBridge : ShadowAgentBridge {
        var scenario = Scenario.ACCEPT
        override fun execute(task: AgentTask): AgentResult {
            val today = LocalDate.now(ZoneId.of("Europe/Amsterdam"))
            val firstAttempt = task.runId.endsWith("-1")
            val different = scenario == Scenario.REVISE || (scenario == Scenario.REVISE_THEN_ACCEPT && firstAttempt)
            val json = when (task.taskType.removePrefix("shadow-")) {
                "researcher" -> """{
                    "summary":"Open erfgoedbronnen kunnen een controleerbare eerste zoekervaring ondersteunen.",
                    "findings":[{"title":"Open collecties","finding":"Noord-Hollands Archief en Rijksmuseum bieden publiek beschreven collecties met herleidbare objectpagina's.","sourceUrls":["https://noord-hollandsarchief.nl/","https://www.rijksmuseum.nl/nl/rijksstudio"]}],
                    "sources":[
                      {"url":"https://noord-hollandsarchief.nl/","consultedOn":"$today","rightsIndication":"Rechten verschillen per object en moeten op de objectpagina worden gecontroleerd.","rationale":"Regionale bron voor Noord-Hollandse archiefcollecties."},
                      {"url":"https://www.rijksmuseum.nl/nl/rijksstudio","consultedOn":"$today","rightsIndication":"Beschikbaarheid en rechten staan per object vermeld.","rationale":"Voorbeeld van een doorzoekbare Nederlandse erfgoedcollectie."}
                    ]
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
                "story_writer" -> """{
                    "candidates":[{
                      "title":"${when {
                          different -> "Brede erfgoedportal"
                          scenario == Scenario.REVISE_THEN_ACCEPT -> "Herziene bronnenkaart voor één locatie"
                          scenario == Scenario.AUTONOMY_REVISE_THEN_ACCEPT && firstAttempt -> "Handmatig geteste bronnenkaart"
                          scenario == Scenario.AUTONOMY_REVISE_THEN_ACCEPT -> "Automatisch geteste bronnenkaart"
                          scenario == Scenario.WARNING_ONLY_REVISE -> "Toegankelijke bronnenkaart met waarschuwing"
                          else -> "Bronnenkaart voor één locatie"
                      }}",
                      "description":"${when {
                          different -> "Bouw in één keer zoeken, kaarten, tijdlijnen, beeldherkenning en reconstructies voor alle bronnen."
                          scenario == Scenario.REVISE_THEN_ACCEPT -> "Toon voor één historische locatie een herzien verhaal met herleidbare bron- en rechteninformatie."
                          scenario == Scenario.WARNING_ONLY_REVISE -> "Toon één historische locatie en bewaar een niet-blokkerende toegankelijkheidswaarschuwing."
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
                "critic" -> if (scenario == Scenario.WARNING_ONLY_REVISE) """{
                    "overallVerdict":"REVISE","summary":"De kandidaat is veilig, maar kan later preciezer.",
                    "issues":[{"severity":"WARNING","category":"ACCESSIBILITY","description":"Controleer de aankondiging ook handmatig.","candidateIndex":0}],
                    "candidateReviews":[{"candidateIndex":0,"verdict":"REVISE","reason":"Leg de waarschuwing vast voor vervolgwerk."}],
                    "requiredChanges":["Controleer later met een schermlezer."]
                }""" else if (scenario == Scenario.REVISE || (scenario == Scenario.REVISE_THEN_ACCEPT && firstAttempt)) """{
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

    class FakeWorkspacePublicationPort : WorkspacePublicationPort {
        val artifacts = mutableListOf<WorkspaceArtifact>()
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
