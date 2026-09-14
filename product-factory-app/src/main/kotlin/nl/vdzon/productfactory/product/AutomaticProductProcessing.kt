package nl.vdzon.productfactory.product

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PreDestroy
import nl.vdzon.productfactory.api.design.ProductDesignService
import nl.vdzon.productfactory.api.dispatcher.SoftwareFactoryDispatcherService
import nl.vdzon.productfactory.api.planning.ProductPlanningService
import nl.vdzon.productfactory.api.quality.QualityService
import nl.vdzon.productfactory.api.shared.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/** Fixed-size state, a durable per-product lease and work probes: idle ticks never become sessions. */
@Service
class AutomaticProductProcessing(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val design: ProductDesignService,
    private val planning: ProductPlanningService,
    private val quality: QualityService,
    private val dispatcher: SoftwareFactoryDispatcherService,
    @Value("\${PF_SCHEDULES_ENABLED:false}") private val enabled: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val executor = Executors.newFixedThreadPool(4)
    @PreDestroy fun close() { executor.shutdownNow() }

    @Scheduled(fixedRate = 10_000)
    fun poll() {
        if (!enabled) return
        val now = clock.instant()
        val products = jdbc.query("SELECT product_id FROM pf_product WHERE status='ACTIVE' AND dispatching_enabled=TRUE ORDER BY product_id", { rs, _ -> rs.getString(1) })
        products.forEach { id ->
            try {
                jdbc.update("INSERT INTO pf_automation_state(product_id,next_check_at) SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM pf_automation_state WHERE product_id=?)", id, now, id)
            } catch (_: DuplicateKeyException) { /* Already initialized; no history record is added. */ }
            val owner = UUID.randomUUID().toString()
            if (claim(id, owner, now)) executor.submit { runClaimed(id, owner) }
        }
    }

    internal fun claim(id: String, owner: String, now: Instant): Boolean = jdbc.update(
        """UPDATE pf_automation_state SET lease_owner=?,lease_until=?,next_check_at=? WHERE product_id=? AND next_check_at<=?
           AND (lease_until IS NULL OR lease_until<=?) AND EXISTS (SELECT 1 FROM pf_product p WHERE p.product_id=? AND p.status='ACTIVE' AND p.dispatching_enabled=TRUE)""",
        owner, now.plusSeconds(300), Instant.ofEpochMilli((now.toEpochMilli() / 10_000 + 1) * 10_000), id, now, now, id,
    ) == 1

    internal fun runClaimed(id: String, owner: String) {
        try {
            ScheduledProcess.entries.forEach { process ->
                if (!active(id)) return@forEach
                check(id, process)
            }
        } finally {
            val now = clock.instant()
            jdbc.update("UPDATE pf_automation_state SET checked_at=?,lease_owner=NULL,lease_until=NULL WHERE product_id=? AND lease_owner=?", now, id, owner)
        }
    }

    private fun active(id: String) = jdbc.queryForObject("SELECT COUNT(*) FROM pf_product WHERE product_id=? AND status='ACTIVE' AND dispatching_enabled=TRUE", Long::class.java, id) == 1L

    internal fun check(id: String, process: ScheduledProcess) {
        if (!active(id)) return
        val now = clock.instant()
        val state = jdbc.query("SELECT input_key,failure_count,retry_after,error_code FROM pf_automation_process WHERE product_id=? AND process=?", { rs, _ -> State(rs.getString(1),rs.getInt(2),rs.getTimestamp(3)?.toInstant(),rs.getString(4)) }, id, process.name).singleOrNull() ?: State(null,0,null,null)
        if (state.retryAfter?.isAfter(now) == true) return
        var key: String? = null
        try {
            if (process != ScheduledProcess.SOFTWARE_FACTORY_DISPATCHER) {
                key = workKey(id, process) ?: return
                if (key == state.key && state.failures == 0) return
            }
            when (process) {
                ScheduledProcess.PRODUCT_DESIGN -> design.runProcessSession(ProductId(id))
                ScheduledProcess.PRODUCT_PLANNING -> planning.runProcessSession(ProductId(id))
                ScheduledProcess.QUALITY_ASSURANCE -> quality.runProcessSession(ProductId(id))
                ScheduledProcess.SOFTWARE_FACTORY_DISPATCHER -> dispatcher.checkAutomatically(ProductId(id))
            }
            // Process services project failures into their session instead of throwing them.
            if (process != ScheduledProcess.SOFTWARE_FACTORY_DISPATCHER) {
                val failure = jdbc.query("SELECT error_code FROM ${table(process)} WHERE product_id=? AND status IN ('FAILED','BLOCKED') AND updated_at>=? ORDER BY updated_at DESC LIMIT 1", { rs, _ -> rs.getString(1) ?: "PROCESS_BLOCKED" }, id, now).firstOrNull()
                if (failure != null) throw AutomaticFailure(failure)
            }
            save(id, process, key, 0, null, null)
        } catch (_: ProcessAlreadyRunning) {
            // A human action or another coordinator owns this work. It will be checked again.
        } catch (failure: AutomaticFailure) {
            // A domain decision needs new input, not another AI attempt on every retry tick.
            save(id, process, key, 0, null, failure.code)
            if (state.error != failure.code) log.warn("automatic_processing_blocked productId={} process={} code={}", id, process, failure.code)
        } catch (failure: Exception) {
            val code = failure.javaClass.simpleName.take(160)
            val count = (state.failures + 1).coerceAtMost(20)
            save(id, process, key ?: state.key, count, now.plusSeconds((10L shl count.coerceAtMost(6)).coerceAtMost(600)), code)
            if (state.error != code) log.warn("automatic_processing_failed productId={} process={} failureType={}", id, process, code)
        }
    }

    /** Only local metadata is inspected. Git, AI and remote factory calls happen after a work decision. */
    private fun workKey(id: String, process: ScheduledProcess): String? {
        val sessionTable = table(process)
        val open = jdbc.queryForList("SELECT id,status,current_ai_task_id,call_claimed_until FROM $sessionTable WHERE active_product_id=?", id).firstOrNull()
        if (open != null) {
            val session = open["id"].toString()
            if (process != ScheduledProcess.QUALITY_ASSURANCE) {
                if (open["status"] == "BLOCKED") {
                    val answers = jdbc.queryForList("SELECT question_id,version FROM pf_stakeholder_question WHERE process_session_id=? AND status='ANSWERED' ORDER BY question_id", session)
                    return if (answers.isEmpty()) null else hash(listOf(session, answers))
                }
                val lease = (open["call_claimed_until"] as? java.sql.Timestamp)?.toInstant()
                if (open["status"] == "RUNNING" && open["current_ai_task_id"] == null && (lease == null || !lease.isAfter(clock.instant()))) return hash(session)
                // Design and planning have durable AI-result reconcilers with bounded retries.
                return null
            }
            val tasks = jdbc.queryForList("SELECT id,status FROM pf_ai_task WHERE requester_session_id=? ORDER BY id", session)
            if (tasks.isEmpty() || tasks.any { it["status"] !in listOf("SUCCEEDED","FAILED","CANCELLED") }) return null
            return hash(tasks)
        }
        val work: List<Map<String, Any?>> = when (process) {
            ScheduledProcess.PRODUCT_DESIGN -> {
                val signals = jdbc.queryForList("SELECT signal_id,version FROM pf_user_signal WHERE product_id=? AND status IN ('OPEN','IN_REVIEW') ORDER BY signal_id", id)
                val refinement = jdbc.queryForList("SELECT e.id,e.current_version FROM pf_epic e JOIN pf_epic_version v ON v.epic_id=e.id AND v.version=e.current_version WHERE e.product_id=? AND v.status='NEEDS_REFINEMENT' ORDER BY e.id", id)
                val directed = jdbc.queryForList("SELECT work_item_id,request_version FROM pf_design_work_item WHERE product_id=? AND status='PENDING' ORDER BY work_item_id", id)
                // Human products need an explicit input. AI-owned products may also react to changed product goals.
                val policy = jdbc.query("SELECT policy_json FROM pf_product_governance_policy WHERE product_id=?", { rs, _ -> rs.getString(1) }, id).singleOrNull()
                val autonomous = policy?.let { mapper.readTree(it).path("productOwnerMode").asText() == "AI" } ?: false
                if (signals.isEmpty() && refinement.isEmpty() && directed.isEmpty() && !autonomous) return null
                val assignment = jdbc.queryForList("SELECT version FROM pf_product_assignment WHERE product_id=? ORDER BY version DESC LIMIT 1", id)
                if (assignment.isEmpty()) return null
                signals + refinement + directed + assignment +
                    if (autonomous) jdbc.queryForList("SELECT id,current_version FROM pf_story WHERE product_id=? AND status='DONE' ORDER BY id", id) else emptyList()
            }
            ScheduledProcess.PRODUCT_PLANNING -> jdbc.queryForList("SELECT id,current_version FROM pf_epic WHERE product_id=? AND status IN ('AVAILABLE','IN_PLANNING') ORDER BY id", id) +
                jdbc.queryForList("SELECT id,version FROM pf_planning_work_item WHERE product_id=? AND status='PENDING' ORDER BY id", id)
            ScheduledProcess.QUALITY_ASSURANCE -> jdbc.queryForList("SELECT id,version,attempt_count FROM pf_quality_work_item WHERE product_id=? AND (status='PENDING' OR (status IN ('BLOCKED','FAILED') AND retryable=TRUE AND retry_after<=?)) ORDER BY id", id, clock.instant())
            else -> emptyList()
        }
        return if (work.isEmpty()) null else hash(work)
    }

    private fun table(process: ScheduledProcess) = when (process) {
        ScheduledProcess.PRODUCT_DESIGN -> "pf_design_process_session"
        ScheduledProcess.PRODUCT_PLANNING -> "pf_planning_process_session"
        ScheduledProcess.QUALITY_ASSURANCE -> "pf_quality_process_session"
        else -> error("Dispatcher has its own probe")
    }
    private fun hash(value: Any): String = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)))
    private fun save(id: String, process: ScheduledProcess, key: String?, failures: Int, retry: Instant?, error: String?) {
        val now = clock.instant()
        if (jdbc.update("UPDATE pf_automation_process SET input_key=?,failure_count=?,retry_after=?,error_code=?,updated_at=? WHERE product_id=? AND process=?", key,failures,retry,error,now,id,process.name) == 0) {
            jdbc.update("INSERT INTO pf_automation_process(product_id,process,input_key,failure_count,retry_after,error_code,updated_at) VALUES (?,?,?,?,?,?,?)", id,process.name,key,failures,retry,error,now)
        }
    }
    private data class State(val key: String?,val failures: Int,val retryAfter: Instant?,val error: String?)
    private class AutomaticFailure(val code: String): RuntimeException(code)
}
