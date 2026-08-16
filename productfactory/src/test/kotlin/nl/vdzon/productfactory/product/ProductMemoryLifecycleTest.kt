package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.product.api.MemoryMutation
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
class ProductMemoryLifecycleTest(
    @Autowired private val products: ProductCatalog,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val mvc: MockMvc,
) {
    private val slug = "memory-lifecycle-test"

    @BeforeEach
    fun prepareProduct() {
        runCatching {
            products.create(
                CreateProductRequest(
                    slug = slug,
                    name = "Memory lifecycle test",
                    mission = "Test append-only actief geheugen",
                    status = "active",
                    developmentMode = "manual",
                ).configuration(),
            )
        }
        jdbc.update("delete from product_memory_retraction where product_slug = ?", slug)
        jdbc.queryForList(
            "select id from product_memory where product_slug = ? order by id desc",
            Long::class.java,
            slug,
        ).forEach { jdbc.update("delete from product_memory where id = ?", it) }
    }

    @Test
    fun `replaced and retracted memory is completely absent from active reads while history remains`() {
        val original = products.addRecord(slug, "memory", "Database", "Gebruik PostgreSQL-instructies.")

        val replacement = products.applyMemoryMutations(
            listOf(
                MemoryMutation(
                    action = "REPLACE",
                    productSlug = slug,
                    targetMemoryId = original.id,
                    title = "Database",
                    content = "Gebruik uitsluitend MongoDB-instructies.",
                    reason = "De architectuur is gemigreerd.",
                ),
            ),
            actor = "meeting:test",
        ).single()

        val activeAfterReplacement = products.listRecords(slug, "memory")
        assertEquals(listOf("Gebruik uitsluitend MongoDB-instructies."), activeAfterReplacement.map { it.content })
        assertEquals(original.id, activeAfterReplacement.single().supersedesId)
        assertEquals("meeting:test", activeAfterReplacement.single().createdBy)
        assertEquals(2, jdbc.queryForObject("select count(*) from product_memory where product_slug = ?", Int::class.java, slug))

        products.applyMemoryMutations(
            listOf(
                MemoryMutation(
                    action = "RETRACT",
                    productSlug = slug,
                    targetMemoryId = replacement.memoryId,
                    title = null,
                    content = null,
                    reason = "Ook deze keuze is niet meer van toepassing.",
                ),
            ),
            actor = "meeting:test",
        )

        assertEquals(emptyList(), products.listRecords(slug, "memory"))
        assertEquals(2, jdbc.queryForObject("select count(*) from product_memory where product_slug = ?", Int::class.java, slug))
        assertEquals(1, jdbc.queryForObject("select count(*) from product_memory_retraction where product_slug = ?", Int::class.java, slug))
    }

    @Test
    fun `memory can be reconstructed at a date while the complete immutable timeline remains visible`() {
        val original = products.addRecord(slug, "memory", "Database", "Gebruik PostgreSQL-instructies.")
        jdbc.update("update product_memory set created_at = ? where id = ?", Instant.parse("2026-03-01T10:00:00Z"), original.id)

        val replacement = products.applyMemoryMutations(
            listOf(
                MemoryMutation(
                    action = "REPLACE",
                    productSlug = slug,
                    targetMemoryId = original.id,
                    title = "Database",
                    content = "Gebruik uitsluitend MongoDB-instructies.",
                    reason = "De architectuur is gemigreerd.",
                ),
            ),
            actor = "meeting:migration",
        ).single()
        jdbc.update("update product_memory set created_at = ? where id = ?", Instant.parse("2026-05-01T09:00:00Z"), replacement.memoryId)

        products.applyMemoryMutations(
            listOf(
                MemoryMutation(
                    action = "RETRACT",
                    productSlug = slug,
                    targetMemoryId = replacement.memoryId,
                    title = null,
                    content = null,
                    reason = "De databasekeuze wordt opnieuw onderzocht.",
                ),
            ),
            actor = "meeting:reconsideration",
        )
        jdbc.update(
            "update product_memory_retraction set created_at = ? where memory_id = ?",
            Instant.parse("2026-07-01T08:00:00Z"),
            replacement.memoryId,
        )

        assertEquals(
            listOf("Gebruik PostgreSQL-instructies."),
            products.memoryAt(slug, "2026-04-01").map { it.content },
        )
        assertEquals(
            listOf("Gebruik uitsluitend MongoDB-instructies."),
            products.memoryAt(slug, "2026-06-01T12:00:00Z").map { it.content },
        )
        assertEquals(emptyList(), products.memoryAt(slug, "2026-08-01"))

        val history = products.memoryHistory(slug)
        assertEquals(listOf(replacement.memoryId, original.id), history.map { it.id })
        assertEquals(listOf("RETRACTED", "SUPERSEDED"), history.map { it.status })
        assertEquals(listOf(2, 1), history.map { it.versionNumber })
        assertEquals(listOf(original.id, original.id), history.map { it.rootMemoryId })
        assertEquals(Instant.parse("2026-07-01T08:00:00Z"), history[0].effectiveUntil)
        assertEquals("De databasekeuze wordt opnieuw onderzocht.", history[0].retirementReason)
        assertEquals("meeting:reconsideration", history[0].retiredBy)
        assertEquals(Instant.parse("2026-05-01T09:00:00Z"), history[1].effectiveUntil)
        assertEquals("De architectuur is gemigreerd.", history[1].retirementReason)
        assertEquals("meeting:migration", history[1].retiredBy)

        mvc.get("/api/products/$slug/memory") {
            param("asOf", "2026-04-01")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].content") { value("Gebruik PostgreSQL-instructies.") }
            jsonPath("$[1]") { doesNotExist() }
        }
        mvc.get("/api/products/$slug/memory/history").andExpect {
            status { isOk() }
            jsonPath("$[0].status") { value("RETRACTED") }
            jsonPath("$[0].versionNumber") { value(2) }
            jsonPath("$[1].status") { value("SUPERSEDED") }
        }
        mvc.get("/api/products/$slug/memory") {
            param("asOf", "geen-datum")
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
