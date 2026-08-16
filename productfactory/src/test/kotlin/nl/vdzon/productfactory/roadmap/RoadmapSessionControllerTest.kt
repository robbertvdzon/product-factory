package nl.vdzon.productfactory.roadmap

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.WorkspacePublicationView
import nl.vdzon.productfactory.product.CreateProductRequest
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.workspace.api.WorkspacePublicationPort
import nl.vdzon.productfactory.workspace.api.WorkspaceVisionPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.concurrent.TimeUnit

/**
 * Dekt de roadmap-sessie-REST-laag end-to-end via MockMvc, met een nep-AI-antwoord (zelfde patroon
 * als MeetingControllerTest) zodat er geen echte agentworker nodig is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RoadmapSessionControllerTest.Fakes::class)
class RoadmapSessionControllerTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper,
    @Autowired private val products: ProductCatalog,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val slug = "roadmap-session-controller-test"

    @BeforeEach
    fun ensureTestProduct() {
        runCatching {
            products.create(
                CreateProductRequest(
                    slug = slug,
                    name = "Roadmapsessietest",
                    mission = "Test de roadmap-sessieflow",
                    status = "active",
                    developmentMode = "manual",
                ).configuration(),
            )
        }
        jdbc.update("delete from roadmap_future_vision where product_slug = ?", slug)
        jdbc.update("delete from roadmap_session where product_slug = ?", slug)
        jdbc.update("delete from roadmap_theme where product_slug = ?", slug)
        jdbc.update("delete from roadmap_settled_question where product_slug = ?", slug)
        jdbc.update("delete from product_bug where product_slug = ?", slug)
        jdbc.update("delete from agent_run where product_slug = ?", slug)
    }

    @Test
    fun `a session can be started and completes with the roadmap updated`() {
        val started = mvc.post("/api/products/$slug/roadmap/sessions").andExpect {
            status { isAccepted() }
            jsonPath("$.status") { value("QUEUED") }
        }.andReturn()
        val sessionId = mapper.readTree(started.response.contentAsString).path("id").asText()

        awaitCompletion(sessionId)

        mvc.get("/api/products/$slug/roadmap/sessions/$sessionId").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLETED") }
            jsonPath("$.summary") { value("Nepsamenvatting van de roadmap-sessie.") }
        }
        mvc.get("/api/products/$slug/roadmap/themes").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("UX verbeteren") }
            jsonPath("$[0].horizon") { value("NOW") }
            jsonPath("$[0].capabilityKey") { value("betere-navigatie") }
        }
        mvc.get("/api/products/$slug/roadmap/vision").andExpect {
            status { isOk() }
            jsonPath("$.version") { value(1) }
            jsonPath("$.content.northStarTitle") { value("Een vanzelfsprekende museumreis") }
        }
        mvc.get("/api/products/$slug/bugs").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].priority") { value("P1") }
            jsonPath("$[0].sourceType") { value("ROADMAP_SESSION") }
        }
    }

    @Test
    fun `starting a second session while one is active is rejected`() {
        // Simuleert een nog lopende sessie zonder op de (async) eerste aanroep te hoeven wachten.
        jdbc.update(
            "insert into roadmap_session(id, product_slug, sequence_number, status) values (?, ?, 1, 'RUNNING')",
            "roadmap-session-$slug-0001",
            slug,
        )

        mvc.post("/api/products/$slug/roadmap/sessions").andExpect { status { isConflict() } }
    }

    private fun awaitCompletion(sessionId: String) {
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
        while (System.currentTimeMillis() < deadline) {
            val status = jdbc.queryForObject("select status from roadmap_session where id = ?", String::class.java, sessionId)
            if (status in setOf("COMPLETED", "FAILED")) return
            Thread.sleep(50)
        }
        error("Roadmap-sessie $sessionId is niet binnen 10s afgerond")
    }

    @TestConfiguration
    class Fakes {
        @Bean
        @Primary
        fun fakeAgentDispatch(): AgentDispatchPort = AgentDispatchPort { task ->
            val summary = when (task.taskType) {
                "roadmap-visionary" -> visionaryJson()
                "roadmap-strategist" -> strategyJson()
                "roadmap-manager" -> managerJson()
                else -> """{"summary":"onbekend"}"""
            }
            AgentResult(runId = task.runId, status = "COMPLETED", summary = summary)
        }

        @Bean
        @Primary
        fun fakeWorkspacePublicationPort(): WorkspacePublicationPort = WorkspacePublicationPort { artifact ->
            WorkspacePublicationView(
                runId = artifact.runId,
                productSlug = artifact.productSlug,
                artifactPath = artifact.relativePath,
                contentHash = "test-hash",
                status = "COMMITTED_LOCAL",
                pullRequestUrl = null,
                commitSha = "test-commit-sha",
            )
        }

        @Bean
        @Primary
        fun fakeWorkspaceVisionPort(): WorkspaceVisionPort = WorkspaceVisionPort { "Een breed museum voor iedereen." }

        companion object {
            private val experiences = (1..8).joinToString(",") {
                """{"key":"ervaring-$it","title":"Ervaring $it","promise":"Een aansprekende en concrete belofte voor iedere bezoeker.","scenario":"Een bezoeker doorloopt een levendige toekomstige ervaring met verrassende ontdekkingen.","wowFactor":"Dit verbindt informatie op een manier die vandaag nog niet vanzelfsprekend is."}"""
            }
            private val screens = (1..3).joinToString(",") {
                """{"key":"scherm-$it","title":"Conceptscherm $it","viewport":"DESKTOP","eyebrow":"Ontdek","headline":"Een prachtige blik op het verleden","body":"Hier ziet de bezoeker bronnen, verhalen en beelden als één begrijpelijke ervaring.","primaryAction":"Begin met ontdekken","secondaryAction":"Bekijk bronnen","visualDescription":"Een rijk gelaagd landschap met historische beelden en een heldere tijdlijn.","highlights":["Bronnen naast het verhaal","Door de tijd bewegen"]}"""
            }
            private val capabilities = (1..6).joinToString(",") {
                val key = if (it == 1) "betere-navigatie" else "capability-$it"
                val horizon = listOf("NOW", "NEXT", "LATER", "HORIZON")[(it - 1) % 4]
                """{"key":"$key","title":"Capability $it","outcome":"Bezoekers bereiken zelfstandig een betekenisvolle historische ontdekking.","successMeasure":"Minstens één aantoonbare route werkt van begin tot bron.","horizon":"$horizon","experienceKeys":["ervaring-$it"],"feasibility":"${if (it == 1) "PROVEN" else "UNKNOWN"}"}"""
            }

            private fun visionaryJson() = """{"northStarTitle":"Een vanzelfsprekende museumreis","northStar":"Iedere bezoeker kan vanuit gewone nieuwsgierigheid een persoonlijke en betrouwbare reis door de collectie beginnen.","futureNarrative":"Een bezoeker opent de applicatie en ontdekt zonder voorkennis hoe plaatsen, mensen en gebeurtenissen door de tijd met elkaar verbonden zijn. Iedere stap toont rijke beelden, betrouwbare bronnen en verrassende nieuwe routes, zodat een vluchtige vraag uitgroeit tot een betekenisvolle ontdekkingstocht.","experiences":[$experiences],"wildIdeas":["Laat een ruimte reageren op waar de bezoeker kijkt en welke verhalen diegene eerder opende.","Maak een gezamenlijke tijdreis waarin meerdere bezoekers ontdekkingen live met elkaar verbinden.","Laat historische stemmen een plaats vanuit verschillende en soms botsende perspectieven vertellen."],"conceptScreens":[$screens]}"""

            private fun strategyJson() = """{"northStarTitle":"Een vanzelfsprekende museumreis","northStar":"Iedere bezoeker kan vanuit gewone nieuwsgierigheid een persoonlijke en betrouwbare reis door de collectie beginnen.","futureNarrative":"Een bezoeker opent de applicatie en ontdekt zonder voorkennis hoe plaatsen, mensen en gebeurtenissen door de tijd met elkaar verbonden zijn. Iedere stap toont rijke beelden, betrouwbare bronnen en verrassende nieuwe routes, zodat een vluchtige vraag uitgroeit tot een betekenisvolle ontdekkingstocht.","experiences":[$experiences],"capabilities":[$capabilities],"assumptions":[{"key":"bronkwaliteit","statement":"Beschikbare bronmetadata is rijk genoeg voor betrouwbare verbindingen.","risk":"Zonder consistente metadata ontstaan misleidende verbanden.","probeType":"DESK_RESEARCH","proposedProbe":"Vergelijk een representatieve steekproef uit drie publieke collecties.","capabilityKeys":["betere-navigatie"],"feasibility":"UNKNOWN"}],"conceptScreens":[$screens],"visionChangeSummary":"De eerste concrete en ambitieuze producthorizon is vastgesteld."}"""

            private fun managerJson() = """{"summary":"Nepsamenvatting van de roadmap-sessie.","epicUpdates":[{"action":"CREATE","epicId":null,"title":"UX verbeteren","description":"Navigatie begrijpelijker maken voor nieuwe bezoekers.","processRank":1,"dependencyIds":[],"horizon":"NOW","kind":"DELIVERY","capabilityKey":"betere-navigatie"}],"settledQuestions":["Archief X is publiek benaderbaar zonder token"],"bugUpdates":[{"action":"CREATE","bugId":null,"title":"Navigatie opent niet","description":"De primaire navigatie reageert niet op activering.","reproductionSteps":"Open het menu en activeer de eerste link","expectedResult":"De doelpagina wordt geopend","actualResult":"De huidige pagina blijft zichtbaar","priority":"P1"}]}"""
        }
    }
}
