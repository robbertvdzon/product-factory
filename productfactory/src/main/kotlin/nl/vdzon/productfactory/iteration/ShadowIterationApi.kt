package nl.vdzon.productfactory.iteration

import nl.vdzon.productfactory.contracts.ShadowIterationStepView
import nl.vdzon.productfactory.contracts.ShadowIterationView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class StartShadowIterationRequest(val focus: String? = null)
data class ShadowIterationStarted(val iterationId: String)

@RestController
class ShadowIterationController(private val service: ShadowIterationService) {
    @PostMapping("/api/products/{slug}/shadow-iterations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(@PathVariable slug: String, @RequestBody(required = false) request: StartShadowIterationRequest?): ShadowIterationView =
        service.startShadow(slug, request?.focus)

    @PostMapping("/api/products/{slug}/autonomous-cycles")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun startAutonomous(@PathVariable slug: String, @RequestBody(required = false) request: StartShadowIterationRequest?): ShadowIterationView =
        service.startAutonomous(slug, request?.focus)

    @GetMapping("/api/shadow-iterations")
    fun list(@RequestParam productSlug: String): List<ShadowIterationView> = service.list(productSlug)

    @GetMapping("/api/shadow-iterations/{id}")
    fun get(@PathVariable id: String, @RequestParam productSlug: String): ShadowIterationView = service.require(productSlug, id)

    @GetMapping("/api/shadow-iterations/{id}/steps")
    fun steps(@PathVariable id: String, @RequestParam productSlug: String): List<ShadowIterationStepView> =
        service.steps(productSlug, id)
}

@Service
class ShadowIterationService(
    private val repository: ShadowIterationRepository,
    private val products: ProductCatalog,
    private val events: ApplicationEventPublisher,
) {
    @Transactional
    fun startShadow(productSlug: String, requestedFocus: String?): ShadowIterationView = doStart(productSlug, requestedFocus, "shadow")

    @Transactional
    fun startAutonomous(productSlug: String, requestedFocus: String?): ShadowIterationView = doStart(productSlug, requestedFocus, "autonomous")

    private fun doStart(productSlug: String, requestedFocus: String?, mode: String): ShadowIterationView {
        require(mode in setOf("shadow", "autonomous"))
        val product = products.requireActive(productSlug)
        if (mode == "autonomous") products.requireStoryPublication(productSlug)
        if (product.workspaceOwnership != "product-factory") {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Shadow-iteraties vereisen workspace-eigenaarschap product-factory")
        }
        if (repository.hasActive(product.slug)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Er loopt al een shadow-iteratie voor dit product")
        }
        val focus = requestedFocus?.trim()?.ifBlank { null }
            ?: "Bepaal autonoom de belangrijkste nog onbeantwoorde productvraag op basis van missie, bestaand dossier en eerdere iteraties."
        require(focus.length <= 1000) { "Focus mag maximaal 1000 tekens bevatten" }
        val iteration = repository.create(product.slug, focus, mode)
        events.publishEvent(ShadowIterationStarted(iteration.id))
        return iteration
    }

    fun list(productSlug: String): List<ShadowIterationView> {
        val product = products.requireContext(productSlug)
        return repository.list(product.slug)
    }

    fun require(productSlug: String, id: String): ShadowIterationView {
        val product = products.requireContext(productSlug)
        return repository.require(product.slug, id)
    }

    fun steps(productSlug: String, id: String): List<ShadowIterationStepView> {
        require(productSlug, id)
        return repository.steps(productSlug, id)
    }
}

@Repository
class ShadowIterationRepository(private val jdbc: JdbcTemplate) {
    fun hasActive(productSlug: String): Boolean = (jdbc.queryForObject(
        "select count(*) from shadow_iteration where product_slug = ? and status in ('QUEUED', 'RUNNING')",
        Long::class.java,
        productSlug,
    ) ?: 0) > 0

    fun create(productSlug: String, focus: String, mode: String = "shadow"): ShadowIterationView {
        val sequence = (jdbc.queryForObject(
            "select coalesce(max(sequence_number), 0) + 1 from shadow_iteration where product_slug = ?",
            Int::class.java,
            productSlug,
        ) ?: 1)
        val id = "shadow-$productSlug-${sequence.toString().padStart(4, '0')}"
        jdbc.update(
            "insert into shadow_iteration(id, product_slug, sequence_number, focus, mode, status) values (?, ?, ?, ?, ?, 'QUEUED')",
            id,
            productSlug,
            sequence,
            focus,
            mode,
        )
        return require(productSlug, id)
    }

