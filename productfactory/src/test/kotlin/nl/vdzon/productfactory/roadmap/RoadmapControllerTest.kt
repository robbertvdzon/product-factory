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
        jdbc.update("delete from roadmap_theme where product_slug = ?", slug)
    }

    @Test
    fun `a theme can be created, updated, closed and read back via the REST API`() {
        val created = mvc.post("/api/products/$slug/roadmap/themes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"UX verbeteren","description":"Navigatie begrijpelijker maken","priority":"HIGH"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("OPEN") }
        }.andReturn()
        val id = mapper.readTree(created.response.contentAsString).path("id").asText()

        mvc.put("/api/products/$slug/roadmap/themes/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"priority":"LOW"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.priority") { value("LOW") }
            jsonPath("$.title") { value("UX verbeteren") }
        }

        mvc.post("/api/products/$slug/roadmap/themes/$id/close").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DONE") }
        }

        mvc.get("/api/products/$slug/roadmap/themes").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
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
    fun `an invalid priority is rejected with a 400`() {
        mvc.post("/api/products/$slug/roadmap/themes") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Iets","description":"Iets anders","priority":"URGENT"}"""
        }.andExpect { status { isBadRequest() } }
    }
}
