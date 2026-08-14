package nl.vdzon.productfactory.preview

import nl.vdzon.productfactory.autonomy.AutonomousDeliveryRepository
import nl.vdzon.productfactory.iteration.ShadowIterationRepository
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class AcceptanceDataSeederTest(
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val products: ProductCatalog,
    @Autowired private val iterations: ShadowIterationRepository,
    @Autowired private val deliveries: AutonomousDeliveryRepository,
    @Autowired private val transactions: TransactionTemplate,
) {
    private val acceptanceConfig = PreviewRuntimeConfig(
        enabled = true,
        marker = PreviewRuntimeConfig.ACCEPTANCE_MARKER,
        databaseUrl = "jdbc:postgresql://postgres:5432/productfactory",
        previewPrNumber = "",
    )
    private val seeder by lazy { AcceptanceDataSeeder(jdbc, acceptanceConfig) }
    @BeforeEach
    fun prepareDatabase() {
        cleanupFixture()
    }

    @AfterEach
    fun cleanUp() {
        cleanupFixture()
    }

    @Test
    fun `catalogus wordt exact en idempotent opgeslagen zonder neveneffecten`() {
        val unrelatedBefore = unrelatedCounts()

        assertTrue(ensureInTransaction().applied)
        assertEquals(AcceptanceFixtureCatalog.fixture(), seeder.readStoredFixture())
        assertEquals("paused", products.requireProduct("product-factory").status)
        assertFalse(ensureInTransaction().applied)
        assertEquals(AcceptanceFixtureCatalog.fixture(), seeder.readStoredFixture())
        jdbc.update(
            "update product_definition set target_repository_name = 'robbertvdzon/product-factory', updated_at = current_timestamp where slug = 'product-factory'",
        )
        assertFalse(ensureInTransaction().applied)
        assertEquals(AcceptanceFixtureCatalog.fixture(), seeder.readStoredFixture())

        val storedIterations = iterations.list("product-factory").filter { it.id.startsWith("acceptance-pf-") }
        assertEquals(4, storedIterations.size)
        assertEquals("RUNNING", storedIterations.single { it.id == "acceptance-pf-running-v1" }.status)
        assertNull(storedIterations.single { it.id == "acceptance-pf-running-v1" }.decision)
        val cancelled = storedIterations.single { it.id == "acceptance-pf-failed-v1" }
        assertEquals("HUMAN", cancelled.decision?.actorType)
        assertEquals("MANUAL_CANCELLATION", cancelled.decision?.mechanism)
        assertEquals("MANUALLY_CANCELLED", cancelled.decision?.reasonCode)
        assertEquals("ACCEPT", storedIterations.single { it.id == "acceptance-pf-accepted-v1" }.criticVerdict)
        assertNull(storedIterations.single { it.id == "acceptance-pf-accepted-v1" }.decision)
        assertEquals("ACCEPT", storedIterations.single { it.id == "acceptance-pf-rejected-v1" }.criticVerdict)
        assertNull(storedIterations.single { it.id == "acceptance-pf-rejected-v1" }.decision)

        val storedDeliveries = deliveries.list("product-factory").filter { it.id in setOf(-920002L, -920001L) }
        assertEquals(listOf("SYNTH-PF-101", "SYNTH-PF-102"), storedDeliveries.mapNotNull { it.externalStoryKey }.sorted())
        assertTrue(storedDeliveries.all { it.status == "DONE" && it.remotePhase == "developed" })
        assertTrue(deliveries.toReconcile("product-factory").none { it.id in setOf(-920002L, -920001L) })
        assertTrue(deliveries.eligible("product-factory").isEmpty())
        assertEquals(0L, count("agent_run", "run_id like 'acceptance-pf-%'"))
        assertEquals(0L, count("workspace_publication", "run_id like 'acceptance-pf-%'"))
        assertEquals(unrelatedBefore, unrelatedCounts())
    }

    @Test
    fun `afwijkende gereserveerde identifier faalt zonder overschrijven of gedeeltelijke dataset`() {
        assertTrue(ensureInTransaction().applied)
        jdbc.update("delete from preview_seed_history where seed_key = ?", AcceptanceFixtureCatalog.SEED_KEY)
        jdbc.update("delete from story_delivery where iteration_id like 'acceptance-pf-%'")
        jdbc.update("delete from story_candidate where iteration_id like 'acceptance-pf-%'")
        jdbc.update("delete from shadow_iteration_decision where iteration_id like 'acceptance-pf-%'")
        jdbc.update("delete from shadow_iteration where id <> 'acceptance-pf-accepted-v1' and id like 'acceptance-pf-%'")
        jdbc.update("update shadow_iteration set focus = 'Bestaande afwijkende inhoud' where id = 'acceptance-pf-accepted-v1'")

        val failure = assertFailsWith<IllegalStateException> { ensureInTransaction() }
        assertTrue(failure.message.orEmpty().contains("Gereserveerde acceptatiefixture wijkt af"))
        assertEquals(
            "Bestaande afwijkende inhoud",
            jdbc.queryForObject(
                "select focus from shadow_iteration where id = 'acceptance-pf-accepted-v1'",
                String::class.java,
            ),
        )
        assertEquals(1L, count("shadow_iteration", "id like 'acceptance-pf-%'"))
        assertEquals(0L, count("story_candidate", "id in (-920002, -920001)"))
        assertEquals(0L, count("story_delivery", "id in (-920002, -920001)"))
        assertEquals(0L, count("preview_seed_history", "seed_key = '${AcceptanceFixtureCatalog.SEED_KEY}'"))
    }

    private fun ensureInTransaction(): AcceptanceSeedResult =
        transactions.execute { seeder.ensure() } ?: error("Transactie gaf geen seedresultaat")

    private fun unrelatedCounts(): Map<String, Long> = linkedMapOf(
        "iterations" to count("shadow_iteration", "product_slug = 'hkh-autopilot'"),
        "candidates" to count("story_candidate", "product_slug = 'hkh-autopilot'"),
        "deliveries" to count("story_delivery", "product_slug = 'hkh-autopilot'"),
    )

    private fun count(table: String, condition: String): Long =
        jdbc.queryForObject("select count(*) from $table where $condition", Long::class.java) ?: 0

    private fun cleanupFixture() {
        jdbc.update("delete from preview_seed_history where seed_key = ?", AcceptanceFixtureCatalog.SEED_KEY)
        jdbc.update("delete from story_delivery where id in (-920002, -920001) or iteration_id like 'acceptance-pf-%'")
        jdbc.update("delete from story_candidate where id in (-920002, -920001) or iteration_id like 'acceptance-pf-%'")
        jdbc.update("delete from shadow_iteration_decision where iteration_id like 'acceptance-pf-%'")
        jdbc.update("delete from shadow_iteration where id like 'acceptance-pf-%'")
        jdbc.update("delete from product_iteration_time where product_slug = 'product-factory'")
        jdbc.update("delete from product_roadmap_schedule where product_slug = 'product-factory'")
        jdbc.update("delete from product_allowed_write_path where product_slug = 'product-factory'")
        jdbc.update("delete from product_definition where slug = 'product-factory' or id = 'acceptance-product-product-factory-v1'")
    }
}