    fun list(productSlug: String): List<ShadowIterationView> = jdbc.query(
        VIEW_SELECT + " where i.product_slug = ? order by i.sequence_number desc",
        mapper,
        productSlug,
    )

    fun require(productSlug: String, id: String): ShadowIterationView = jdbc.query(
        VIEW_SELECT + " where i.product_slug = ? and i.id = ?",
        mapper,
        productSlug,
        id,
    ).singleOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende shadow-iteratie voor dit product")

    fun requireById(id: String): ShadowIterationView = jdbc.query(VIEW_SELECT + " where i.id = ?", mapper, id).singleOrNull()
        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Onbekende shadow-iteratie")

    fun steps(productSlug: String, iterationId: String): List<ShadowIterationStepView> = jdbc.query(
        """select role, attempt, run_id, status, error_message, started_at, completed_at
            from shadow_iteration_step where product_slug = ? and iteration_id = ? order by id""".trimIndent(),
        { row, _ ->
            ShadowIterationStepView(
                row.getString("role"), row.getInt("attempt"), row.getString("run_id"), row.getString("status"),
                row.getString("error_message"), row.getTimestamp("started_at").toInstant(),
                row.getTimestamp("completed_at")?.toInstant(),
            )
        },
        productSlug,
        iterationId,
    )

    fun markRunning(iterationId: String) {
        jdbc.update("update shadow_iteration set status = 'RUNNING', started_at = current_timestamp where id = ? and status = 'QUEUED'", iterationId)
    }

    fun startStep(iterationId: String, productSlug: String, role: String, attempt: Int, runId: String) {
        jdbc.update("update shadow_iteration set current_agent_role = ? where id = ?", role, iterationId)
        jdbc.update(
            "insert into shadow_iteration_step(iteration_id, product_slug, role, attempt, run_id, status) values (?, ?, ?, ?, ?, 'RUNNING')",
            iterationId,
            productSlug,
            role,
            attempt,
            runId,
        )
    }

    fun completeStep(iterationId: String, role: String, attempt: Int, outputJson: String) {
        require(outputJson.length <= MAX_AGENT_OUTPUT_CHARS) { "Agentoutput is te groot" }
        val artifactType = role.lowercase() + if (attempt == 1) "" else "-$attempt"
        jdbc.update(
            "update shadow_iteration_step set status = 'COMPLETED', output_json = ?, completed_at = current_timestamp where iteration_id = ? and role = ? and attempt = ?",
            outputJson,
            iterationId,
            role,
            attempt,
        )
        jdbc.update(
            "insert into shadow_iteration_artifact(iteration_id, product_slug, artifact_type, content_json) select id, product_slug, ?, ? from shadow_iteration where id = ?",
            artifactType,
            outputJson,
            iterationId,
        )
    }

    fun failStep(iterationId: String, role: String, attempt: Int, error: String) {
        jdbc.update(
            "update shadow_iteration_step set status = 'FAILED', error_message = ?, completed_at = current_timestamp where iteration_id = ? and role = ? and attempt = ?",
            error.take(MAX_ERROR_CHARS),
            iterationId,
            role,
            attempt,
        )
    }

    fun artifact(iterationId: String, type: String): String? = jdbc.queryForObject(
        "select content_json from shadow_iteration_artifact where iteration_id = ? and artifact_type = ?",
        String::class.java,
        iterationId,
        type.lowercase(),
    )

    fun existingCandidateContext(productSlug: String): String = jdbc.query(
        "select id, title, description, status from story_candidate where product_slug = ? order by id desc limit 20",
        { row, _ -> "${row.getLong(1)} | ${row.getString(2)} | ${row.getString(3)} | ${row.getString(4)}" },
        productSlug,
    ).joinToString("\n").ifBlank { "Geen bestaande kandidaten." }

    fun previousIterationContext(productSlug: String, currentIterationId: String): String = jdbc.query(
        """select i.sequence_number, a.artifact_type, a.content_json
            from shadow_iteration i join shadow_iteration_artifact a on a.iteration_id = i.id
            where i.product_slug = ? and i.id <> ? and i.status in ('ACCEPTED', 'NEEDS_REVISION', 'REJECTED')
            order by i.sequence_number desc, a.artifact_type limit 8""".trimIndent(),
        { row, _ -> "Iteratie ${row.getInt(1)} / ${row.getString(2)}: ${row.getString(3).take(3000)}" },
        productSlug,
        currentIterationId,
    ).joinToString("\n\n").take(12_000).ifBlank { "Nog geen eerdere beoordeelde productiteraties." }

