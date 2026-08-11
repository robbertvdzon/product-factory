package nl.vdzon.productfactory.roadmap.api

import nl.vdzon.productfactory.product.CreateProductRequest
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class RoadmapCatalogTest(
    @Autowired private val roadmap: RoadmapCatalog,
    @Autowired private val products: ProductCatalog,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val slug = "roadmap-catalog-test"

    @BeforeEach
    fun ensureTestProduct() {
        runCatching {
            products.create(
                CreateProductRequest(
                    slug = slug,
                    name = "Roadmaptestproduct",
                    mission = "Test de roadmap-CRUD",
                    status = "active",
                    developmentMode = "manual",
                ).configuration(),
            )
        }
        jdbc.update("delete from roadmap_settled_question where product_slug = ?", slug)
        jdbc.update("delete from roadmap_theme where product_slug = ?", slug)
    }

    @Test
    fun `a created theme starts open with the given fields`() {
        val theme = roadmap.createTheme(slug, "UX verbeteren", "Navigatie begrijpelijker maken", "HIGH")

        assertEquals("UX verbeteren", theme.title)
        assertEquals("HIGH", theme.priority)
        assertEquals("OPEN", theme.status)
        assertNull(theme.closedAt)
    }

    @Test
    fun `an invalid priority is rejected`() {
        assertFailsWith<IllegalArgumentException> { roadmap.createTheme(slug, "Iets", "Iets anders", "URGENT") }
    }

    @Test
    fun `updateTheme changes only the given fields`() {
        val theme = roadmap.createTheme(slug, "Archiefintegratie", "Bronnen doorzoekbaar maken", "MEDIUM")

        val updated = roadmap.updateTheme(slug, theme.id, priority = "LOW")

        assertEquals("Archiefintegratie", updated.title)
        assertEquals("LOW", updated.priority)
        assertEquals("OPEN", updated.status)
    }

    @Test
    fun `closeTheme sets status DONE and stamps closedAt`() {
        val theme = roadmap.createTheme(slug, "Toegankelijkheid", "Schermlezerondersteuning op orde", "LOW")

        val closed = roadmap.closeTheme(slug, theme.id)

        assertEquals("DONE", closed.status)
        assertTrue(closed.closedAt != null)
    }

    @Test
    fun `require on an unknown theme returns not found`() {
        assertFailsWith<ResponseStatusException> { roadmap.requireTheme(slug, "theme-$slug-9999") }
    }

    @Test
    fun `settled questions are listed newest first`() {
        roadmap.addSettledQuestion(slug, "Archief X is publiek benaderbaar zonder token")
        roadmap.addSettledQuestion(slug, "Rijksmuseum-API vereist geen authenticatie")

        val questions = roadmap.listSettledQuestions(slug)

        assertEquals(2, questions.size)
        assertEquals("Rijksmuseum-API vereist geen authenticatie", questions.first().content)
    }
}
