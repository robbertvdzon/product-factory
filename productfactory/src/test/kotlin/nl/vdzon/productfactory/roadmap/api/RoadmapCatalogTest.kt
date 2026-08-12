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
        jdbc.update(
            "delete from roadmap_epic_dependency where epic_id in (select id from roadmap_theme where product_slug = ?)",
            slug,
        )
        jdbc.update("delete from roadmap_theme where product_slug = ?", slug)
    }

    @Test
    fun `a created epic starts open at the end of both rankings`() {
        roadmap.createEpic(slug, "Archief ontsluiten", "Bronnen doorzoekbaar maken")
        val epic = roadmap.createEpic(slug, "UX verbeteren", "Navigatie begrijpelijker maken")

        assertEquals("UX verbeteren", epic.title)
        assertEquals(2, epic.customerRank)
        assertEquals(2, epic.processRank)
        assertEquals(2, epic.roadmapRank)
        assertEquals(0, epic.priorityScore)
        assertEquals("OPEN", epic.status)
        assertNull(epic.closedAt)
    }

    @Test
    fun `customer and process can independently reorder epics`() {
        val first = roadmap.createEpic(slug, "Eerste", "Eerste beschrijving")
        val second = roadmap.createEpic(slug, "Tweede", "Tweede beschrijving")
        val third = roadmap.createEpic(slug, "Derde", "Derde beschrijving")

        roadmap.updateEpicFromCustomer(slug, third.id, customerRank = 1)
        roadmap.updateEpicFromProcess(slug, second.id, processRank = 1)

        val byId = roadmap.listEpics(slug).associateBy { it.id }
        assertEquals(1, byId.getValue(third.id).customerRank)
        assertEquals(2, byId.getValue(first.id).customerRank)
        assertEquals(3, byId.getValue(second.id).customerRank)
        assertEquals(1, byId.getValue(second.id).processRank)
        assertEquals(2, byId.getValue(first.id).processRank)
        assertEquals(3, byId.getValue(third.id).processRank)
        assertEquals(75, byId.getValue(third.id).priorityScore)
        assertEquals(25, byId.getValue(second.id).priorityScore)
        assertEquals(50, byId.getValue(first.id).priorityScore)
    }

    @Test
    fun `dependencies override score in roadmap order`() {
        val foundation = roadmap.createEpic(slug, "Fundament", "Technisch fundament neerzetten")
        val visible = roadmap.createEpic(slug, "Publieke UX", "De belangrijkste klantreis verbeteren")
        roadmap.updateEpicFromCustomer(slug, visible.id, customerRank = 1)
        roadmap.updateEpicFromProcess(slug, visible.id, processRank = 1)

        val dependent = roadmap.updateEpicFromCustomer(slug, visible.id, dependencyIds = setOf(foundation.id))

        assertEquals(listOf(foundation.id), dependent.blockedByIds)
        assertEquals(2, dependent.roadmapRank)
        assertEquals(listOf(visible.id), roadmap.requireEpic(slug, foundation.id).blocksIds)
    }

    @Test
    fun `a dependency cycle is rejected and rolled back`() {
        val first = roadmap.createEpic(slug, "Eerste", "Eerste beschrijving")
        val second = roadmap.createEpic(slug, "Tweede", "Tweede beschrijving", dependencyIds = setOf(first.id))

        assertFailsWith<IllegalArgumentException> {
            roadmap.updateEpicFromCustomer(slug, first.id, dependencyIds = setOf(second.id))
        }
        assertTrue(roadmap.requireEpic(slug, first.id).dependencyIds.isEmpty())
    }

    @Test
    fun `closing an epic stamps closedAt and unblocks dependents`() {
        val first = roadmap.createEpic(slug, "Toegankelijkheid", "Schermlezerondersteuning op orde")
        val second = roadmap.createEpic(slug, "Nieuwe flow", "Volgende flow bouwen", dependencyIds = setOf(first.id))

        val closed = roadmap.closeEpic(slug, first.id)

        assertEquals("DONE", closed.status)
        assertTrue(closed.closedAt != null)
        assertTrue(roadmap.requireEpic(slug, second.id).blockedByIds.isEmpty())
    }

    @Test
    fun `require on an unknown epic returns not found`() {
        assertFailsWith<ResponseStatusException> { roadmap.requireEpic(slug, "epic-$slug-9999") }
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
