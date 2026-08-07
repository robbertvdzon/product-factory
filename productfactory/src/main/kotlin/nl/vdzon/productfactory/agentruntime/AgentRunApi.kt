package nl.vdzon.productfactory.agentruntime

import nl.vdzon.productfactory.contracts.AgentRunView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class RegisterAgentRunRequest(val runId: String, val productSlug: String, val taskType: String)

@RestController
@RequestMapping("/api/agent-runs")
class AgentRunController(private val jdbc: JdbcTemplate, private val products: ProductCatalog) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterAgentRunRequest): AgentRunView {
        val product = products.requireActive(request.productSlug)
        require(request.runId.matches(Regex("[A-Za-z0-9._-]{1,120}"))) { "Ongeldig run-ID" }
        require(request.taskType.matches(Regex("[A-Za-z0-9._-]{1,80}"))) { "Ongeldig taaktype" }
        try {
            jdbc.update(
                "insert into agent_run(run_id, product_slug, task_type, status) values (?, ?, ?, 'RUNNING')",
                request.runId,
                product.slug,
                request.taskType,
            )
        } catch (_: DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Run-ID bestaat al")
        }
        return requireRun(product.slug, request.runId)
    }

    @GetMapping
    fun list(@RequestParam productSlug: String): List<AgentRunView> {
        val product = products.requireContext(productSlug)
        return jdbc.query(SELECT + " where product_slug = ? order by started_at desc", mapper, product.slug)
    }

    @GetMapping("/{runId}")
    fun get(@PathVariable runId: String, @RequestParam productSlug: String): AgentRunView = requireRun(productSlug, runId)

    private fun requireRun(productSlug: String, runId: String): AgentRunView {
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
