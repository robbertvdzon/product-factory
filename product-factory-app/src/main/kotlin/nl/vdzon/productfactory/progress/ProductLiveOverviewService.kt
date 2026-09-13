package nl.vdzon.productfactory.progress

import nl.vdzon.productfactory.api.design.ProductDesignQueryService
import nl.vdzon.productfactory.api.dispatcher.DispatcherProductStatusDetails
import nl.vdzon.productfactory.api.dispatcher.SoftwareFactoryDispatcherQueryService
import nl.vdzon.productfactory.api.planning.ProductPlanningQueryService
import nl.vdzon.productfactory.api.product.ProductQueryService
import nl.vdzon.productfactory.api.quality.QualityQueryService
import nl.vdzon.productfactory.api.shared.*
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

data class ProductLiveOverview(
    val productId: String,
    val generatedAt: Instant,
    val processes: List<ProcessLiveStatus>,
    val dispatcher: DispatcherProductStatusDetails,
)

data class ProcessLiveStatus(
    val process: ScheduledProcess,
    val enabled: Boolean,
    val intervalMinutes: Long?,
    val nextRunAt: Instant?,
    val running: ProcessSessionDetails?,
    val lastSession: ProcessSessionDetails?,
    val lastMeaningfulSession: ProcessSessionDetails?,
    val last24h: ProcessSessionCounts,
    val hourly: List<HourlySessionBucket>,
)

data class ProcessSessionCounts(val total: Int, val noOps: Int, val failed: Int, val meaningful: Int)
data class HourlySessionBucket(val hourStart: Instant, val total: Int, val noOps: Int, val failed: Int)

/** Samengesteld live-overzicht van de vier procesmodules van één product. */
@Service
class ProductLiveOverviewService(
    private val products: ProductQueryService,
    private val design: ProductDesignQueryService,
    private val planning: ProductPlanningQueryService,
    private val quality: QualityQueryService,
    private val dispatcher: SoftwareFactoryDispatcherQueryService,
    private val clock: Clock,
) {
    fun overview(productId: ProductId): ProductLiveOverview {
        val now = clock.instant()
        val schedules = products.getProcessSchedules(productId).associateBy { it.process }
        val processes = PROCESS_ORDER.map { process ->
            val find = sessionFinder(process)
            val schedule = schedules[process]
            val recent = find(ProcessSessionFilter(productId, timeRange = TimeRange(from = now.minus(Duration.ofHours(24)))))
            ProcessLiveStatus(
                process = process,
                enabled = schedule?.enabled ?: false,
                intervalMinutes = schedule?.pattern?.intervalMinutes,
                nextRunAt = schedule?.nextRunAt,
                running = find(ProcessSessionFilter(productId, ACTIVE_STATUSES, limit = 1)).firstOrNull(),
                lastSession = find(ProcessSessionFilter(productId, limit = 1)).firstOrNull(),
                lastMeaningfulSession = find(ProcessSessionFilter(productId, limit = 1, excludeNoOps = true)).firstOrNull(),
                last24h = counts(recent),
                hourly = hourly(recent, now),
            )
        }
        return ProductLiveOverview(productId.value, now, processes, dispatcher.getDispatchStatus(productId))
    }

    private fun sessionFinder(process: ScheduledProcess): (ProcessSessionFilter) -> List<ProcessSessionDetails> = when (process) {
        ScheduledProcess.PRODUCT_DESIGN -> design::findProcessSessions
        ScheduledProcess.PRODUCT_PLANNING -> planning::findProcessSessions
        ScheduledProcess.QUALITY_ASSURANCE -> quality::findProcessSessions
        ScheduledProcess.SOFTWARE_FACTORY_DISPATCHER -> dispatcher::findDispatchSessions
    }

    private fun counts(sessions: List<ProcessSessionDetails>): ProcessSessionCounts {
        val noOps = sessions.count { it.noOp }
        val failed = sessions.count { it.status in FAILED_STATUSES }
        val active = sessions.count { it.status in ACTIVE_STATUSES }
        return ProcessSessionCounts(sessions.size, noOps, failed, sessions.size - noOps - failed - active)
    }

    private fun hourly(sessions: List<ProcessSessionDetails>, now: Instant): List<HourlySessionBucket> {
        val currentHour = now.truncatedTo(ChronoUnit.HOURS)
        val byHour = sessions.groupBy { it.startedAt.truncatedTo(ChronoUnit.HOURS) }
        return (23 downTo 0).map { offset ->
            val hourStart = currentHour.minus(Duration.ofHours(offset.toLong()))
            val inHour = byHour[hourStart].orEmpty()
            HourlySessionBucket(hourStart, inHour.size, inHour.count { it.noOp }, inHour.count { it.status in FAILED_STATUSES })
        }
    }

    companion object {
        private val PROCESS_ORDER = listOf(
            ScheduledProcess.PRODUCT_DESIGN, ScheduledProcess.PRODUCT_PLANNING,
            ScheduledProcess.QUALITY_ASSURANCE, ScheduledProcess.SOFTWARE_FACTORY_DISPATCHER,
        )
        private val ACTIVE_STATUSES = setOf(ProcessSessionStatus.RUNNING, ProcessSessionStatus.WAITING_FOR_AI)
        private val FAILED_STATUSES = setOf(ProcessSessionStatus.FAILED, ProcessSessionStatus.BLOCKED)
    }
}
