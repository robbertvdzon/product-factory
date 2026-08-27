package nl.vdzon.productfactory.planning.mvp

import nl.vdzon.productfactory.api.planning.ProductPlanningService
import nl.vdzon.productfactory.api.shared.ProductId
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.logging.Level
import java.util.logging.Logger

@Component
class ProductPlanningAiOrchestrator(
    private val jdbc: JdbcTemplate,
    private val planning: ProductPlanningService,
) {
    private val log = Logger.getLogger(javaClass.name)

    fun resumeReady() {
        val products = jdbc.query(
            """SELECT DISTINCT s.product_id FROM pf_planning_process_session s
                JOIN pf_ai_task t ON t.id=s.current_ai_task_id
                WHERE (s.status='WAITING_FOR_AI' AND t.status IN ('SUCCEEDED','FAILED','CANCELLED'))
                   OR (s.status='BLOCKED' AND t.status IN ('SUCCEEDED','FAILED','CANCELLED') AND
                       ((t.status='FAILED' AND s.ai_attempt < ?) OR
                        (t.job_key='PLANNING.SLICE_EPIC' AND t.prompt_template_version < ?)))
                ORDER BY s.product_id LIMIT 20""".trimIndent(),
            { rs, _ -> ProductId(rs.getString(1)) },
            ProductPlanningMvpService.MAX_AUTOMATIC_AI_ATTEMPTS,
            ProductPlanningMvpService.PLAN_PROMPT_VERSION,
        )
        products.forEach { productId ->
            runCatching { planning.runProcessSession(productId) }
                .onFailure { failure ->
                    log.log(Level.WARNING, "planning_ai_resume_failed productId=${productId.value} failureType=${failure.javaClass.simpleName}", failure)
                }
        }
    }
}

@Component
class ProductPlanningAiCoordinator(
    private val orchestrator: ProductPlanningAiOrchestrator,
    @Value("\${PF_AI_RUNTIME_SCHEDULING_ENABLED:false}") private val enabled: Boolean,
) {
    @Scheduled(fixedDelayString = "\${PF_PLANNING_AI_RECONCILE_DELAY_MS:2000}")
    fun resume() {
        if (enabled) orchestrator.resumeReady()
    }
}
