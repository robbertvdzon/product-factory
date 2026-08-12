package nl.vdzon.productfactory.roadmap

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.product.CreateProductRequest
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

@SpringBootTest
@AutoConfigureMockMvc
class RoadmapControllerTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val mapper: ObjectMapper,
    @Autowired private val products: ProductCatalog,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val slug = "roadmap-controller-test"

    @BeforeEach
    fun ensureTestProduct() {
        runCatching {
            products.create(
                CreateProductRequest(
                    slug = slug,
                    name = "Roadmapcontrollertest",
                    mission = "Test de roadmap-endpoints",
                    status = "active",
                    developmentMode = "manual",
                ).configuration(),
            )
        }
        jdbc.update("delete from roadmap_settled_question where product_slug = ?", slug)
        jdbc.update(
            "delete from roadmap_epic_dependency where epic_id in (select id from roadmap_theme where product_slug = ?)",
            slug,
        )
        jdbc.update("delete from roadmap_theme where product_slug = ?", slug)
    }

    @Test
    fun `an epic can be created ranked updated closed and read back via the REST API`() {
        mvc.post("/api/products/$slug/roadmap/epics") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Fundament","description":"Technisch fundament neerzetten"}"""
        }.andExpect { status { isCreated() } }
        val created = mvc.post("/api/products/$slug/roadmap/epics") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"UX verbeteren","description":"Navigatie begrijpelijker maken"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("OPEN") }
            jsonPath("$.customerRank") { value(2) }
            jsonPath("$.processRank") { value(2) }
            jsonPath("$.priorityScore") { value(0) }
        }.andReturn()
        val id = mapper.readTree(created.response.contentAsString).path("id").asText()

        mvc.put("/api/products/$slug/roadmap/epics/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"customerRank":1}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.customerRank") { value(1) }
            jsonPath("$.priorityScore") { value(75) }
            jsonPath("$.title") { value("UX verbeteren") }
        }

        mvc.post("/api/products/$slug/roadmap/epics/$id/close").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DONE") }
        }

        mvc.get("/api/products/$slug/roadmap/epics").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].status") { value("DONE") }
        }
    }

    @Test
    fun `settled questions can be added and are listed`() {
        mvc.post("/api/products/$slug/roadmap/settled-questions") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"content":"Archief X is publiek benaderbaar zonder token"}"""
        }.andExpect { status { isCreated() } }

        mvc.get("/api/products/$slug/roadmap/settled-questions").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].content") { value("Archief X is publiek benaderbaar zonder token") }
        }
    }

    @Test
    fun `a title longer than the compact card limit is rejected`() {
        mvc.post("/api/products/$slug/roadmap/epics") {
            contentType = MediaType.APPLICATION_JSON
            content = mapper.writeValueAsString(
                mapOf("title" to "x".repeat(81), "description" to "Iets anders"),
            )
        }.andExpect { status { isBadRequest() } }
    }
}
