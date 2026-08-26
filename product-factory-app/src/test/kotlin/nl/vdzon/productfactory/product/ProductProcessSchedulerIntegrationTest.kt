package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.api.design.ProductDesignService
import nl.vdzon.productfactory.api.dispatcher.SoftwareFactoryDispatcherService
import nl.vdzon.productfactory.api.planning.ProductPlanningService
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.quality.QualityService
import nl.vdzon.productfactory.api.shared.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import java.time.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false", "PF_SCHEDULES_ENABLED=false"])
@ActiveProfiles("test")
@Import(ProductProcessSchedulerIntegrationTest.Fakes::class)
class ProductProcessSchedulerIntegrationTest @Autowired constructor(
    private val productCommands: ProductCommandService,
    private val productQueries: ProductQueryService,
    private val runner: ProductScheduleRunner,
    private val clock: MutableSchedulerClock,
    private val design: ProductDesignService,
    private val planning: ProductPlanningService,
    private val quality: QualityService,
    private val dispatcher: SoftwareFactoryDispatcherService,
) {
    @BeforeEach
    fun reset() {
        clock.now = Instant.parse("2026-03-28T00:00:00Z")
        reset(design, planning, quality, dispatcher)
    }

    @Test
    fun `claim is exact eenmaal haalt hoogstens een gemiste run in en verwerkt twee producten onafhankelijk`() {
        val first = product("schedule-first")
        val second = product("schedule-second")
        schedule(first, ScheduledProcess.PRODUCT_DESIGN)
        schedule(second, ScheduledProcess.PRODUCT_DESIGN)
        clock.now = clock.now.plus(Duration.ofMinutes(2))

        val pool = Executors.newFixedThreadPool(2)
        pool.submit { runner.runDueSchedules() }
        pool.submit { runner.runDueSchedules() }
        pool.shutdown()
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        runner.runDueSchedules()

        verify(design, times(1)).runProcessSession(first)
        verify(design, times(1)).runProcessSession(second)
        assertThat((productQueries.findScheduleRuns(first) + productQueries.findScheduleRuns(second)).filter { it.status == ScheduleRunStatus.SUCCEEDED }).hasSize(2)
        assertThat(productQueries.getProcessSchedule(first, ScheduledProcess.PRODUCT_DESIGN).nextRunAt).isAfter(clock.now)
    }

    @Test
    fun `scheduler roept uitsluitend de vier publieke procesfuncties aan`() {
        val id = product("schedule-all")
        ScheduledProcess.entries.forEach { schedule(id, it) }
        clock.now = clock.now.plus(Duration.ofMinutes(2))

        runner.runDueSchedules()

        verify(design).runProcessSession(id)
        verify(planning).runProcessSession(id)
        verify(quality).runProcessSession(id)
        verify(dispatcher).runDispatchSession(id)
        assertThat(productQueries.findScheduleRuns(id)).allMatch { it.status == ScheduleRunStatus.SUCCEEDED }
    }

    @Test
    fun `inactief product en actieve call worden zichtbaar overgeslagen`() {
        val inactive = product("schedule-inactive")
        schedule(inactive, ScheduledProcess.PRODUCT_DESIGN)
        productCommands.setProductStatus(SetProductStatusCommand(
            inactive, ProductStatus.INACTIVE, productQueries.getProduct(inactive).version, ACTOR, "inactive",
        ))
        val colliding = product("schedule-collision")
        schedule(colliding, ScheduledProcess.PRODUCT_DESIGN)
        doThrow(ProcessAlreadyRunning(colliding)).`when`(design).runProcessSession(colliding)
        clock.now = clock.now.plus(Duration.ofMinutes(2))

        runner.runDueSchedules()

        verify(design, never()).runProcessSession(inactive)
        assertThat((productQueries.findScheduleRuns(inactive) + productQueries.findScheduleRuns(colliding)).filter { it.status == ScheduleRunStatus.SKIPPED }).hasSize(2)
        assertThat(productQueries.findScheduleRuns(colliding).single().errorCode).isEqualTo("PROCESS_ALREADY_RUNNING")
    }

    @Test
    fun `weekritme rekent niet bestaande DST tijd naar eerste geldige instant`() {
        val id = product("schedule-dst")
        productCommands.updateProcessSchedule(UpdateProcessScheduleCommand(
            id, ScheduledProcess.PRODUCT_DESIGN, true, "Europe/Amsterdam",
            SchedulePattern(weeklyRules = listOf(WeeklyScheduleRule(setOf(DayOfWeek.SUNDAY), setOf(LocalTime.of(2, 30))))),
            1, ACTOR, "dst",
        ))

        assertThat(productQueries.getProcessSchedule(id, ScheduledProcess.PRODUCT_DESIGN).nextRunAt)
            .isEqualTo(Instant.parse("2026-03-29T01:00:00Z"))
    }

    private fun product(value: String): ProductId = ProductId(value).also {
        productCommands.createProduct(CreateProductCommand(it, value, actor = ACTOR, idempotencyKey = "create-$value"))
    }

    private fun schedule(id: ProductId, process: ScheduledProcess) {
        productCommands.updateProcessSchedule(UpdateProcessScheduleCommand(
            id, process, true, "Europe/Amsterdam", SchedulePattern(intervalMinutes = 1), 1, ACTOR, "schedule-${id.value}-$process",
        ))
    }

    @TestConfiguration
    class Fakes {
        @Bean @Primary fun mutableClock() = MutableSchedulerClock()
        @Bean @Primary fun design(): ProductDesignService = mock(ProductDesignService::class.java)
        @Bean @Primary fun planning(): ProductPlanningService = mock(ProductPlanningService::class.java)
        @Bean @Primary fun quality(): QualityService = mock(QualityService::class.java)
        @Bean @Primary fun dispatcher(): SoftwareFactoryDispatcherService = mock(SoftwareFactoryDispatcherService::class.java)
    }

    companion object { private val ACTOR = ActorReference(ActorType.STAKEHOLDER, "scheduler-test@example.com") }
}

class MutableSchedulerClock(var now: Instant = Instant.parse("2026-03-28T00:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}
