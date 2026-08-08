package nl.vdzon.productfactory.preview

import nl.vdzon.productfactory.iteration.ShadowIterationRepository
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
class PreviewDataSeederTest(
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val products: ProductCatalog,
    @Autowired private val iterations: ShadowIterationRepository,
) {
    private val previewConfig = PreviewRuntimeConfig(
        enabled = true,
        marker = PreviewRuntimeConfig.REQUIRED_MARKER,
        databaseUrl = "jdbc:postgresql://postgres:5432/productfactory",
        previewPrNumber = "7",
    )
    private val seeder = PreviewDataSeeder(jdbcTemplate, products, iterations, previewConfig)

    @Test
    fun `seeding is idempotent and populates representative data`() {
        val first = seeder.ensure()
        assertEquals(setOf("shadow-iterations-v1", "product-records-v1"), first.applied.toSet())
        assertTrue(first.alreadyPresent.isEmpty())

        val second = seeder.ensure()
        assertTrue(second.applied.isEmpty())
        assertEquals(setOf("shadow-iterations-v1", "product-records-v1"), second.alreadyPresent.toSet())

        val iterationCount = jdbcTemplate.queryForObject(
            "select count(*) from shadow_iteration where product_slug = 'hkh-autopilot'",
            Long::class.java,
        )
        assertTrue((iterationCount ?: 0) >= 3)

        val candidateCount = jdbcTemplate.queryForObject(
            "select count(*) from story_candidate where product_slug = 'hkh-autopilot'",
            Long::class.java,
        )
        assertTrue((candidateCount ?: 0) >= 3)

        val acceptedIteration = iterations.list("hkh-autopilot").single { it.focus.startsWith("Onderzoek welke bronervaring") }
        assertEquals(5, iterations.artifacts("hkh-autopilot", acceptedIteration.id).size)
    }
}
