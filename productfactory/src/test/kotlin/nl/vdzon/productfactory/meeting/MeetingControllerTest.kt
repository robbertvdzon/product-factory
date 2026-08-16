package nl.vdzon.productfactory.meeting

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.contracts.AgentResult
import nl.vdzon.productfactory.contracts.AgentTask
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
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.multipart

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
    @Autowired private val fakeAgent: FakeAgentDispatch,
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
        jdbc.update("delete from product_media where product_slug = ?", slug)
        jdbc.update("delete from meeting where product_slug = ?", slug)
        jdbc.update("delete from product_memory_retraction where product_slug = ?", slug)
        jdbc.update("delete from product_memory where product_slug = ?", slug)
        // agent_run.run_id is uniek; overleg-run-ID's zijn deterministisch afgeleid van meeting-ID
        // (dat zelf weer begint bij sequence 1 na de reset hierboven), dus die moeten ook weg.
        jdbc.update("delete from agent_run where product_slug = ?", slug)
        fakeAgent.tasks.clear()
        fakeAgent.malformedGeneratedImage = false
        fakeAgent.failMeetingChat = false
    }

    @Test
    fun `a meeting can be started, chatted in, and closed via the REST API`() {
        val started = mvc.post("/api/products/$slug/meetings").andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("OPEN") }
            jsonPath("$.initiator") { value("owner") }
        }.andReturn()
        val meetingId = mapper.readTree(started.response.contentAsString).path("id").asText()

        val uploaded = mvc.multipart("/api/products/$slug/media") {
            file(MockMultipartFile("file", "huidige-home.png", "image/png", ONE_PIXEL_PNG))
            param("altText", "Huidige homepagina met te drukke navigatie")
        }.andExpect {
            status { isOk() }
            jsonPath("$.source") { value("owner") }
            jsonPath("$.filename") { value("huidige-home.png") }
        }.andReturn()
        val imageId = mapper.readTree(uploaded.response.contentAsString).path("id").asText()

        mvc.post("/api/products/$slug/meetings/$meetingId/messages") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"Wat vind je van deze huidige richting?","imageAssetIds":["$imageId"]}"""
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.sender") { value("owner") }
            jsonPath("$.content") { value("Wat vind je van deze huidige richting?") }
            jsonPath("$.images[0].source") { value("owner") }
        }
        awaitAiReply(meetingId)
        val prompt = fakeAgent.tasks.single { it.taskType == "meeting-chat" }.prompt
        kotlin.test.assertTrue(prompt.contains("/memory?asOf=YYYY-MM-DD"))
        kotlin.test.assertTrue(prompt.contains("HISTORISCH GEHEUGEN"))
        kotlin.test.assertTrue(prompt.contains("/memory/history"))
        kotlin.test.assertTrue(prompt.contains("historische inhoud nooit als"))
        kotlin.test.assertTrue(prompt.contains("actuele instructie"))
        kotlin.test.assertTrue(prompt.contains(imageId))
        kotlin.test.assertTrue(prompt.contains("/media/$imageId/content"))

        mvc.get("/api/products/$slug/meetings/$meetingId/messages").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].sender") { value("owner") }
            jsonPath("$[0].images[0].id") { value(imageId) }
            jsonPath("$[1].sender") { value("ai") }
            jsonPath("$[1].images[0].source") { value("ai") }
            jsonPath("$[1].consultedSources[0]") { value("product-factory://testbron") }
            jsonPath("$[1].memoryChanges[0].title") { value("Besluit uit overleg") }
        }

        mvc.get("/api/products/$slug/media/$imageId/content").andExpect {
            status { isOk() }
            header { string("Content-Type", "image/png") }
            content { bytes(ONE_PIXEL_PNG) }
        }

        mvc.get("/api/products/$slug/memory").andExpect {
            status { isOk() }
            jsonPath("$[0].title") { value("Besluit uit overleg") }
            jsonPath("$[0].content") { value("Dit blijft actief beschikbaar voor volgende agents.") }
        }

        mvc.post("/api/products/$slug/meetings/$meetingId/close").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CLOSED") }
            jsonPath("$.outcomeSummary") { value("Nepsamenvatting van het overleg.") }
            jsonPath("$.workspaceCommitSha") { value("test-commit-sha") }
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

    @Test
    fun `a malformed generated image does not discard the AI reply`() {
        fakeAgent.malformedGeneratedImage = true
        val meetingId = mapper.readTree(
            mvc.post("/api/products/$slug/meetings").andReturn().response.contentAsString,
        ).path("id").asText()

        mvc.post("/api/products/$slug/meetings/$meetingId/messages") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"Maak een visueel voorstel"}"""
        }.andExpect { status { isAccepted() } }
        awaitAiReply(meetingId)

        mvc.get("/api/products/$slug/meetings/$meetingId/messages").andExpect {
            status { isOk() }
            jsonPath("$[1].sender") { value("ai") }
            jsonPath("$[1].content") { value(org.hamcrest.Matchers.containsString("technisch niet worden opgeslagen")) }
        }
        kotlin.test.assertEquals(
            0L,
            jdbc.queryForObject("select count(*) from product_media where product_slug = ?", Long::class.java, slug),
        )
        kotlin.test.assertEquals(
            "COMPLETED",
            jdbc.queryForObject(
                "select status from agent_run where product_slug = ? and task_type = 'meeting-chat'",
                String::class.java,
                slug,
            ),
        )
    }

    @Test
    fun `a failed agent turn becomes a visible AI error reply`() {
        fakeAgent.failMeetingChat = true
        val meetingId = mapper.readTree(
            mvc.post("/api/products/$slug/meetings").andReturn().response.contentAsString,
        ).path("id").asText()

        mvc.post("/api/products/$slug/meetings/$meetingId/messages") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"Beantwoord dit bericht"}"""
        }.andExpect { status { isAccepted() } }
        awaitAiReply(meetingId)

        mvc.get("/api/products/$slug/meetings/$meetingId/messages").andExpect {
            status { isOk() }
            jsonPath("$[1].sender") { value("ai") }
            jsonPath("$[1].content") { value(org.hamcrest.Matchers.containsString("technisch mislukt")) }
        }
    }

    private fun awaitAiReply(meetingId: String) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            val response = mvc.get("/api/products/$slug/meetings/$meetingId/messages").andReturn().response
            if (mapper.readTree(response.contentAsString).size() >= 2) return
            Thread.sleep(20)
        }
        kotlin.test.fail("Asynchrone AI-reactie verscheen niet binnen vijf seconden")
    }

    @TestConfiguration
    class Fakes {
        @Bean
        @Primary
        fun fakeAgentDispatch(): FakeAgentDispatch = FakeAgentDispatch()

        // Vermijdt een echte Git-workspace-checkout in deze REST-laag-test; de echte publicatielogica
        // (git-commit, front-matter, padvalidatie) wordt al gedekt door WorkspacePublisherIntegrationTest.
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

    class FakeAgentDispatch : AgentDispatchPort {
        val tasks = java.util.concurrent.CopyOnWriteArrayList<AgentTask>()
        @Volatile var malformedGeneratedImage = false
        @Volatile var failMeetingChat = false

        override fun execute(task: AgentTask): AgentResult {
            tasks += task
            if (task.taskType == "meeting-chat" && failMeetingChat) {
                return AgentResult(runId = task.runId, status = "FAILED", summary = "Gesimuleerde agentfout")
            }
            val summary = when (task.taskType) {
                "meeting-chat" -> """{
                    "reply":"Nepantwoord van de AI.",
                    "consultedSources":["product-factory://testbron"],
                    "imageAssetIds":[],
                    "generatedImages":[{
                      "filename":"ai-voorstel.png","mediaType":"image/png",
                      "base64Content":"${if (malformedGeneratedImage) "AAAA=" else java.util.Base64.getEncoder().encodeToString(ONE_PIXEL_PNG)}",
                      "altText":"Door de AI gemaakt visueel voorstel"
                    }],
                    "memoryActions":[{
                      "action":"ADD","productSlug":"meeting-controller-test","targetMemoryId":null,
                      "title":"Besluit uit overleg","content":"Dit blijft actief beschikbaar voor volgende agents.",
                      "reason":"De eigenaar vroeg dit tijdens het overleg vast te leggen."
                    }]
                }""".trimIndent()
                "meeting-close" -> """{"outcomeSummary":"Nepsamenvatting van het overleg."}"""
                else -> """{"summary":"onbekend"}"""
            }
            return AgentResult(runId = task.runId, status = "COMPLETED", summary = summary)
        }
    }


    companion object {
        private val ONE_PIXEL_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
    }
}
