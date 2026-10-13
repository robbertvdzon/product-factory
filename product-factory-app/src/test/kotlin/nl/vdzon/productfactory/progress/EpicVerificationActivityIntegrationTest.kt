package nl.vdzon.productfactory.progress

import nl.vdzon.productfactory.api.design.EpicDetails
import nl.vdzon.productfactory.api.design.ProductDesignQueryService
import nl.vdzon.productfactory.api.product.CreateProductCommand
import nl.vdzon.productfactory.api.product.ProductCommandService
import nl.vdzon.productfactory.api.shared.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest(properties = ["PF_AUTH_REQUIRED=false", "PF_SCHEDULES_ENABLED=false"])
@ActiveProfiles("test")
@Transactional
class EpicVerificationActivityIntegrationTest @Autowired constructor(
    private val activity: EpicActivityService,
    private val progress: EpicProgressService,
    private val products: ProductCommandService,
    private val design: ProductDesignQueryService,
    private val jdbc: JdbcTemplate,
) {
    private lateinit var epic: EpicDetails
    private val now = Instant.parse("2026-10-13T08:00:00Z")

    @BeforeEach
    fun prepare() {
        val productId = ProductId("verify-activity-${UUID.randomUUID().toString().take(8)}")
        products.createProduct(CreateProductCommand(productId, "Verificatieactiviteit", actor = ActorReference(ActorType.SYSTEM, "test"), idempotencyKey = "create-${productId.value}"))
        val id = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO pf_epic(id,product_id,current_version,status,created_at,updated_at) VALUES (?,?,3,'VERIFYING',?,?)",
            id, productId.value, now, now)
        jdbc.update(
            """INSERT INTO pf_epic_version(epic_id,version,title,summary,problem,solution,direction_references_json,ux_design,
                acceptance_criteria_json,slicability_rationale,source_references_json,status,actor_type,actor_id,created_at)
                VALUES (?,3,'Vergaderingen analyseren','Alle vergaderingen.','Er ontbreekt een vergadering.','Selecteer alle vergaderingen.',
                    '[]','Agenda.','["Alle vergaderingen zijn zichtbaar."]','Zelfstandig te bouwen.','[]','VERIFYING','SYSTEM','test',?)""", id, now,
        )
        epic = design.getEpic(EpicId(id))
    }

    @Test
    fun `structurele blokkade toont herstel in activiteit en voortgang ondanks handmatige retry mogelijkheid`() {
        val id = insertWork("BLOCKED", reason = "De acceptatieroute voor de testscenario's geeft HTTP 404.")
        jdbc.update("UPDATE pf_quality_work_item SET retryable=TRUE,error_code='QUALITY_RESULT_BLOCKED' WHERE id=?", id)

        val result = activity.activity(epic)
        val waiting = progress.progress(epic.id).waitingOn!!

        assertThat(result.state).isEqualTo(EpicActivityState.BLOCKED)
        assertThat(result.label).isEqualTo("Verificatie geblokkeerd")
        assertThat(result.detail).contains("HTTP 404")
        assertThat(result.next).contains("Herstel de testvoorwaarde", "geen automatische herhaling")
        assertThat(result.waitingRole).isNull()
        assertThat(waiting.title).isEqualTo(result.label)
        assertThat(waiting.detail).isEqualTo(result.detail)
        assertThat(waiting.next).isEqualTo(result.next)
        assertThat(waiting.reference).isEqualTo(id)
    }

    @Test
    fun `alleen een retry tijdstip toont een geplande herpoging en geen draaiende test`() {
        val retryAt = now.plusSeconds(3600)
        val id = insertWork("BLOCKED", reason = "De worker is tijdelijk niet bereikbaar.")
        jdbc.update("UPDATE pf_quality_work_item SET retryable=TRUE,retry_after=?,error_code='QUALITY_TRANSIENT_TECHNICAL_FAILURE' WHERE id=?", retryAt, id)

        val result = activity.activity(epic)
        assertThat(result.state).isEqualTo(EpicActivityState.BLOCKED)
        assertThat(result.label).isEqualTo("Verificatie wacht op herpoging")
        assertThat(result.next).contains(retryAt.toString(), "Er draait nu geen test")
        assertThat(progress.progress(epic.id).waitingOn!!.next).isEqualTo(result.next)
    }

    @Test
    fun `wachtrij worker lopende test en gereed resultaat hebben afzonderlijke actuele status`() {
        val id = insertWork("PENDING")
        assertThat(activity.activity(epic).label).isEqualTo("Verificatie staat klaar")
        val session = insertSession(id, "WAITING_FOR_WORKER")
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.QUEUED)
        assertThat(activity.activity(epic).label).isEqualTo("Verificatie wacht op AI-capaciteit")
        updateTask(session, "RUNNING")
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.WORKING)
        assertThat(progress.progress(epic.id).waitingOn!!.title).isEqualTo("Product Factory test de epic")
        updateTask(session, "SUCCEEDED")
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.QUEUED)
        assertThat(activity.activity(epic).label).isEqualTo("Testresultaat wacht op verwerking")
        jdbc.update("UPDATE pf_quality_process_session SET status='RUNNING' WHERE id=?", session)
        assertThat(activity.activity(epic).label).isEqualTo("Product Factory verwerkt de verificatie")
    }

    @Test
    fun `oude epicversie en ander product bepalen de actuele verificatie niet`() {
        val id = insertWork("BLOCKED", reason = "Oude blokkade", version = epic.version - 1)
        assertThat(activity.activity(epic).label).isEqualTo("Verificatie staat klaar")
        jdbc.update("UPDATE pf_quality_work_item SET source_version=? WHERE id=?", epic.version, id)
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.BLOCKED)
        assertThat(activity.activity(epic.copy(productId = ProductId("ander-product"))).state).isEqualTo(EpicActivityState.QUEUED)
        assertThat(activity.activity(epic.copy(id = EpicId("andere-epic"))).state).isEqualTo(EpicActivityState.QUEUED)
    }

    @Test
    fun `nieuwere verificatie vervangt oude blokkade ook nadat het resultaat verwerkt is`() {
        insertWork("BLOCKED", reason = "Oude blokkade", createdAt = now.minusSeconds(60))
        val current = insertWork("PENDING")
        insertSession(current, "RUNNING")
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.WORKING)
        jdbc.update("UPDATE pf_quality_work_item SET status='DONE' WHERE id=?", current)
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.QUEUED)
        assertThat(progress.progress(epic.id).waitingOn!!.detail).doesNotContain("Oude blokkade")
    }

    @Test
    fun `afgesloten of niet gekoppelde kwaliteitssessie bewijst geen actieve tester`() {
        val work = insertWork("IN_PROGRESS")
        val session = insertSession(work, "RUNNING")
        jdbc.update("UPDATE pf_quality_process_session SET active_product_id=NULL,finished_at=? WHERE id=?", now, session)
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.QUEUED)
        jdbc.update("UPDATE pf_quality_process_session SET active_product_id=?,finished_at=NULL WHERE id=?", epic.productId.value, session)
        jdbc.update("UPDATE pf_quality_work_item SET claimed_by_session_id=NULL WHERE id=?", work)
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.QUEUED)
    }

    private fun insertWork(status: String, reason: String? = null, version: Long = epic.version, createdAt: Instant = now): String {
        val id = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO pf_quality_work_item(id,idempotency_key,request_fingerprint,product_id,type,source_type,source_id,source_version,
                request_json,target_environment,priority,status,blocked_reason,attempt_count,retryable,created_at,updated_at,version)
                VALUES (?,?,?,?,'VERIFY_EPIC','EPIC',?,?,'{}','acceptance',50,?,?,1,FALSE,?,?,1)""",
            id, id, "f".repeat(64), epic.productId.value, epic.id.value, version, status, reason, createdAt, createdAt,
        )
        return id
    }

    private fun insertSession(workId: String, taskStatus: String): String {
        val id = UUID.randomUUID().toString()
        val taskId = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO pf_ai_task(id,idempotency_key,request_fingerprint,job_key,product_id,requester_capability,
                requester_session_id,agent_role,provider,model,configuration_version,prompt_template_version,status,created_at,updated_at)
                VALUES (?,?,?,'QUALITY.VERIFY_EPIC',?,'QUALITY_ASSURANCE',?,'TESTER_MVP','MOCKED','test',1,1,?,?,?)""",
            taskId, taskId, "f".repeat(64), epic.productId.value, id, taskStatus, now, now,
        )
        jdbc.update(
            """INSERT INTO pf_quality_process_session(id,product_id,active_product_id,status,implementation_artifact,
                implementation_variant,implementation_version,implementation_revision,inputs_json,publications_json,
                claimed_work_items_json,memory_version_ids_json,ai_task_ids_json,current_ai_task_id,started_at,updated_at)
                VALUES (?,?,?,'WAITING_FOR_AI','test','test','1','test','[]','[]','[]','[]','[]',?,?,?)""",
            id, epic.productId.value, epic.productId.value, taskId, now, now,
        )
        jdbc.update("UPDATE pf_quality_work_item SET status='IN_PROGRESS',claimed_by_session_id=? WHERE id=?", id, workId)
        return id
    }

    private fun updateTask(sessionId: String, status: String) {
        jdbc.update("UPDATE pf_ai_task SET status=? WHERE id=(SELECT current_ai_task_id FROM pf_quality_process_session WHERE id=?)", status, sessionId)
    }
}
