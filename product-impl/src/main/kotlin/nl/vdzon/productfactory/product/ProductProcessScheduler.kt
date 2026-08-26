package nl.vdzon.productfactory.product

import com.fasterxml.jackson.databind.ObjectMapper
import io.micrometer.core.instrument.MeterRegistry
import nl.vdzon.productfactory.api.design.ProductDesignService
import nl.vdzon.productfactory.api.dispatcher.SoftwareFactoryDispatcherService
import nl.vdzon.productfactory.api.planning.ProductPlanningService
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.quality.QualityService
import nl.vdzon.productfactory.api.shared.ProcessAlreadyRunning
import nl.vdzon.productfactory.api.shared.ProductId
import nl.vdzon.productfactory.api.shared.ScheduledProcess
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.slf4j.LoggerFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.*
import java.util.UUID

@Component
class ProductProcessScheduler(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val design: ProductDesignService,
    private val planning: ProductPlanningService,
    private val quality: QualityService,
    private val dispatcher: SoftwareFactoryDispatcherService,
    private val meterRegistry: ObjectProvider<MeterRegistry>,
    transactionManager: PlatformTransactionManager,
    @Value("\${PF_SCHEDULES_ENABLED:false}") private val enabled: Boolean,
) : ProductScheduleRunner {
    private val transactions = TransactionTemplate(transactionManager)
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${PF_SCHEDULE_POLL_MILLIS:15000}")
    fun poll() {
        if (enabled) runDueSchedules()
    }

    override fun runDueSchedules() {
        while (true) {
            val claim = transactions.execute { claimOne() } ?: return
            val productActive = jdbc.queryForObject("SELECT status FROM pf_product WHERE product_id=?", String::class.java, claim.productId.value) == "ACTIVE"
            if (!productActive) {
                finish(claim, ScheduleRunStatus.SKIPPED, "Product is inactief; geplande start overgeslagen.", null)
                continue
            }
            try {
                when (claim.process) {
                    ScheduledProcess.PRODUCT_DESIGN -> design.runProcessSession(claim.productId)
                    ScheduledProcess.PRODUCT_PLANNING -> planning.runProcessSession(claim.productId)
                    ScheduledProcess.QUALITY_ASSURANCE -> quality.runProcessSession(claim.productId)
                    ScheduledProcess.SOFTWARE_FACTORY_DISPATCHER -> dispatcher.runDispatchSession(claim.productId)
                }
                finish(claim, ScheduleRunStatus.SUCCEEDED, "Publieke procesfunctie uitgevoerd.", null)
            } catch (_: ProcessAlreadyRunning) {
                finish(claim, ScheduleRunStatus.SKIPPED, "Procescall was al actief; geplande start overgeslagen.", "PROCESS_ALREADY_RUNNING")
            } catch (failure: Exception) {
                log.warn(
                    "scheduler_process_failed productId={} process={} runId={} failureType={}",
                    claim.productId.value, claim.process, claim.id, failure.javaClass.simpleName,
                )
                finish(claim, ScheduleRunStatus.FAILED, "Publieke procesfunctie kon niet worden uitgevoerd.", "SCHEDULED_PROCESS_FAILED")
            }
        }
    }

    private fun claimOne(): Claim? {
        val now = clock.instant()
        val due = jdbc.query(
            """SELECT product_id,process,next_run_at,timezone,pattern_json FROM pf_process_schedule
                WHERE enabled=TRUE AND next_run_at IS NOT NULL AND next_run_at<=? ORDER BY next_run_at,product_id,process""".trimIndent(),
            { rs, _ -> DueSchedule(
                ProductId(rs.getString(1)), ScheduledProcess.valueOf(rs.getString(2)), rs.getTimestamp(3).toInstant(),
                ZoneId.of(rs.getString(4)), mapper.readValue(rs.getString(5), SchedulePattern::class.java),
            ) }, now,
        ).firstOrNull() ?: return null
        val next = nextRun(due.pattern, due.zone, now)
        if (jdbc.update(
                """UPDATE pf_process_schedule SET next_run_at=?,last_scheduled_at=?,updated_at=?
                    WHERE product_id=? AND process=? AND enabled=TRUE AND next_run_at=?""".trimIndent(),
                next, due.scheduledFor, now, due.productId.value, due.process.name, due.scheduledFor,
            ) != 1
        ) return null
        val id = UUID.randomUUID().toString()
        jdbc.update(
            "INSERT INTO pf_schedule_run(id,product_id,process,scheduled_for,status,claimed_at) VALUES (?,?,?,?,?,?)",
            id, due.productId.value, due.process.name, due.scheduledFor, ScheduleRunStatus.CLAIMED.name, now,
        )
        log.info(
            "scheduler_claimed productId={} process={} runId={} scheduledFor={}",
            due.productId.value, due.process, id, due.scheduledFor,
        )
        meterRegistry.ifAvailable?.counter("pf.scheduler.runs.claimed", "process", due.process.name)?.increment()
        return Claim(id, due.productId, due.process)
    }

    private fun finish(claim: Claim, status: ScheduleRunStatus, summary: String, code: String?) {
        val now = clock.instant()
        jdbc.update(
            "UPDATE pf_schedule_run SET status=?,result_summary=?,error_code=?,finished_at=? WHERE id=? AND status='CLAIMED'",
            status.name, summary, code, now, claim.id,
        )
        if (status == ScheduleRunStatus.SKIPPED) {
            jdbc.update("UPDATE pf_process_schedule SET last_skipped_at=? WHERE product_id=? AND process=?", now, claim.productId.value, claim.process.name)
        }
        log.info(
            "scheduler_finished productId={} process={} runId={} status={} errorCode={}",
            claim.productId.value, claim.process, claim.id, status, code ?: "NONE",
        )
        meterRegistry.ifAvailable?.counter(
            "pf.scheduler.runs.finished", "process", claim.process.name, "status", status.name,
        )?.increment()
    }

    private fun nextRun(pattern: SchedulePattern, zone: ZoneId, now: Instant): Instant {
        pattern.intervalMinutes?.let { return now.plusSeconds(it * 60) }
        val localNow = now.atZone(zone)
        return (0..8).flatMap { offset ->
            val date = localNow.toLocalDate().plusDays(offset.toLong())
            pattern.weeklyRules.filter { date.dayOfWeek in it.days }.flatMap { rule ->
                rule.times.flatMap { time -> validInstants(LocalDateTime.of(date, time), zone) }
            }
        }.filter { it.isAfter(now) }.minOrNull() ?: error("Geldig weekschema mist volgend tijdstip.")
    }

    private fun validInstants(local: LocalDateTime, zone: ZoneId): List<Instant> {
        val offsets = zone.rules.getValidOffsets(local)
        if (offsets.isNotEmpty()) return offsets.map { local.toInstant(it) }
        val transition = zone.rules.getTransition(local) ?: return emptyList()
        return listOf(transition.dateTimeAfter.atZone(zone).toInstant())
    }

    private data class DueSchedule(val productId: ProductId, val process: ScheduledProcess, val scheduledFor: Instant, val zone: ZoneId, val pattern: SchedulePattern)
    private data class Claim(val id: String, val productId: ProductId, val process: ScheduledProcess)
}
