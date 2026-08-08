package nl.vdzon.productfactory

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
class ProductFactoryApiTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper,
) {
    @Test
    fun `HKH is absent and HKH Autopilot is initially configured`() {
        mvc.get("/api/products").andExpect {
            status { isOk() }
            jsonPath("$[?(@.slug == 'hkh')]") { isEmpty() }
            jsonPath("$[?(@.slug == 'hkh-autopilot')]") { isNotEmpty() }
        }
        mvc.get("/api/products/hkh").andExpect { status { isNotFound() } }
        mvc.get("/api/products/hkh-autopilot").andExpect {
            status { isOk() }
            jsonPath("$.workspaceOwnership") { value("product-factory") }
            jsonPath("$.developmentMode") { value("autonomous") }
            jsonPath("$.allowedWritePaths[0]") { exists() }
        }
    }

    @Test
    fun `two arbitrary products can be added and their data never leaks`() {
        createProduct("castle-guide", "Kasteelgids", "Ontsluit kastelen")
        createProduct("archive-explorer", "Archiefverkenner", "Doorzoek archieven")

        createStory("castle-guide", "Kasteelstory")
        createStory("archive-explorer", "Archiefstory")
        mvc.get("/api/story-candidates?productSlug=castle-guide").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("Kasteelstory") }
        }
        mvc.get("/api/story-candidates?productSlug=archive-explorer").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("Archiefstory") }
        }

        addResearch("castle-guide", "Kasteelbron")
        addResearch("archive-explorer", "Archiefbron")
        registerRun("castle-guide", "castle-run")
        registerRun("archive-explorer", "archive-run")
        mvc.get("/api/products/castle-guide/research").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("Kasteelbron") }
        }
        mvc.get("/api/products/archive-explorer/research").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].title") { value("Archiefbron") }
        }
        mvc.get("/api/agent-runs?productSlug=castle-guide").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].runId") { value("castle-run") }
        }
        mvc.get("/api/agent-runs/archive-run?productSlug=castle-guide").andExpect { status { isNotFound() } }
    }

    @Test
    fun `pause is isolated and story publication requires autonomous approval plus merged evidence`() {
        createOwnerProduct("owner-led")
        mvc.post("/api/products/hkh-autopilot/pause").andExpect { status { isOk() }; jsonPath("$.status") { value("paused") } }
        mvc.post("/api/story-candidates") {
            contentType = MediaType.APPLICATION_JSON
            content = storyJson("hkh-autopilot", "Geblokkeerd")
        }.andExpect { status { isConflict() } }

        mvc.get("/api/products/owner-led").andExpect { status { isOk() }; jsonPath("$.status") { value("active") } }
        mvc.post("/api/products/hkh-autopilot/resume").andExpect { status { isOk() }; jsonPath("$.status") { value("active") } }

        val ownerStory = createStory("owner-led", "Handmatige story")
        mvc.post("/api/story-candidates/$ownerStory/publish") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"productSlug":"owner-led"}"""
        }.andExpect { status { isConflict() } }

        val autopilotStory = createStory("hkh-autopilot", "Autonome story")
        mvc.post("/api/story-candidates/$autopilotStory/publish") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"productSlug":"hkh-autopilot"}"""
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `product settings can be switched between AI providers and reject unknown model combinations`() {
        createProduct("settings-test", "Instellingen", "Test instellingen bijwerken")

        mvc.get("/api/ai-catalog").andExpect {
            status { isOk() }
            jsonPath("$.codex") { isNotEmpty() }
            jsonPath("$.claude") { isNotEmpty() }
        }

        mvc.put("/api/products/settings-test/settings") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"iterationTimes":["03:00","08:00","21:00"],"aiProvider":"claude","aiModel":"claude-sonnet-5","maxStoriesPerCycle":5}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.iterationTimes.length()") { value(3) }
            jsonPath("$.iterationTimes[0]") { value("03:00") }
            jsonPath("$.aiProvider") { value("claude") }
            jsonPath("$.aiModel") { value("claude-sonnet-5") }
            jsonPath("$.maxStoriesPerCycle") { value(5) }
        }

        mvc.put("/api/products/settings-test/settings") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"aiProvider":"claude","aiModel":"gpt-5.6-terra"}"""
        }.andExpect { status { isBadRequest() } }

        mvc.put("/api/products/settings-test/settings") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"iterationTimes":[]}"""
        }.andExpect { status { isBadRequest() } }

        mvc.put("/api/products/settings-test/settings") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"iterationTimes":["25:00"]}"""
        }.andExpect { status { isBadRequest() } }

        // aiModel bleef ongewijzigd na de afgewezen updates.
        mvc.get("/api/products/settings-test").andExpect {
            status { isOk() }
            jsonPath("$.aiModel") { value("claude-sonnet-5") }
        }
    }

    private fun createProduct(slug: String, name: String, mission: String) {
        mvc.post("/api/products") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"slug":"$slug","name":"$name","mission":"$mission","status":"active","developmentMode":"autonomous"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.slug") { value(slug) }
            jsonPath("$.workspaceDirectory") { value("products/$slug") }
        }
    }

    private fun createOwnerProduct(slug: String) {
        mvc.post("/api/products") {
            contentType = MediaType.APPLICATION_JSON
            content = """{
                "slug":"$slug",
                "name":"Door eigenaar beheerd product",
                "mission":"Test handmatige productregie",
                "status":"active",
                "developmentMode":"manual",
                "workspaceOwnership":"owner"
            }""".trimIndent()
        }.andExpect {
            status { isCreated() }
            jsonPath("$.workspaceOwnership") { value("owner") }
        }
    }

    private fun createStory(slug: String, title: String): Long {
        val result = mvc.post("/api/story-candidates") {
            contentType = MediaType.APPLICATION_JSON
            content = storyJson(slug, title)
        }.andExpect { status { isCreated() } }.andReturn()
        return mapper.readTree(result.response.contentAsString).path("id").asLong()
    }

    private fun addResearch(slug: String, title: String) {
        mvc.post("/api/products/$slug/research") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"$title","content":"Onderbouwde bevinding"}"""
        }.andExpect { status { isCreated() } }
    }

    private fun registerRun(slug: String, runId: String) {
        mvc.post("/api/agent-runs") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"runId":"$runId","productSlug":"$slug","taskType":"research"}"""
        }.andExpect { status { isCreated() } }
    }

    private fun storyJson(slug: String, title: String) =
        """{"productSlug":"$slug","title":"$title","description":"Kleine toetsbare productstap"}"""
}
