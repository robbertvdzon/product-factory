package nl.vdzon.productfactory.design.mvp

import nl.vdzon.productfactory.api.design.ProductDesignService
import nl.vdzon.productfactory.api.shared.ProductId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ProductDesignAiOrchestrator(
    private val jdbc: JdbcTemplate,
    private val design: ProductDesignService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun resumeReady() {
        val products = jdbc.query(
            """SELECT s.product_id FROM pf_design_process_session s
                JOIN pf_ai_task t ON t.id=s.current_ai_task_id
                WHERE s.status='WAITING_FOR_AI' AND t.status IN ('SUCCEEDED','FAILED','CANCELLED')
                ORDER BY s.updated_at LIMIT 20""".trimIndent(),
            { rs, _ -> ProductId(rs.getString(1)) },
        )
        products.forEach { productId ->
            runCatching { design.runProcessSession(productId) }
                .onFailure { failure ->
                    log.warn("design_ai_resume_failed productId={} failureType={}", productId.value, failure.javaClass.simpleName)
                }
        }
    }
}

@Component
class ProductDesignAiCoordinator(
    private val orchestrator: ProductDesignAiOrchestrator,
    @Value("\${PF_AI_RUNTIME_SCHEDULING_ENABLED:false}") private val enabled: Boolean,
) {
    @Scheduled(fixedDelayString = "\${PF_DESIGN_AI_RECONCILE_DELAY_MS:2000}")
    fun resume() {
        if (enabled) orchestrator.resumeReady()
    }
}
