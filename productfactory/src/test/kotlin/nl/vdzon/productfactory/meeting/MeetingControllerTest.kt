package nl.vdzon.productfactory.meeting

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.product.CreateProductRequest
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * Dekt de overleg-REST-endpoints end-to-end via MockMvc, met een nep-AI-antwoord (net als
 * ShadowIterationEngineTest.FakeShadowAgentBridge) zodat er geen echte agentworker nodig is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(MeetingControllerTest.Fakes::class)
class MeetingControllerTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper,
    @Autowired private val products: ProductCatalog,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val slug = "meeting-controller-test"

    @BeforeEach
    fun ensureTestProduct() {
        runCatching {
            products.create(
                CreateProductRequest(
                    slug = slug,
                    name = "Overlegcontrollertest",
                    mission = "Test de overlegendpoints",
                    status = "active",
                    developmentMode = "manual",
                ).configuration(),
            )
        }
        jdbc.update("delete from meeting_message where product_slug = ?", slug)
        jdbc.update("delete from meeting where product_slug = ?", slug)
        // agent_run.run_id is uniek; overleg-run-ID's zijn deterministisch afgeleid van meeting-ID
        // (dat zelf weer begint bij sequence 1 na de reset hierboven), dus die moeten ook weg.
        jdbc.update("delete from agent_run where product_slug = ?", slug)
    }

    @Test
    fun `a meeting can be started, chatted in, and closed via the REST API`() {
        val started = mvc.post("/api/products/$slug/meetings").andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("OPEN") }
            jsonPath("$.initiator") { value("owner") }
        }.andReturn()
        val meetingId = mapper.readTree(started.response.contentAsString).path("id").asText()

        mvc.post("/api/products/$slug/meetings/$meetingId/messages") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"Wat vind je van de huidige richting?"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.sender") { value("ai") }
            jsonPath("$.content") { value("Nepantwoord van de AI.") }
        }

        mvc.get("/api/products/$slug/meetings/$meetingId/messages").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].sender") { value("owner") }
            jsonPath("$[1].sender") { value("ai") }
        }

        mvc.post("/api/products/$slug/meetings/$meetingId/close").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CLOSED") }
            jsonPath("$.outcomeSummary") { value("Nepsamenvatting van het overleg.") }
        }

        mvc.get("/api/products/$slug/meetings").andExpect {
            status { isOk() }
            jsonPath("$[0].status") { value("CLOSED") }
        }
    }

    @Test
    fun `starting a second meeting while one is still open is rejected`() {
        mvc.post("/api/products/$slug/meetings").andExpect { status { isCreated() } }
        mvc.post("/api/products/$slug/meetings").andExpect { status { isConflict() } }
    }

    @Test
    fun `a message cannot be sent after the meeting is closed`() {
        val started = mvc.post("/api/products/$slug/meetings").andReturn()
        val meetingId = mapper.readTree(started.response.contentAsString).path("id").asText()
        mvc.post("/api/products/$slug/meetings/$meetingId/close")

        mvc.post("/api/products/$slug/meetings/$meetingId/messages") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"Te laat"}"""
        }.andExpect { status { isConflict() } }
    }

    @TestConfiguration
    class Fakes {
        @Bean
        @Primary
        fun fakeAgentDispatch(): AgentDispatchPort = AgentDispatchPort { task ->
            val summary = when (task.taskType) {
                "meeting-chat" -> """{"reply":"Nepantwoord van de AI."}"""
                "meeting-close" -> """{"outcomeSummary":"Nepsamenvatting van het overleg."}"""
                else -> """{"summary":"onbekend"}"""
            }
            AgentResult(runId = task.runId, status = "COMPLETED", summary = summary)
        }
    }
}
