package nl.vdzon.productfactory.agentruntime.api

import nl.vdzon.productfactory.contracts.AgentRunView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class AgentRunRegistry(private val jdbc: JdbcTemplate, private val products: ProductCatalog) {
    fun register(runId: String, productSlug: String, taskType: String): AgentRunView {
        val product = products.requireActive(productSlug)
        require(runId.matches(Regex("[A-Za-z0-9._-]{1,120}"))) { "Ongeldig run-ID" }
        require(taskType.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Ongeldig taaktype" }
        try {
            jdbc.update(
                "insert into agent_run(run_id, product_slug, task_type, status) values (?, ?, ?, 'RUNNING')",
                runId,
                product.slug,
                taskType,
            )
        } catch (_: DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Run-ID bestaat al")
        }
        return requireRun(product.slug, runId)
    }

    fun complete(productSlug: String, runId: String, status: String, resultReference: String?): AgentRunView {
        require(status in setOf("COMPLETED", "FAILED")) { "Ongeldige eindstatus" }
        val product = products.requireContext(productSlug)
        if (jdbc.update(
                "update agent_run set status = ?, result_reference = ?, completed_at = current_timestamp where product_slug = ? and run_id = ? and status = 'RUNNING'",
                status,
                resultReference?.take(1000),
                product.slug,
                runId,
            ) == 0
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Agentrun kan niet worden afgerond")
        }
        return requireRun(product.slug, runId)
    }

    fun list(productSlug: String): List<AgentRunView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(SELECT + " where product_slug = ? order by started_at desc", mapper, product.slug)
    }

    fun requireRun(productSlug: String, runId: String): AgentRunView {
        val product = products.requireContext(productSlug)
        return jdbc.query(SELECT + " where product_slug = ? and run_id = ?", mapper, product.slug, runId).singleOrNull()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende agentrun voor dit product")
    }

    private val mapper = { row: java.sql.ResultSet, _: Int ->
        AgentRunView(
            row.getString("run_id"), row.getString("product_slug"), row.getString("task_type"),
            row.getString("status"), row.getString("result_reference"), row.getTimestamp("started_at").toInstant(),
            row.getTimestamp("completed_at")?.toInstant(),
        )
    }

    companion object {
        private const val SELECT = "select run_id, product_slug, task_type, status, result_reference, started_at, completed_at from agent_run"
    }
}
