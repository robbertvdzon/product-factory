package nl.vdzon.productfactory.planning

import nl.vdzon.productfactory.api.planning.ProductPlanningService
import nl.vdzon.productfactory.api.shared.ProcessAlreadyRunning
import nl.vdzon.productfactory.api.shared.ProductId
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger

/** Starts planning from an approval event; product schedules are only periodic catch-up runs. */
@Component
class ApprovedEpicPlanningCoordinator(
    private val jdbc: JdbcTemplate,
    private val planning: ProductPlanningService,
    private val clock: Clock,
    @Value("\${PF_AI_RUNTIME_SCHEDULING_ENABLED:false}") private val enabled: Boolean,
) {
    private val log = Logger.getLogger(javaClass.name)

    @Scheduled(fixedDelayString = "\${PF_APPROVED_EPIC_RECONCILE_DELAY_MS:2000}")
    fun startApprovedEpics() {
        if (!enabled) return
        val now = clock.instant()
        val products = jdbc.query(
            """SELECT DISTINCT product_id FROM pf_approved_epic_planning_trigger
                WHERE status='PENDING' AND next_attempt_at<=? AND product_id IN (SELECT product_id FROM pf_product WHERE status='ACTIVE' AND dispatching_enabled=TRUE) ORDER BY product_id LIMIT 20""".trimIndent(),
            { rs, _ -> ProductId(rs.getString(1)) }, now,
        )
        products.forEach { productId ->
            try {
                planning.runProcessSession(productId)
                markStarted(productId)
            } catch (_: ProcessAlreadyRunning) {
                retryLater(productId, now, Duration.ofSeconds(5), countAttempt = false)
            } catch (failure: RuntimeException) {
                retryLater(productId, now, RETRY_DELAY, countAttempt = true)
                log.log(Level.WARNING, "approved_epic_planning_start_failed productId=${productId.value} failureType=${failure.javaClass.simpleName}", failure)
            }
        }
    }

    private fun markStarted(productId: ProductId) {
        jdbc.update(
            "UPDATE pf_approved_epic_planning_trigger SET status='STARTED',updated_at=? WHERE product_id=? AND status='PENDING'",
            clock.instant(), productId.value,
        )
    }

    private fun retryLater(productId: ProductId, now: java.time.Instant, delay: Duration, countAttempt: Boolean) {
        jdbc.update(
            """UPDATE pf_approved_epic_planning_trigger
                SET attempt_count=attempt_count+?,next_attempt_at=?,updated_at=?
                WHERE product_id=? AND status='PENDING'""".trimIndent(),
            if (countAttempt) 1 else 0, now.plus(delay), now, productId.value,
        )
    }

    private companion object { val RETRY_DELAY: Duration = Duration.ofMinutes(1) }
}
