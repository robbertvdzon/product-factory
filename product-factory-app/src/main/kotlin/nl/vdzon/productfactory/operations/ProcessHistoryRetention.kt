package nl.vdzon.productfactory.operations

import nl.vdzon.productfactory.api.shared.NO_OP_PROCESS_SESSION_SQL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class RetentionResult(
    val designSessions: Int,
    val planningSessions: Int,
    val qualitySessions: Int,
    val dispatcherSessions: Int,
    val scheduleRuns: Int,
)

/**
 * Ruimt operationele ruis op: succesvolle no-op-sessies ouder dan 48 uur en afgeronde schedule-runs ouder dan 7 dagen.
 * Sessies waarnaar nog iets verwijst (formele FK of losse sessiekolom) blijven altijd staan.
 * Verwijderen gebeurt in kleine batches zonder omvattende transactie, zodat er geen lange locks ontstaan.
 */
@Component
class ProcessHistoryRetention(
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
    @Value("\${PF_HISTORY_RETENTION_ENABLED:true}") private val enabled: Boolean,
    @Value("\${PF_HISTORY_RETENTION_BATCH_SIZE:1000}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${PF_HISTORY_RETENTION_MILLIS:3600000}",
        initialDelayString = "\${PF_HISTORY_RETENTION_INITIAL_DELAY_MILLIS:60000}",
    )
    fun scheduledPrune() {
        if (!enabled) return
        try {
            prune(clock.instant())
        } catch (error: Exception) {
            log.warn("history_retention_failed failureType={}", error.javaClass.simpleName, error)
        }
    }

    fun prune(now: Instant): RetentionResult {
        val sessionCutoff = now.minus(NO_OP_SESSION_RETENTION)
        val result = RetentionResult(
            designSessions = pruneNoOpSessions("pf_design_process_session", sessionCutoff, DESIGN_REFERENCES),
            planningSessions = pruneNoOpSessions("pf_planning_process_session", sessionCutoff, PLANNING_REFERENCES),
            qualitySessions = pruneNoOpSessions("pf_quality_process_session", sessionCutoff, QUALITY_REFERENCES),
            dispatcherSessions = pruneNoOpSessions("pf_dispatcher_process_session", sessionCutoff, DISPATCHER_REFERENCES),
            scheduleRuns = deleteInBatches(
                """DELETE FROM pf_schedule_run WHERE id IN (
                    SELECT id FROM pf_schedule_run WHERE claimed_at<? AND status<>'CLAIMED' LIMIT $batchSize)""".trimIndent(),
                now.minus(SCHEDULE_RUN_RETENTION),
            ),
        )
        log.info(
            "history_retention_pruned designSessions={} planningSessions={} qualitySessions={} dispatcherSessions={} scheduleRuns={}",
            result.designSessions, result.planningSessions, result.qualitySessions, result.dispatcherSessions, result.scheduleRuns,
        )
        return result
    }

    private fun pruneNoOpSessions(table: String, cutoff: Instant, references: List<Pair<String, String>>): Int {
        val unreferenced = (COMMON_REFERENCES + references).joinToString("\n") { (refTable, refColumn) ->
            "AND NOT EXISTS (SELECT 1 FROM $refTable r WHERE r.$refColumn=s.id)"
        }
        return deleteInBatches(
            """DELETE FROM $table WHERE id IN (
                SELECT s.id FROM $table s WHERE $NO_OP_PROCESS_SESSION_SQL AND s.started_at<?
                $unreferenced
                LIMIT $batchSize)""".trimIndent(),
            cutoff,
        )
    }

    private fun deleteInBatches(sql: String, cutoff: Instant): Int {
        var total = 0
        do {
            val deleted = jdbc.update(sql, cutoff)
            total += deleted
        } while (deleted >= batchSize)
        return total
    }

    companion object {
        private val NO_OP_SESSION_RETENTION: Duration = Duration.ofHours(48)
        private val SCHEDULE_RUN_RETENTION: Duration = Duration.ofDays(7)

        /** Kolommen die naar een processessie van willekeurig welk proces kunnen wijzen (zonder formele FK). */
        private val COMMON_REFERENCES = listOf(
            "pf_ai_task" to "requester_session_id",
            "pf_stakeholder_question" to "process_session_id",
            "pf_agent_memory_version" to "process_session_id",
            "pf_agent_memory_retraction" to "process_session_id",
            "pf_agent_memory_read_audit" to "process_session_id",
        )
        private val DESIGN_REFERENCES = listOf("pf_design_work_item" to "process_session_id")
        private val PLANNING_REFERENCES = listOf("pf_planning_work_item" to "claimed_by_session_id")
        private val QUALITY_REFERENCES = listOf(
            "pf_quality_work_item" to "claimed_by_session_id",
            "pf_quality_snapshot" to "session_id",
        )
        private val DISPATCHER_REFERENCES = listOf("pf_delivery_attempt" to "last_session_id")
    }
}
