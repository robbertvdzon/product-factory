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

/**
 * Dekt [ShadowIterationRepository.failOrphaned], de opruimlogica achter [OrphanedIterationReconciler].
 * Test rechtstreeks op de repository (zonder een echte procesherstart te simuleren) omdat de
 * reconciler zelf maar één regel bevat: doorgeven van de drempel en loggen.
 */
@SpringBootTest
class OrphanedIterationReconcilerTest(
    @Autowired private val repository: ShadowIterationRepository,
    @Autowired private val jdbc: JdbcTemplate,
) {
    @Test
    fun `fails a RUNNING iteration and its running step once past the threshold`() {
        val iteration = repository.create("hkh-autopilot", "Weeskind door herstart")
        repository.markRunning(iteration.id)
        repository.startStep(iteration.id, "hkh-autopilot", "RESEARCHER", 1, "${iteration.id}-researcher-1")
        backdate(iteration.id, Duration.ofHours(3))

        val failed = repository.failOrphaned("Cyclus afgebroken: het proces is herstart", Duration.ofHours(2))

        assertEquals(listOf(iteration.id), failed)
        val reloaded = repository.require("hkh-autopilot", iteration.id)
        assertEquals("FAILED", reloaded.status)
        assertEquals("Cyclus afgebroken: het proces is herstart", reloaded.errorMessage)
        val step = repository.steps("hkh-autopilot", iteration.id).single()
        assertEquals("FAILED", step.status)
    }

    @Test
    fun `leaves a RUNNING iteration inside the threshold untouched`() {
        val iteration = repository.create("hkh-autopilot", "Nog legitiem bezig")
        repository.markRunning(iteration.id)
        try {
            val failed = repository.failOrphaned("Cyclus afgebroken: het proces is herstart", Duration.ofHours(2))

            assertTrue(iteration.id !in failed)
            assertEquals("RUNNING", repository.require("hkh-autopilot", iteration.id).status)
        } finally {
            repository.markFailed(iteration.id, "test cleanup")
        }
    }

    @Test
    fun `leaves a freshly QUEUED iteration untouched`() {
        val iteration = repository.create("hkh-autopilot", "Nog in de wachtrij")
        try {
            val failed = repository.failOrphaned("Cyclus afgebroken: het proces is herstart", Duration.ofHours(2))

            assertTrue(iteration.id !in failed)
            assertEquals("QUEUED", repository.require("hkh-autopilot", iteration.id).status)
        } finally {
            repository.markFailed(iteration.id, "test cleanup")
        }
    }

    private fun backdate(iterationId: String, olderThan: Duration) {
        val stamp = Timestamp.from(Instant.now().minus(olderThan).minusSeconds(60))
        jdbc.update("update shadow_iteration set started_at = ?, created_at = ? where id = ?", stamp, stamp, iterationId)
    }
}
