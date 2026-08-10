package nl.vdzon.productfactory.iteration

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val REASON = "Cyclus afgebroken: het proces is herstart"

/**
 * Dekt [ShadowIterationRepository.failOrphaned], de opruimlogica achter [OrphanedIterationReconciler].
 * Test rechtstreeks op de repository (zonder een echte procesherstart te simuleren) omdat de
 * reconciler zelf maar één regel bevat: aanroepen en loggen.
 */
@SpringBootTest
class OrphanedIterationReconcilerTest(
    @Autowired private val repository: ShadowIterationRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @Test
    fun `fails a RESEARCHER step once past its own (long) timeout`() {
        val iteration = repository.create("hkh-autopilot", "Weeskind-onderzoek door herstart")
        repository.markRunning(iteration.id)
        repository.startStep(iteration.id, "hkh-autopilot", "RESEARCHER", 1, "${iteration.id}-researcher-1")
        backdateStep(iteration.id, "RESEARCHER", 1, ShadowIterationEngine.RESEARCHER_TIMEOUT_SECONDS)

        val failed = repository.failOrphaned(REASON)

        assertEquals(listOf(iteration.id), failed)
        val reloaded = repository.require("hkh-autopilot", iteration.id)
        assertEquals("FAILED", reloaded.status)
        assertEquals(REASON, reloaded.errorMessage)
        val step = repository.steps("hkh-autopilot", iteration.id).single()
        assertEquals("FAILED", step.status)
    }

    @Test
    fun `leaves a RESEARCHER step within its own timeout untouched`() {
        val iteration = repository.create("hkh-autopilot", "Onderzoek nog legitiem bezig")
        repository.markRunning(iteration.id)
        repository.startStep(iteration.id, "hkh-autopilot", "RESEARCHER", 1, "${iteration.id}-researcher-1")
        // Ruim binnen de RESEARCHER-timeout, maar voorbij de kortere timeout van andere rollen — bevestigt
        // dat de per-rol-drempel echt per rol wordt toegepast, niet als één vaste waarde voor iedereen.
        backdateStep(iteration.id, "RESEARCHER", 1, ShadowIterationEngine.ROLE_TIMEOUT_SECONDS + 120)

        try {
            val failed = repository.failOrphaned(REASON)

            assertTrue(iteration.id !in failed)
            assertEquals("RUNNING", repository.require("hkh-autopilot", iteration.id).status)
        } finally {
            repository.markFailed(iteration.id, "test cleanup")
        }
    }

    @Test
    fun `fails a non-RESEARCHER step once past the shorter role timeout`() {
        val iteration = repository.create("hkh-autopilot", "Weeskind-productbesluit door herstart")
        repository.markRunning(iteration.id)
        repository.startStep(iteration.id, "hkh-autopilot", "PRODUCT_OWNER", 1, "${iteration.id}-product_owner-1")
        backdateStep(iteration.id, "PRODUCT_OWNER", 1, ShadowIterationEngine.ROLE_TIMEOUT_SECONDS)

        val failed = repository.failOrphaned(REASON)

        assertEquals(listOf(iteration.id), failed)
        assertEquals("FAILED", repository.require("hkh-autopilot", iteration.id).status)
    }

    @Test
    fun `fails a QUEUED iteration that never got a step started`() {
        val iteration = repository.create("hkh-autopilot", "Wachtrij-weeskind, listener nooit afgevuurd")
        backdateIterationCreatedAt(iteration.id, Duration.ofMinutes(10))

        val failed = repository.failOrphaned(REASON)

        assertEquals(listOf(iteration.id), failed)
        assertEquals("FAILED", repository.require("hkh-autopilot", iteration.id).status)
    }

    @Test
    fun `leaves a freshly QUEUED iteration untouched`() {
        val iteration = repository.create("hkh-autopilot", "Nog in de wachtrij")
        try {
            val failed = repository.failOrphaned(REASON)

            assertTrue(iteration.id !in failed)
            assertEquals("QUEUED", repository.require("hkh-autopilot", iteration.id).status)
        } finally {
            repository.markFailed(iteration.id, "test cleanup")
        }
    }

    private fun backdateStep(iterationId: String, role: String, attempt: Int, olderThanSeconds: Long) {
        val stamp = Timestamp.from(Instant.now().minusSeconds(olderThanSeconds).minusSeconds(60))
        jdbc.update(
            "update shadow_iteration_step set started_at = ? where iteration_id = ? and role = ? and attempt = ?",
            stamp,
            iterationId,
            role,
            attempt,
        )
    }

    private fun backdateIterationCreatedAt(iterationId: String, olderThan: Duration) {
        val stamp = Timestamp.from(Instant.now().minus(olderThan).minusSeconds(60))
        jdbc.update("update shadow_iteration set created_at = ? where id = ?", stamp, iterationId)
    }
}
