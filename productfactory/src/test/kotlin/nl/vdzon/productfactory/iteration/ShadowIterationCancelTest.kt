package nl.vdzon.productfactory.iteration

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
class ShadowIterationCancelTest(
    @Autowired private val service: ShadowIterationService,
    @Autowired private val repository: ShadowIterationRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @BeforeEach
    fun ensureIsolatedProduct() {
        val exists = jdbc.queryForObject(
            "select count(*) from product_definition where slug = ?",
            Long::class.java,
            PRODUCT_SLUG,
        ) ?: 0
        if (exists == 0L) {
            jdbc.update(
                "insert into product_definition(id, slug, name, mission, guardrails) values (?, ?, ?, ?, ?)",
                "product-$PRODUCT_SLUG",
                PRODUCT_SLUG,
                "Shadow iteration cancel test",
                "Test cancellation in isolation",
                "Test only",
            )
        }
    }

    @Test
    fun `cancel marks a running iteration as failed and frees the product for a new cycle`() {
        val iteration = repository.create(PRODUCT_SLUG, "Vastgelopen onderzoek")
        repository.markRunning(iteration.id)
        assertEquals(true, repository.hasActive(PRODUCT_SLUG))

        val cancelled = service.cancel(PRODUCT_SLUG, iteration.id, "Handmatig gestopt in test")

        assertEquals("FAILED", cancelled.status)
        assertEquals("Handmatig gestopt in test", cancelled.errorMessage)
        assertEquals(false, repository.hasActive(PRODUCT_SLUG))
    }

    @Test
    fun `cancel rejects an iteration that already finished`() {
        val iteration = repository.create(PRODUCT_SLUG, "Al afgerond onderzoek")
        repository.markRunning(iteration.id)
        repository.markFailed(iteration.id, "Al eerder mislukt")

        assertFailsWith<ResponseStatusException> {
            service.cancel(PRODUCT_SLUG, iteration.id, null)
        }
    }

    // Simuleert dat de achtergrondthread nog bezig is met de agentworker op het moment van annuleren:
    // die thread weet niets van de annulering en probeert later alsnog af te ronden. De write-once-guard
    // op de terminale status (zie ShadowIterationRepository.markAccepted) moet die late poging negeren.
    @Test
    fun `a late completion from the original run after cancel is ignored`() {
        val iteration = repository.create(PRODUCT_SLUG, "Race met achtergrondthread")
        repository.markRunning(iteration.id)
        service.cancel(PRODUCT_SLUG, iteration.id, null)

        repository.markAccepted(iteration.id, "ACCEPT", "late-run-id", null, null)

        assertEquals("FAILED", repository.require(PRODUCT_SLUG, iteration.id).status)
    }

    private companion object {
        const val PRODUCT_SLUG = "shadow-iteration-cancel-test"
    }
}