    fun findDuplicate(productSlug: String, fingerprint: String): Long? = jdbc.queryForObject(
        """select max(c.id) from story_candidate c
            join shadow_iteration i on i.id = c.iteration_id
            where c.product_slug = ? and c.fingerprint = ?
              and (c.status = 'PUBLISHED' or (c.status = 'INTERNAL' and i.status = 'ACCEPTED'))""".trimIndent(),
        Long::class.java,
        productSlug,
        fingerprint,
    )

    fun saveCandidate(
        iterationId: String,
        productSlug: String,
        title: String,
        description: String,
        acceptanceCriteria: String,
        fingerprint: String,
        criticStatus: String,
        criticReason: String,
        duplicateOfId: Long?,
    ) {
        val status = when {
            duplicateOfId != null -> "DUPLICATE"
            criticStatus == "ACCEPT" -> "INTERNAL"
            else -> "REJECTED"
        }
        jdbc.update(
            """insert into story_candidate(
                product_slug, title, description, status, iteration_id, fingerprint,
                acceptance_criteria, critic_status, critic_reason, duplicate_of_id
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
            productSlug,
            title,
            description,
            status,
            iterationId,
            fingerprint,
            acceptanceCriteria,
            criticStatus,
            criticReason,
            duplicateOfId,
        )
    }

    fun saveSource(iterationId: String, productSlug: String, url: String, consultedOn: java.time.LocalDate, rights: String, rationale: String) {
        jdbc.update(
            "insert into research_source(iteration_id, product_slug, url, consulted_on, rights_indication, rationale) values (?, ?, ?, ?, ?, ?)",
            iterationId,
            productSlug,
            url,
            consultedOn,
            rights,
            rationale,
        )
    }

    fun saveKnowledge(
        iterationId: String,
        productSlug: String,
        researchTitle: String,
        researchContent: String,
        firstSourceUrl: String,
        decisionTitle: String,
        decisionContent: String,
        uxTitle: String,
        uxContent: String,
    ) {
        jdbc.update(
            "insert into product_research(product_slug, title, content, source_url) values (?, ?, ?, ?)",
            productSlug, researchTitle, researchContent, firstSourceUrl,
        )
        jdbc.update(
            "insert into product_decision(product_slug, title, content) values (?, ?, ?)",
            productSlug, decisionTitle, decisionContent,
        )
        jdbc.update(
            "insert into product_ux(product_slug, iteration_id, title, content) values (?, ?, ?, ?)",
            productSlug, iterationId, uxTitle, uxContent,
        )
    }

    fun markAccepted(
        iterationId: String,
        criticVerdict: String,
        workspaceRunId: String,
        pullRequestUrl: String?,
        commitSha: String?,
    ) {
        jdbc.update(
            """update shadow_iteration set status = 'ACCEPTED', current_agent_role = null, critic_verdict = ?,
                workspace_run_id = ?, workspace_pull_request_url = ?, workspace_commit_sha = ?,
                completed_at = current_timestamp where id = ?""".trimIndent(),
            criticVerdict,
            workspaceRunId,
            pullRequestUrl,
            commitSha,
            iterationId,
        )
    }

    fun markReviewed(iterationId: String, verdict: String, status: String) {
        require(status in setOf("NEEDS_REVISION", "REJECTED"))
        jdbc.update(
            "update shadow_iteration set status = ?, current_agent_role = null, critic_verdict = ?, completed_at = current_timestamp where id = ?",
            status,
            verdict,
            iterationId,
        )
    }

    fun markFailed(iterationId: String, error: String) {
        jdbc.update(
            "update shadow_iteration set status = 'FAILED', current_agent_role = null, error_message = ?, completed_at = current_timestamp where id = ?",
            error.take(MAX_ERROR_CHARS),
            iterationId,
        )
    }

    private val mapper = { row: java.sql.ResultSet, _: Int ->
        ShadowIterationView(
            row.getString("id"), row.getString("product_slug"), row.getInt("sequence_number"), row.getString("focus"),
            row.getString("mode"), row.getString("status"), row.getString("current_agent_role"), row.getString("critic_verdict"), row.getInt("candidate_count"),
            row.getString("workspace_run_id"), row.getString("workspace_pull_request_url"), row.getString("workspace_commit_sha"),
            row.getString("error_message"), row.getTimestamp("created_at").toInstant(), row.getTimestamp("started_at")?.toInstant(),
            row.getTimestamp("completed_at")?.toInstant(),
        )
    }

    companion object {
        private const val MAX_AGENT_OUTPUT_CHARS = 200_000
        private const val MAX_ERROR_CHARS = 4_000
        private const val VIEW_SELECT = """select i.*,
            (select count(*) from story_candidate s where s.iteration_id = i.id) as candidate_count
            from shadow_iteration i"""
    }
}
