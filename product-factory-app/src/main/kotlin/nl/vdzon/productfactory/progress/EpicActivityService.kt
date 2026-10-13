package nl.vdzon.productfactory.progress

import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.design.*
import nl.vdzon.productfactory.api.advisor.ProductMembershipRole
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

enum class EpicActivityState { WORKING, QUEUED, WAITING_FOR_HUMAN, BLOCKED, DONE, STOPPED }
data class EpicActivity(
    val state: EpicActivityState,
    val label: String,
    val detail: String,
    val waitingRole: ProductMembershipRole? = null,
    val action: String? = null,
    val pendingApprovalRoles: List<ProductMembershipRole> = emptyList(),
    val next: String? = null,
)

/** Current responsibility, separate from the epic's lifecycle and future approval gates. */
@Service
class EpicActivityService(
    private val design: ProductDesignQueryService,
    private val products: ProductQueryService,
    private val policies: ProductGovernanceService,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val verification: EpicVerificationActivityService,
) {
    fun activities(productId: ProductId): Map<String, EpicActivity> {
        val questions = products.findStakeholderQuestions(StakeholderQuestionFilter(productId))
        val policy = policies.getPolicy(productId)
        return design.findEpics(EpicFilter(productId)).associate { it.id.value to activity(it, questions, policy) }
    }

    fun activity(epic: EpicDetails): EpicActivity = activity(epic,
        products.findStakeholderQuestions(StakeholderQuestionFilter(epic.productId)), policies.getPolicy(epic.productId))

    private fun activity(epic: EpicDetails, questions: List<StakeholderQuestionDetails>, policy: ProductGovernancePolicy): EpicActivity {
        val current = currentActivity(epic, questions, policy)
        if (current.state in setOf(EpicActivityState.DONE, EpicActivityState.STOPPED)) return current
        val pending = buildList {
            epic.review?.let { review ->
                if (!review.productOwnerApproved && policy.productOwnerMode == ResponsibilityMode.HUMAN) add(ProductMembershipRole.PRODUCT_OWNER)
                if (!review.architectApproved && review.architectRequired) add(ProductMembershipRole.ARCHITECT)
            }
        }
        return current.copy(pendingApprovalRoles = pending)
    }

    private fun currentActivity(epic: EpicDetails, questions: List<StakeholderQuestionDetails>, policy: ProductGovernancePolicy): EpicActivity {
        if (epic.status == EpicStatus.COMPLETED) return EpicActivity(EpicActivityState.DONE, "Afgerond", "De epic is opgeleverd en gecontroleerd.")
        if (epic.status in setOf(EpicStatus.CANCELLED, EpicStatus.WITHDRAWN, EpicStatus.SUPERSEDED, EpicStatus.NOT_SUCCESSFUL))
            return EpicActivity(EpicActivityState.STOPPED, "Gestopt", "De Product Factory werkt niet verder aan deze epic.")
        val storyIds = jdbc.query("SELECT id FROM pf_story WHERE epic_id=? AND status<>'CANCELLED'", { rs, _ -> rs.getString(1) }, epic.id.value).toSet()
        val question = questions.firstOrNull { q -> q.status == StakeholderQuestionStatus.OPEN &&
            (q.epicLinkId == epic.id || (epic.sourceProductRequestId != null && q.productRequestId?.value == epic.sourceProductRequestId) ||
                q.storyLinkId?.value in storyIds || q.linkedObjects.any { (it.type == "EPIC" && it.id == epic.id.value) || (it.type == "STORY" && it.id in storyIds) }) }
        if (question != null) return human(question.requestedRole, "ANSWER")

        if (policy.configured && epic.readiness.readyForPlanning) {
            epic.review?.let { review ->
                if (!review.productOwnerApproved && policy.productOwnerMode == ResponsibilityMode.HUMAN) return human(ProductMembershipRole.PRODUCT_OWNER, "REVIEW")
                if (!review.architectApproved && review.architectRequired) return human(ProductMembershipRole.ARCHITECT, "REVIEW")
                if (!review.ready) return queued("De Product Factory moet de automatische beoordeling nog afronden.")
            }
        }
        if (!policy.configured && epic.status in setOf(EpicStatus.AWAITING_APPROVAL, EpicStatus.AWAITING_FACTORY_OWNER_APPROVAL))
            return human(ProductMembershipRole.ARCHITECT, "REVIEW")
        if (!policy.configured && epic.status == EpicStatus.AWAITING_PRODUCT_OWNER_APPROVAL)
            return human(ProductMembershipRole.PRODUCT_OWNER, "REVIEW")

        if (epic.status in setOf(EpicStatus.NEEDS_RESEARCH, EpicStatus.NEEDS_REFINEMENT)) {
            val sessions = jdbc.query(
                """SELECT s.status,s.error_code,s.snapshot_json,t.status AS task_status,w.epic_id,w.request_id
                    FROM pf_design_process_session s LEFT JOIN pf_ai_task t ON t.id=s.current_ai_task_id
                    LEFT JOIN pf_design_work_item w ON w.process_session_id=s.id
                    WHERE s.active_product_id=? ORDER BY s.started_at DESC""",
                { rs, _ -> DesignActivity(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)) }, epic.productId.value,
            )
            val session = sessions.firstOrNull { it.epicId == epic.id.value ||
                (epic.sourceProductRequestId != null && it.requestId == epic.sourceProductRequestId) ||
                it.snapshot?.let { json -> mapper.readTree(json).path("currentEpicToRefine").path("id").asText() == epic.id.value } == true }
            if (session?.error == "WAITING_FOR_USER") return EpicActivity(EpicActivityState.BLOCKED, "Wacht op verduidelijking", "De uitwerking is gepauzeerd. Er is nog geen actuele antwoordvraag aan deze epic gekoppeld.")
            if (session?.status in setOf("BLOCKED", "FAILED") || session?.task in setOf("FAILED", "CANCELLED"))
                return EpicActivity(EpicActivityState.BLOCKED, "Product Factory kan niet verder", "De automatische uitwerking is onderbroken en vraagt aandacht van de beheerder.")
            if (session?.task in setOf("RUNNING", "SUCCEEDED") || session?.status == "RUNNING")
                return EpicActivity(EpicActivityState.WORKING, "Product Factory werkt de epic uit", "De AI onderzoekt of verwerkt de uitwerking. Na de uitwerking volgt de beoordeling.")
            return queued("De verdere uitwerking moet nog door de Product Factory worden opgepakt.")
        }
        return when (epic.status) {
            EpicStatus.ACTIVE -> {
                val running = jdbc.queryForObject("SELECT COUNT(*) FROM pf_story WHERE epic_id=? AND status='IN_PROGRESS'", Long::class.java, epic.id.value) ?: 0
                if (running > 0) EpicActivity(EpicActivityState.WORKING, "Product Factory bouwt en test", "De uitvoering van de epic is bezig.")
                else queued("De Product Factory moet de volgende bouw- of controlestap oppakken.")
            }
            EpicStatus.IN_PLANNING -> planningActivity(epic)
            EpicStatus.VERIFYING -> verification.current(epic).let { EpicActivity(it.state, it.label, it.detail, next = it.next) }
            else -> queued("De Product Factory moet de volgende automatische stap oppakken.")
        }
    }

    private fun planningActivity(epic: EpicDetails): EpicActivity {
        // A product can have several epics. Only the active session's frozen selection owns
        // this planning state; an older selection must not describe a newer epic version.
        val session = jdbc.query(
            """SELECT s.status,s.error_code,s.selected_epics_json,t.status AS task_status
                FROM pf_planning_process_session s LEFT JOIN pf_ai_task t ON t.id=s.current_ai_task_id
                WHERE s.product_id=? AND s.active_product_id=? AND s.phase='PLANNING'
                    AND s.status IN ('RUNNING','WAITING_FOR_AI','BLOCKED','FAILED')""",
            { rs, _ -> PlanningActivity(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)) },
            epic.productId.value, epic.productId.value,
        ).singleOrNull()?.takeIf { candidate ->
            mapper.readTree(candidate.selectedEpics).any {
                it.path("id").asText() == epic.id.value && it.path("expectedStateVersion").asLong(-1) == epic.version
            }
        }
        if (session?.status in setOf("BLOCKED", "FAILED") || session?.task in setOf("FAILED", "CANCELLED")) {
            val detail = when (session?.error) {
                "PLANNING_RESULT_INVALID" -> "Het AI-plan voldoet niet aan alle inhoudelijke controles. De planning is gepauzeerd en vraagt aandacht van de beheerder."
                "PLANNING_VERSION_CONFLICT" -> "De epic of backlog is gewijzigd tijdens het plannen. De planning is gepauzeerd en vraagt aandacht van de beheerder."
                "PLANNING_PUBLICATION_CONFLICT" -> "Het plan kon door een conflict in de backlog niet worden opgeslagen. De planning is gepauzeerd en vraagt aandacht van de beheerder."
                else -> "De automatische planning is onderbroken en vraagt aandacht van de beheerder."
            }
            return EpicActivity(EpicActivityState.BLOCKED, "Planning geblokkeerd", detail)
        }
        if (session?.task == "RUNNING")
            return EpicActivity(EpicActivityState.WORKING, "Product Factory maakt de planning", "De AI werkt de epic uit in uitvoerbare stories.")
        if (session?.status == "RUNNING")
            return EpicActivity(EpicActivityState.WORKING, "Product Factory verwerkt de planning", "De Product Factory bereidt de planning voor of controleert en verwerkt het plan.")
        if (session?.status == "WAITING_FOR_AI") return when (session.task) {
            "SUCCEEDED" -> queued("Het AI-plan is klaar en wacht op controle en verwerking door de Product Factory.")
            "WAITING_FOR_WORKER" -> queued("De planning wacht op beschikbare AI-capaciteit.")
            else -> queued("De planningsopdracht staat klaar en wacht tot de AI begint.")
        }
        return EpicActivity(EpicActivityState.QUEUED, "Product Factory is aan zet: planning", "De epic wordt opgepakt voor het plannen van de uitvoering.")
    }

    private fun human(role: ProductMembershipRole, action: String): EpicActivity {
        val name = if (role == ProductMembershipRole.ARCHITECT) "de architect" else "de PO"
        val task = if (action == "ANSWER") "antwoord" else "goedkeuring of feedback"
        return EpicActivity(EpicActivityState.WAITING_FOR_HUMAN, "Wacht op $task van $name",
            if (action == "ANSWER") "De Product Factory wacht op een antwoord op een open vraag. Daarna kan de uitwerking verder."
            else "De uitwerking ligt klaar voor beoordeling. $name kan akkoord geven of aangeven wat aangepast moet worden.", role, action)
    }
    private fun queued(detail: String) = EpicActivity(EpicActivityState.QUEUED, "Wacht op de Product Factory", detail)
    private data class DesignActivity(val status: String, val error: String?, val snapshot: String?, val task: String?, val epicId: String?, val requestId: String?)
    private data class PlanningActivity(val status: String, val error: String?, val selectedEpics: String, val task: String?)
}
