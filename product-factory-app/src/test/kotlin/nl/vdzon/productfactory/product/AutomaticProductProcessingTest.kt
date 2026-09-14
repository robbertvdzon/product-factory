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
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false", "PF_SCHEDULES_ENABLED=false"])
@ActiveProfiles("test")
@Import(ProductProcessSchedulerIntegrationTest.Fakes::class)
class AutomaticProductProcessingTest @Autowired constructor(
    private val automatic: AutomaticProductProcessing,
    private val commands: ProductCommandService,
    private val queries: ProductQueryService,
    private val jdbc: JdbcTemplate,
    private val clock: MutableSchedulerClock,
    private val design: ProductDesignService,
    private val planning: ProductPlanningService,
    private val quality: QualityService,
    private val dispatcher: SoftwareFactoryDispatcherService,
) {
    private var id = ProductId("uninitialized")
    private val actor = ActorReference(ActorType.SYSTEM,"test")
    @BeforeEach fun setup() {
        clock.now = Instant.parse("2026-09-14T00:00:00Z")
        reset(design,planning,quality,dispatcher)
        id = commands.createProduct(CreateProductCommand(ProductId("auto-${UUID.randomUUID().toString().take(8)}"),"Automatisch",actor=actor,idempotencyKey=UUID.randomUUID().toString()))
    }
    @Test fun `honderd lege controles starten geen AI processen of geschiedenis`() {
        assertThat(queries.getProduct(id).dispatchingEnabled).isTrue()
        repeat(100) { ScheduledProcess.entries.forEach { automatic.check(id.value,it) }; clock.now=clock.now.plusSeconds(10) }
        verifyNoInteractions(design,planning,quality)
        for (table in listOf("pf_design_process_session","pf_planning_process_session","pf_quality_process_session","pf_dispatcher_process_session","pf_schedule_run")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM $table WHERE product_id=?",Long::class.java,id.value)).isZero()
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_automation_process WHERE product_id=?",Long::class.java,id.value)).isLessThanOrEqualTo(4)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pf_automation_process WHERE product_id=? AND error_code IS NOT NULL",Long::class.java,id.value)).isZero()
    }
    @Test fun `pauze en inactief blokkeren alle automatische processtarts`() {
        commands.setProductDispatching(SetProductDispatchingCommand(id,false,1,actor,"pause-${id.value}"))
        ScheduledProcess.entries.forEach { automatic.check(id.value,it) }
        verifyNoInteractions(design,planning,quality,dispatcher)
        commands.setProductDispatching(SetProductDispatchingCommand(id,true,2,actor,"resume-${id.value}"))
        commands.setProductStatus(SetProductStatusCommand(id,ProductStatus.INACTIVE,3,actor,"inactive-${id.value}"))
        ScheduledProcess.entries.forEach { automatic.check(id.value,it) }
        verifyNoInteractions(design,planning,quality,dispatcher)
    }
    @Test fun `gewijzigd werk start eenmaal en blijft na herhaalde controle stil`() {
        val work = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO pf_planning_work_item(id,idempotency_key,request_fingerprint,product_id,type,source_type,source_id,source_version,explanation,priority,status,created_at,updated_at,version) VALUES (?,?,?,?,'MANUAL_REPLAN','PRODUCT',?,1,'Nieuwe wens',1,'PENDING',?,?,1)",work,work,"f".repeat(64),id.value,id.value,clock.now,clock.now)
        repeat(10) { automatic.check(id.value,ScheduledProcess.PRODUCT_PLANNING) }
        verify(planning,times(1)).runProcessSession(id)
        jdbc.update("UPDATE pf_planning_work_item SET version=2 WHERE id=?",work)
        automatic.check(id.value,ScheduledProcess.PRODUCT_PLANNING)
        verify(planning,times(2)).runProcessSession(id)
    }
    @Test fun `inhoudelijke blokkade herhaalt geen AI zonder nieuwe input`() {
        val work = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO pf_planning_work_item(id,idempotency_key,request_fingerprint,product_id,type,source_type,source_id,source_version,explanation,priority,status,created_at,updated_at,version) VALUES (?,?,?,?,'MANUAL_REPLAN','PRODUCT',?,1,'Nieuwe wens',1,'PENDING',?,?,1)",work,work,"f".repeat(64),id.value,id.value,clock.now,clock.now)
        doAnswer {
            jdbc.update("""INSERT INTO pf_planning_process_session(id,product_id,status,phase,implementation_artifact,implementation_variant,implementation_version,implementation_revision,
                inputs_json,publications_json,selected_epics_json,claimed_work_items_json,memory_version_ids_json,ai_task_ids_json,error_code,started_at,updated_at)
                VALUES (?,?,'BLOCKED','COMPLETED','test','test','1','abc','[]','[]','[]','[]','[]','[]','NEEDS_INPUT',?,?)""", UUID.randomUUID().toString(),id.value,clock.now,clock.now)
            null
        }.`when`(planning).runProcessSession(id)
        repeat(100) { automatic.check(id.value,ScheduledProcess.PRODUCT_PLANNING); clock.now=clock.now.plusSeconds(600) }
        verify(planning,times(1)).runProcessSession(id)
        jdbc.update("UPDATE pf_planning_work_item SET version=2 WHERE id=?",work)
        automatic.check(id.value,ScheduledProcess.PRODUCT_PLANNING)
        verify(planning,times(2)).runProcessSession(id)
    }
    @Test fun `tijdelijke fouten wachten oplopend en herstel wist de fout`() {
        doThrow(IllegalStateException("offline")).`when`(dispatcher).checkAutomatically(id)
        automatic.check(id.value,ScheduledProcess.SOFTWARE_FACTORY_DISPATCHER)
        repeat(10) { automatic.check(id.value,ScheduledProcess.SOFTWARE_FACTORY_DISPATCHER) }
        verify(dispatcher,times(1)).checkAutomatically(id)
        assertThat(retry()).isEqualTo(clock.now.plusSeconds(20))
        clock.now=retry()
        automatic.check(id.value,ScheduledProcess.SOFTWARE_FACTORY_DISPATCHER)
        assertThat(retry()).isEqualTo(clock.now.plusSeconds(40))
        clock.now=retry()
        doNothing().`when`(dispatcher).checkAutomatically(id)
        automatic.check(id.value,ScheduledProcess.SOFTWARE_FACTORY_DISPATCHER)
        assertThat(jdbc.queryForObject("SELECT failure_count FROM pf_automation_process WHERE product_id=?",Int::class.java,id.value)).isZero()
    }
    @Test fun `databaselease voorkomt dubbel werk en herstelt na een herstart`() {
        jdbc.update("INSERT INTO pf_automation_state(product_id,next_check_at) VALUES (?,?)",id.value,clock.now)
        assertThat(automatic.claim(id.value,"first",clock.now)).isTrue()
        assertThat(automatic.claim(id.value,"second",clock.now)).isFalse()
        clock.now=clock.now.plusSeconds(301)
        assertThat(automatic.claim(id.value,"second",clock.now)).isTrue()
        automatic.runClaimed(id.value,"second")
        assertThat(jdbc.queryForObject("SELECT checked_at FROM pf_automation_state WHERE product_id=?",java.sql.Timestamp::class.java,id.value)!!.toInstant()).isEqualTo(clock.now)
    }
    private fun retry() = jdbc.queryForObject("SELECT retry_after FROM pf_automation_process WHERE product_id=?",java.sql.Timestamp::class.java,id.value)!!.toInstant()
}
