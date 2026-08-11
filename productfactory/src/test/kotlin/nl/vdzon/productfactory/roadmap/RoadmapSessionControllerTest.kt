package nl.vdzon.productfactory.roadmap

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.WorkspacePublicationView
import nl.vdzon.productfactory.product.CreateProductRequest
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.workspace.api.WorkspacePublicationPort
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
        jdbc.update("delete from roadmap_session where product_slug = ?", slug)
        jdbc.update("delete from roadmap_theme where product_slug = ?", slug)
        jdbc.update("delete from roadmap_settled_question where product_slug = ?", slug)
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
                "roadmap-session" -> """{"summary":"Nepsamenvatting van de roadmap-sessie.","themeUpdates":[{"action":"CREATE","themeId":null,"title":"UX verbeteren","description":"Navigatie begrijpelijker maken voor nieuwe bezoekers.","priority":"HIGH"}],"settledQuestions":["Archief X is publiek benaderbaar zonder token"]}"""
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
    }
}
