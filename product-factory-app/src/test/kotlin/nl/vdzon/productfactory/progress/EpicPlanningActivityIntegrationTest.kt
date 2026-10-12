package nl.vdzon.productfactory.progress

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.design.EpicDetails
import nl.vdzon.productfactory.api.design.EpicStatus
import nl.vdzon.productfactory.api.product.CreateProductCommand
import nl.vdzon.productfactory.api.product.ProductCommandService
import nl.vdzon.productfactory.api.shared.ActorReference
import nl.vdzon.productfactory.api.shared.ActorType
import nl.vdzon.productfactory.api.shared.EpicId
import nl.vdzon.productfactory.api.shared.ProductId
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
class EpicPlanningActivityIntegrationTest @Autowired constructor(
    private val activity: EpicActivityService,
    private val products: ProductCommandService,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    private lateinit var epic: EpicDetails
    private val now = Instant.parse("2026-10-12T10:00:00Z")

    @BeforeEach
    fun prepare() {
        val productId = ProductId("plan-activity-${UUID.randomUUID().toString().take(8)}")
        products.createProduct(CreateProductCommand(productId, "Planningactiviteit", actor = ActorReference(ActorType.SYSTEM, "test"), idempotencyKey = "create-${productId.value}"))
        epic = EpicDetails(
            EpicId(UUID.randomUUID().toString()), productId, "Vergaderingen analyseren", "Analyseer meerdere vergaderingen.",
            "De volgende vergadering ontbreekt.", "Neem de volgende vergaderingen mee.", emptyList(),
            acceptanceCriteria = listOf("Alle geselecteerde vergaderingen zijn zichtbaar."), slicabilityRationale = "Zelfstandig uitvoerbaar.",
            status = EpicStatus.IN_PLANNING, version = 3, createdAt = now, updatedAt = now,
        )
    }

    @Test
    fun `afgekeurd plan is geblokkeerd ook als de AI taak geslaagd is`() {
        val session = insertSession("BLOCKED", taskStatus = "SUCCEEDED", error = "PLANNING_RESULT_INVALID")
        jdbc.update("UPDATE pf_planning_process_session SET blocked_reason=? WHERE id=?", "Interne fout met gevoelige context", session)

        val result = activity.activity(epic)

        assertThat(result.state).isEqualTo(EpicActivityState.BLOCKED)
        assertThat(result.label).isEqualTo("Planning geblokkeerd")
        assertThat(result.detail).contains("inhoudelijke controles", "beheerder").doesNotContain("gevoelige context")
    }

    @Test
    fun `mislukte sessie en mislukte of geannuleerde AI taak tonen geen voortgang`() {
        val session = insertSession("FAILED", taskStatus = "SUCCEEDED", error = "PLANNING_SESSION_FAILED")
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.BLOCKED)

        jdbc.update("UPDATE pf_planning_process_session SET status='WAITING_FOR_AI',error_code=NULL WHERE id=?", session)
        for (status in listOf("FAILED", "CANCELLED")) {
            updateTask(session, status)
            assertThat(activity.activity(epic).state).describedAs(status).isEqualTo(EpicActivityState.BLOCKED)
        }
    }

    @Test
    fun `wachtrij actieve AI en gereed resultaat krijgen elk een eerlijke activiteit`() {
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.QUEUED)
        val session = insertSession("WAITING_FOR_AI", taskStatus = "PENDING_SUBMISSION")
        for (status in listOf("PENDING_SUBMISSION", "QUEUED", "WAITING_FOR_WORKER")) {
            updateTask(session, status)
            assertThat(activity.activity(epic).state).describedAs(status).isEqualTo(EpicActivityState.QUEUED)
        }
        updateTask(session, "RUNNING")
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.WORKING)
        updateTask(session, "SUCCEEDED")
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.QUEUED)
        assertThat(activity.activity(epic).detail).contains("wacht op controle en verwerking")
        jdbc.update("UPDATE pf_planning_process_session SET status='RUNNING' WHERE id=?", session)
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.WORKING)
    }

    @Test
    fun `planning geldt uitsluitend voor exact de geselecteerde epicversie`() {
        val session = insertSession("BLOCKED", taskStatus = "SUCCEEDED", error = "PLANNING_RESULT_INVALID")
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.BLOCKED)
        assertThat(activity.activity(epic.copy(id = EpicId(UUID.randomUUID().toString()))).state).isEqualTo(EpicActivityState.QUEUED)
        assertThat(activity.activity(epic.copy(version = epic.version + 1)).state).isEqualTo(EpicActivityState.QUEUED)

        // Even an inconsistent cross-product active pointer cannot expose another product's work.
        val otherProduct = ProductId("other-${UUID.randomUUID().toString().take(8)}")
        products.createProduct(CreateProductCommand(otherProduct, "Ander product", actor = ActorReference(ActorType.SYSTEM, "test"), idempotencyKey = "create-${otherProduct.value}"))
        jdbc.update("UPDATE pf_planning_process_session SET product_id=? WHERE id=?", otherProduct.value, session)
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.QUEUED)
    }

    @Test
    fun `oude beeindigde sessies overschrijven een nieuwe planning niet`() {
        val old = insertSession("BLOCKED", taskStatus = "SUCCEEDED", error = "PLANNING_RESULT_INVALID")
        jdbc.update("UPDATE pf_planning_process_session SET active_product_id=NULL,finished_at=? WHERE id=?", now.plusSeconds(10), old)
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.QUEUED)
        val current = insertSession("WAITING_FOR_AI", taskStatus = "RUNNING")
        assertThat(activity.activity(epic).state).isEqualTo(EpicActivityState.WORKING)

        for (status in listOf("SUCCEEDED", "CANCELLED")) {
            jdbc.update("UPDATE pf_planning_process_session SET status=? WHERE id=?", status, current)
            assertThat(activity.activity(epic).state).describedAs(status).isEqualTo(EpicActivityState.QUEUED)
        }
        assertThat(activity.activity(epic.copy(status = EpicStatus.COMPLETED)).state).isEqualTo(EpicActivityState.DONE)
    }

    private fun insertSession(status: String, taskStatus: String, error: String? = null): String {
        val id = UUID.randomUUID().toString()
        val taskId = UUID.randomUUID().toString()
        jdbc.update(
            """INSERT INTO pf_ai_task(id,idempotency_key,request_fingerprint,job_key,product_id,requester_capability,
                requester_session_id,agent_role,provider,model,configuration_version,prompt_template_version,status,created_at,updated_at)
                VALUES (?,?,?,'PLANNING.SLICE_EPIC',?, 'PRODUCT_PLANNING',?,'PLANNER_MVP','MOCKED','test',1,1,?,?,?)""",
            taskId, taskId, "f".repeat(64), epic.productId.value, id, taskStatus, now, now,
        )
        jdbc.update(
            """INSERT INTO pf_planning_process_session(id,product_id,active_product_id,status,phase,implementation_artifact,
                implementation_variant,implementation_version,implementation_revision,inputs_json,publications_json,
                selected_epics_json,claimed_work_items_json,memory_version_ids_json,ai_task_ids_json,current_ai_task_id,error_code,started_at,updated_at)
                VALUES (?,?,?,?,'PLANNING','test','test','1','test','[]','[]',?,'[]','[]','[]',?,?,?,?)""",
            id, epic.productId.value, epic.productId.value, status,
            mapper.writeValueAsString(listOf(mapOf("id" to epic.id, "plannedVersion" to epic.version - 1, "expectedStateVersion" to epic.version, "activateAfterPlanning" to true))),
            taskId, error, now, now,
        )
        return id
    }

    private fun updateTask(sessionId: String, status: String) {
        jdbc.update("UPDATE pf_ai_task SET status=? WHERE id=(SELECT current_ai_task_id FROM pf_planning_process_session WHERE id=?)", status, sessionId)
    }
}
