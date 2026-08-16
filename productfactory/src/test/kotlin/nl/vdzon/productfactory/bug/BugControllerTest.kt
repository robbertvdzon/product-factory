package nl.vdzon.productfactory.bug

import nl.vdzon.productfactory.bug.api.BugCatalog
import nl.vdzon.productfactory.bug.api.BugMutation
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
import kotlin.test.assertContains

@SpringBootTest
@AutoConfigureMockMvc
class BugControllerTest(
    @Autowired private val mvc: MockMvc,
    @Autowired private val products: ProductCatalog,
    @Autowired private val bugs: BugCatalog,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val slug = "bug-controller-test"

    @BeforeEach
    fun prepare() {
        runCatching { products.create(CreateProductRequest(slug, "Bugtest", "Test de buglevenscyclus", status = "active").configuration()) }
        jdbc.update("delete from product_bug where product_slug = ?", slug)
    }

    @Test
    fun `bugs are deduplicated and priority and status remain editable`() {
        val mutation = BugMutation(
            "CREATE", null, "Opslaan werkt niet", "Het formulier bewaart geen wijzigingen.",
            "Open instellingen en kies Opslaan", "De wijzigingen blijven bewaard", "De oude waarde blijft staan", "P1",
        )
        bugs.apply(slug, "TEST_SESSION", "test-1", mutation)
        bugs.apply(slug, "ROADMAP_SESSION", "roadmap-1", mutation.copy(description = "Opnieuw gezien tijdens roadmapreview."))

        mvc.get("/api/products/$slug/bugs").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].occurrenceCount") { value(2) }
            jsonPath("$[0].priority") { value("P1") }
        }

        val id = bugs.list(slug).single().id
        mvc.put("/api/products/$slug/bugs/$id") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"priority":"P2","status":"READY_FOR_VERIFICATION"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.priority") { value("P2") }
            jsonPath("$.status") { value("READY_FOR_VERIFICATION") }
        }
    }

    @Test
    fun `important bug awaiting verification blocks a new product cycle`() {
        val bug = bugs.apply(
            slug,
            "TEST_SESSION",
            "test-2",
            BugMutation(
                "CREATE", null, "Kernflow valt uit", "De belangrijkste flow kan niet worden voltooid.",
                "Open de kernflow en bevestig", "De flow wordt voltooid", "De actie stopt zonder resultaat", "P0",
            ),
        ).bug
        bugs.updateManually(slug, bug.id, null, "READY_FOR_VERIFICATION")

        val response = mvc.post("/api/products/$slug/cycles") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect {
            status { isConflict() }
        }.andReturn().response
        assertContains(response.errorMessage.orEmpty(), "wacht op een testsessie")
    }
}
