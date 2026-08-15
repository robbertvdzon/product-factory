package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.product.api.MemoryMutation
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals

@SpringBootTest
class ProductMemoryLifecycleTest(
    @Autowired private val products: ProductCatalog,
    @Autowired private val jdbc: JdbcTemplate,
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
        jdbc.update("delete from product_memory where product_slug = ?", slug)
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
}
