package nl.vdzon.productfactory.product

import nl.vdzon.productfactory.api.shared.AggregateNotFound
import nl.vdzon.productfactory.api.shared.ProductId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Permanently removes all Product Factory data for a product. External Software Factory work is untouched. */
@Service
class ProductDeletionService(private val jdbc: JdbcTemplate) {
    @Transactional
    fun delete(productId: ProductId) {
        val id = productId.value
        if ((jdbc.queryForObject("SELECT COUNT(*) FROM pf_product WHERE product_id=?", Long::class.java, id) ?: 0L) == 0L) {
            throw AggregateNotFound("Product $id bestaat niet.")
        }

        // AI children and product-scoped AI tasks.
        jdbc.update("DELETE FROM pf_ai_artifact_domain_reference WHERE artifact_id IN (SELECT id FROM pf_ai_artifact WHERE task_id IN (SELECT id FROM pf_ai_task WHERE product_id=?))", id)
        jdbc.update("DELETE FROM pf_ai_artifact WHERE task_id IN (SELECT id FROM pf_ai_task WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_ai_runtime_upload WHERE task_id IN (SELECT id FROM pf_ai_task WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_ai_task_input WHERE task_id IN (SELECT id FROM pf_ai_task WHERE product_id=?)", id)
        for (table in listOf("pf_ai_task_specification", "pf_ai_runtime_event", "pf_ai_runtime_event_cursor", "pf_ai_runtime_attempt_usage", "pf_ai_runtime_usage", "pf_ai_task_result", "pf_ai_runtime_outbox")) {
            jdbc.update("DELETE FROM $table WHERE task_id IN (SELECT id FROM pf_ai_task WHERE product_id=?)", id)
        }
        jdbc.update("DELETE FROM pf_meeting_ai_work WHERE task_id IN (SELECT id FROM pf_ai_task WHERE product_id=?) OR meeting_id IN (SELECT meeting_id FROM pf_meeting WHERE product_id=?)", id, id)

        // Memory has its own reference graph.
        jdbc.update("DELETE FROM pf_agent_memory_read_audit WHERE memory_version_id IN (SELECT v.memory_version_id FROM pf_agent_memory_version v JOIN pf_agent_memory_item i ON i.memory_item_id=v.memory_item_id WHERE i.product_id=?)", id)
        jdbc.update("DELETE FROM pf_agent_memory_retraction WHERE memory_item_id IN (SELECT memory_item_id FROM pf_agent_memory_item WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_agent_memory_version WHERE memory_item_id IN (SELECT memory_item_id FROM pf_agent_memory_item WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_agent_memory_item WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_agent_memory_budget WHERE product_id=?", id)

        // Delivery, quality and planning reference stories and epics.
        jdbc.update("DELETE FROM pf_delivery_attempt WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_dispatcher_product_state WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_dispatcher_process_session WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_story_dispatch_reservation WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_bug_story WHERE bug_id IN (SELECT id FROM pf_bug WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_bug_version WHERE bug_id IN (SELECT id FROM pf_bug WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_bug WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_quality_attempt WHERE work_item_id IN (SELECT id FROM pf_quality_work_item WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_verification WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_quality_snapshot WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_quality_work_item WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_quality_process_session WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_story_version WHERE story_id IN (SELECT id FROM pf_story WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_story WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_epic_cancellation_marker WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_planning_work_item WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_planning_process_session WHERE product_id=?", id)

        // Product design and collaboration.
        jdbc.update("DELETE FROM pf_approved_epic_planning_trigger WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_epic_approval_record WHERE epic_id IN (SELECT id FROM pf_epic WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_epic_review WHERE epic_id IN (SELECT id FROM pf_epic WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_design_cancellation_operation WHERE epic_id IN (SELECT id FROM pf_epic WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_design_command WHERE epic_id IN (SELECT id FROM pf_epic WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_epic_version WHERE epic_id IN (SELECT id FROM pf_epic WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_epic WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_design_process_session WHERE product_id=?", id)

        jdbc.update("DELETE FROM pf_personal_notification WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_design_work_item WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_stakeholder_question WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_product_request_approval WHERE request_id IN (SELECT request_id FROM pf_product_request WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_product_request_route WHERE request_id IN (SELECT request_id FROM pf_product_request WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_product_request_version WHERE request_id IN (SELECT request_id FROM pf_product_request WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_product_request WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_product_advisor_turn WHERE conversation_id IN (SELECT conversation_id FROM pf_product_conversation WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_product_conversation_message WHERE conversation_id IN (SELECT conversation_id FROM pf_product_conversation WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_product_conversation WHERE product_id=?", id)

        jdbc.update("DELETE FROM pf_meeting_message WHERE meeting_id IN (SELECT meeting_id FROM pf_meeting WHERE product_id=?)", id)
        jdbc.update("DELETE FROM pf_meeting WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_user_signal WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_decision_details WHERE decision_id IN (SELECT decision_id FROM pf_decision WHERE product_id=?)", id)
        jdbc.update("UPDATE pf_decision SET superseded_by_decision_id=NULL WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_decision WHERE product_id=?", id)

        // Configuration, access and identity links.
        jdbc.update("DELETE FROM pf_agent_environment_grant WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_product_environment_key WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_environment_access_command WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_schedule_run WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_process_schedule WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_product_governance_history WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_product_governance_policy WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_product_membership_history WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_product_membership WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_testable_product_configuration WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_product_assignment WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_ai_task WHERE product_id=?", id)
        jdbc.update("DELETE FROM pf_product WHERE product_id=?", id)
    }
}
