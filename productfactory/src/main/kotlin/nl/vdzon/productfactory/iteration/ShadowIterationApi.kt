package nl.vdzon.productfactory.iteration

import nl.vdzon.productfactory.bug.api.BugCatalog
import nl.vdzon.productfactory.contracts.ManualStartOrigin
import nl.vdzon.productfactory.contracts.ShadowIterationDecisionView
import nl.vdzon.productfactory.contracts.ShadowIterationStepView
import nl.vdzon.productfactory.contracts.ShadowIterationView
import nl.vdzon.productfactory.product.api.ProductCatalog
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
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
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

data class StartCycleRequest(
    val focus: String? = null,
    val manualStartOrigin: ManualStartOrigin? = null,
)
data class CancelIterationRequest(val reason: String? = null)
data class ShadowIterationStarted(val iterationId: String)
data class ShadowIterationArtifactView(
    val artifactType: String,
    val contentJson: String,
    val createdAt: Instant,
)
data class ResumeIterationContext(
    val research: String,
    val productOwner: String,
    val ux: String,
    val stories: String,
    val critic: String,
)

private fun Char.isManualStartTrimCharacter(): Boolean =
    this in '\u0009'..'\u000D' ||
        this == '\u0020' ||
        this == '\u0085' ||
        this == '\u00A0' ||
        this == '\u1680' ||
        this in '\u2000'..'\u200A' ||
        this == '\u2028' ||
        this == '\u2029' ||
        this == '\u202F' ||
        this == '\u205F' ||
        this == '\u3000' ||
        this == '\uFEFF'

/**
 * Expliciet gedeeld whitespacecontract voor eigenaarinput. JVM en Dart verschillen in hun
 * ingebouwde trimdefinitie; de Flutter-client bevat daarom letterlijk dezelfde Unicode-set.
 */
internal fun trimManualStartFocus(value: String): String {
    var start = 0
    var end = value.length
    while (start < end && value[start].isManualStartTrimCharacter()) start++
    while (end > start && value[end - 1].isManualStartTrimCharacter()) end--
    return value.substring(start, end)
}

@RestController
class ShadowIterationController(private val service: ShadowIterationService) {
    /**
     * Eén actie om een productcyclus te starten. Of de uitkomst daadwerkelijk naar de Software Factory
     * doorgezet kan worden, hangt uitsluitend af van de developmentMode-instelling van het product
     * (autonoom = kan doorgezet worden, anders = blijft intern) — niet van een aparte keuze hier.
     */
    @PostMapping("/api/products/{slug}/cycles")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun start(@PathVariable slug: String, @RequestBody request: StartCycleRequest): ShadowIterationView =
        service.startManualCycle(
            slug,
            request.focus,
            request.manualStartOrigin
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Herkomst is verplicht voor een handmatige start"),
        )

    @GetMapping("/api/shadow-iterations")
    fun list(@RequestParam productSlug: String): List<ShadowIterationView> = service.list(productSlug)

    @GetMapping("/api/shadow-iterations/{id}")
    fun get(@PathVariable id: String, @RequestParam productSlug: String): ShadowIterationView = service.require(productSlug, id)

    @GetMapping("/api/shadow-iterations/{id}/steps")
    fun steps(@PathVariable id: String, @RequestParam productSlug: String): List<ShadowIterationStepView> =
        service.steps(productSlug, id)

    @GetMapping("/api/shadow-iterations/{id}/artifacts")
    fun artifacts(@PathVariable id: String, @RequestParam productSlug: String): List<ShadowIterationArtifactView> =
        service.artifacts(productSlug, id)

    /**
     * Markeert een QUEUED/RUNNING iteratie als FAILED zodat een nieuwe cyclus voor dit product kan
     * starten. Stopt geen achtergrondthread die nog met de agentworker bezig is; een eventuele late
     * afronding daarvan wordt genegeerd door de write-once-guard op de terminale status (zie
     * ShadowIterationRepository.markAccepted/markReviewed/markFailed).
     */
    @PostMapping("/api/shadow-iterations/{id}/cancel")
    fun cancel(@PathVariable id: String, @RequestParam productSlug: String, @RequestBody(required = false) request: CancelIterationRequest?): ShadowIterationView =
        service.cancel(productSlug, id, request?.reason)

    @PostMapping("/api/shadow-iterations/{id}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun resume(@PathVariable id: String, @RequestParam productSlug: String): ShadowIterationView =
        service.resume(productSlug, id)
}

@Service
class ShadowIterationService(
    private val repository: ShadowIterationRepository,
    private val products: ProductCatalog,
    private val bugs: BugCatalog,
    private val events: ApplicationEventPublisher,
) {
    companion object {
        const val AUTONOMOUS_DEFAULT_FOCUS =
            "Bepaal autonoom de belangrijkste nog onbeantwoorde productvraag op basis van missie, bestaand dossier en eerdere iteraties."
        const val MAX_OWNER_FOCUS_LENGTH = 300
    }

    /** Modus wordt afgeleid van de productinstelling, niet gekozen door de aanroeper. */
    @Transactional
    fun startManualCycle(
        productSlug: String,
        requestedFocus: String?,
        manualStartOrigin: ManualStartOrigin,
    ): ShadowIterationView {
        val focus = when (manualStartOrigin) {
            ManualStartOrigin.AUTONOMOUS_DEFAULT -> {
                if (requestedFocus != AUTONOMOUS_DEFAULT_FOCUS) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Opdracht past niet bij de gekozen herkomst")
                }
                AUTONOMOUS_DEFAULT_FOCUS
            }
            ManualStartOrigin.OWNER_INPUT -> {
                val trimmed = requestedFocus?.let(::trimManualStartFocus).orEmpty()
                if (trimmed.length !in 1..MAX_OWNER_FOCUS_LENGTH) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Eigen onderzoeksvraag moet na trimmen 1 tot en met 300 tekens bevatten",
                    )
                }
                trimmed
            }
        }
        return createCycle(productSlug, focus, manualStartOrigin)
    }

    /** Automatische starts behouden hun bestaande focusgedrag en krijgen geen handmatige provenance. */
    @Transactional
    fun startCycle(productSlug: String, requestedFocus: String?): ShadowIterationView {
        val focus = requestedFocus?.trim()?.ifBlank { null } ?: AUTONOMOUS_DEFAULT_FOCUS
        require(focus.length <= 1000) { "Focus mag maximaal 1000 tekens bevatten" }
        return createCycle(productSlug, focus, null)
    }

    private fun createCycle(
        productSlug: String,
        focus: String,
        manualStartOrigin: ManualStartOrigin?,
    ): ShadowIterationView {
        val product = products.requireActive(productSlug)
        if (product.workspaceOwnership != "product-factory") {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Productcycli vereisen workspace-eigenaarschap product-factory")
        }
        repository.lockProduct(product.slug)
        if (repository.hasActive(product.slug)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Er loopt al een productcyclus voor dit product")
        }
        val importantWork = bugs.list(product.slug).firstOrNull {
            it.priority in setOf("P0", "P1") && it.status in setOf("IN_PROGRESS", "READY_FOR_VERIFICATION")
        }
        if (importantWork != null) {
            val reason = if (importantWork.status == "READY_FOR_VERIFICATION") {
                "wacht op een testsessie ter verificatie"
            } else {
                "wordt al opgelost"
            }
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "BUG-${importantWork.id} (${importantWork.priority}) $reason; nieuwe functionaliteit blijft geblokkeerd",
            )
        }
        val mode = if (product.developmentMode == "autonomous") "autonomous" else "shadow"
        val iteration = repository.create(product.slug, focus, mode, manualStartOrigin = manualStartOrigin)
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

    fun artifacts(productSlug: String, id: String): List<ShadowIterationArtifactView> {
        require(productSlug, id)
        return repository.artifacts(productSlug, id)
    }

    @Transactional
    fun cancel(productSlug: String, id: String, reason: String?): ShadowIterationView {
        val iteration = require(productSlug, id)
        if (iteration.status !in setOf("QUEUED", "RUNNING")) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Iteratie is al afgerond (${iteration.status})")
        }
        val cancelled = repository.markManuallyCancelled(
            id,
            reason?.trim()?.ifBlank { null } ?: "Handmatig geannuleerd",
        )
        if (!cancelled) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Iteratie is ondertussen al afgerond")
        }
        return require(productSlug, id)
    }

    @Transactional
    fun resume(productSlug: String, id: String): ShadowIterationView {
        val source = require(productSlug, id)
        if (!isResumableIteration(source.status, source.outcomeReason)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Alleen een revisie of herstelbare technische leveringsfout kan worden hervat",
            )
        }
        val product = products.requireActive(productSlug)
        if (product.workspaceOwnership != "product-factory") {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Productcycli vereisen workspace-eigenaarschap product-factory")
        }
        if (repository.hasActive(product.slug)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Er loopt al een productcyclus voor dit product")
        }
        repository.resumeContext(id) // controleer vóór het aanmaken dat alle herbruikbare artefacten bestaan
        val iteration = repository.create(
            product.slug,
            "Hervat iteratie ${source.sequenceNumber}: ${source.focus}",
            source.mode,
            resumeFromIterationId = source.id,
        )
        events.publishEvent(ShadowIterationStarted(iteration.id))
        return iteration
    }
}

internal fun isResumableIteration(status: String, outcomeReason: String?): Boolean =
    status == "NEEDS_REVISION" ||
        (status == "FAILED" && outcomeReason in setOf("DELIVERY_DEPENDENCY_UNRESOLVED", "NO_DELIVERABLE_CANDIDATE"))

@Repository
class ShadowIterationRepository(private val jdbc: JdbcTemplate) {
    /** Row lock voor de maximaal-een-garantie van starts binnen de omringende servicetransactie. */
    fun lockProduct(productSlug: String) {
        jdbc.queryForObject(
            "select slug from product_definition where slug = ? for update",
            String::class.java,
            productSlug,
        )
    }

    fun hasActive(productSlug: String): Boolean = (jdbc.queryForObject(
        "select count(*) from shadow_iteration where product_slug = ? and status in ('QUEUED', 'RUNNING')",
        Long::class.java,
        productSlug,
    ) ?: 0) > 0

    /**
     * Faalt weeskind-iteraties: rijen die onmogelijk nog legitiem kunnen lopen (zie
     * OrphanedIterationReconciler voor waarom zulke rijen ontstaan). Twee gevallen, elk met een eigen,
     * zo scherp mogelijk gekozen drempel in plaats van één brede veiligheidsmarge:
     *
     * 1. Een `shadow_iteration_step` die nog op RUNNING staat terwijl zijn eigen rol-timeout (plus wat
     *    marge) allang verstreken is. Zo'n stap kán niet meer legitiem lopen: was het uitvoerende
     *    thread nog in leven, dan had nl.vdzon.productfactory.agentruntime.api.HttpAgentDispatcher
     *    zichzelf allang op FAILED gezet via zijn eigen timeout. Dit vangt een weeskind dus al
     *    binnen zijn eigen rol-timeout op — voor de meeste rollen ruim binnen een kwartier, voor
     *    RESEARCHER binnen iets meer dan een uur — in plaats van pas na een losse, veel langere
     *    veiligheidsmarge.
     * 2. Een iteratie die nog op QUEUED staat zonder dat er ooit een stap voor is gestart: de
     *    async-listener die een cyclus oppikt (zie ShadowIterationRunner) is dan nooit afgevuurd of is
     *    meteen na het committen van de starttransactie gestorven. [queuedGrace] is bewust klein (de
     *    listener reageert normaal binnen milliseconden na de commit).
     */
    fun failOrphaned(reason: String, queuedGrace: Duration = Duration.ofMinutes(10)): List<String> {
        val researcherCutoff = Timestamp.from(Instant.now().minusSeconds(ShadowIterationEngine.RESEARCHER_TIMEOUT_SECONDS + TIMEOUT_GRACE_SECONDS))
        val otherRoleCutoff = Timestamp.from(Instant.now().minusSeconds(ShadowIterationEngine.ROLE_TIMEOUT_SECONDS + TIMEOUT_GRACE_SECONDS))
        val stuckSteps = jdbc.query(
            """select distinct iteration_id from shadow_iteration_step
                where status = 'RUNNING'
                  and ((role = 'RESEARCHER' and started_at < ?) or (role <> 'RESEARCHER' and started_at < ?))""".trimIndent(),
            { row, _ -> row.getString("iteration_id") },
            researcherCutoff,
            otherRoleCutoff,
        )
        val stuckQueued = jdbc.query(
            "select id from shadow_iteration where status = 'QUEUED' and created_at < ?",
            { row, _ -> row.getString("id") },
            Timestamp.from(Instant.now().minus(queuedGrace)),
        )
        val orphaned = (stuckSteps + stuckQueued).distinct()
        if (orphaned.isEmpty()) return orphaned
        val truncated = reason.take(MAX_ERROR_CHARS)
        orphaned.forEach { id ->
            jdbc.update(
                "update shadow_iteration set status = 'FAILED', current_agent_role = null, error_message = ?, completed_at = current_timestamp where id = ? and status in ('QUEUED', 'RUNNING')",
                truncated,
                id,
            )
            jdbc.update(
                "update shadow_iteration_step set status = 'FAILED', error_message = ?, completed_at = current_timestamp where iteration_id = ? and status = 'RUNNING'",
                truncated,
                id,
            )
        }
        return orphaned
    }

    fun create(
        productSlug: String,
        focus: String,
        mode: String = "shadow",
        resumeFromIterationId: String? = null,
        manualStartOrigin: ManualStartOrigin? = null,
    ): ShadowIterationView {
        val sequence = (jdbc.queryForObject(
            "select coalesce(max(sequence_number), 0) + 1 from shadow_iteration where product_slug = ?",
            Int::class.java,
            productSlug,
        ) ?: 1)
        val id = "shadow-$productSlug-${sequence.toString().padStart(4, '0')}"
        jdbc.update(
            """insert into shadow_iteration(
                id, product_slug, sequence_number, focus, mode, status,
                resume_from_iteration_id, manual_start_origin
            ) values (?, ?, ?, ?, ?, 'QUEUED', ?, ?)""".trimIndent(),
            id,
            productSlug,
            sequence,
            focus,
            mode,
            resumeFromIterationId,
            manualStartOrigin?.name,
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

    fun artifacts(productSlug: String, iterationId: String): List<ShadowIterationArtifactView> = jdbc.query(
        """select artifact_type, content_json, created_at from shadow_iteration_artifact
            where product_slug = ? and iteration_id = ? order by created_at, artifact_type""".trimIndent(),
        { row, _ ->
            ShadowIterationArtifactView(
                row.getString("artifact_type"),
                row.getString("content_json"),
                row.getTimestamp("created_at").toInstant(),
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

    fun resumeContext(iterationId: String): ResumeIterationContext {
        fun latest(role: String): String {
            val artifacts = jdbc.query(
                "select artifact_type, content_json from shadow_iteration_artifact where iteration_id = ? and (artifact_type = ? or artifact_type like ?) order by created_at, artifact_type",
                { row, _ -> row.getString("artifact_type") to row.getString("content_json") },
                iterationId,
                role,
                "$role-%",
            )
            return artifacts.maxByOrNull { (type, _) -> type.substringAfterLast('-', "1").toIntOrNull() ?: 1 }?.second
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Cyclus mist het herbruikbare artefact $role")
        }
        return ResumeIterationContext(latest("researcher"), latest("product_owner"), latest("ux_designer"), latest("story_writer"), latest("critic"))
    }

    fun existingCandidateContext(productSlug: String): String = jdbc.query(
        "select id, title, description, status from story_candidate where product_slug = ? order by id desc limit 20",
        { row, _ ->
            val status = row.getString("status")
            val reference = if (status == "PUBLISHED") "story:${row.getLong("id")}" else "candidate:${row.getLong("id")} (niet als dependsOn gebruiken)"
            "$reference | ${row.getString("title")} | ${row.getString("description")} | $status"
        },
        productSlug,
    ).joinToString("\n").ifBlank { "Geen bestaande kandidaten." }

    /** Alleen reeds gepubliceerde stories van hetzelfde product mogen een cross-batch afhankelijkheid vervullen. */
    fun publishedCandidateIds(productSlug: String): Set<Long> = jdbc.queryForList(
        "select id from story_candidate where product_slug = ? and status = 'PUBLISHED'",
        Long::class.java,
        productSlug,
    ).toSet()

    fun previousIterationContext(productSlug: String, currentIterationId: String): String = jdbc.query(
        """select i.sequence_number, a.artifact_type, a.content_json
            from shadow_iteration i join shadow_iteration_artifact a on a.iteration_id = i.id
            where i.product_slug = ? and i.id <> ? and i.status in ('ACCEPTED', 'NO_CHANGE', 'NEEDS_REVISION', 'REJECTED')
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

    /** @return het door de database toegekende, blijvende `story_candidate.id` van de opgeslagen kandidaat. */
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
        themeId: String? = null,
        bugId: Long? = null,
    ): Long {
        val status = when {
            duplicateOfId != null -> "DUPLICATE"
            criticStatus == "ACCEPT" -> "INTERNAL"
            else -> "REJECTED"
        }
        val keyHolder = GeneratedKeyHolder()
        jdbc.update(
            { connection ->
                val statement = connection.prepareStatement(
                    """insert into story_candidate(
                        product_slug, title, description, status, iteration_id, fingerprint,
                        acceptance_criteria, critic_status, critic_reason, duplicate_of_id, theme_id, bug_id
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent(),
                    arrayOf("id"),
                )
                statement.setString(1, productSlug)
                statement.setString(2, title)
                statement.setString(3, description)
                statement.setString(4, status)
                statement.setString(5, iterationId)
                statement.setString(6, fingerprint)
                statement.setString(7, acceptanceCriteria)
                statement.setString(8, criticStatus)
                statement.setString(9, criticReason)
                if (duplicateOfId != null) statement.setLong(10, duplicateOfId) else statement.setNull(10, java.sql.Types.BIGINT)
                statement.setString(11, themeId)
                if (bugId != null) statement.setLong(12, bugId) else statement.setNull(12, java.sql.Types.BIGINT)
                statement
            },
            keyHolder,
        )
        return keyHolder.key!!.toLong()
    }

    /** Generieke opslag voor een niet-agentgebonden dossierartefact (bv. de dependsOn-resolutiemapping). */
    fun saveArtifact(iterationId: String, productSlug: String, artifactType: String, contentJson: String) {
        jdbc.update(
            "insert into shadow_iteration_artifact(iteration_id, product_slug, artifact_type, content_json) values (?, ?, ?, ?)",
            iterationId,
            productSlug,
            artifactType,
            contentJson,
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
        val updated = jdbc.update(
            """update shadow_iteration set status = 'ACCEPTED', current_agent_role = null, critic_verdict = ?,
                workspace_run_id = ?, workspace_pull_request_url = ?, workspace_commit_sha = ?,
                completed_at = current_timestamp where id = ? and status not in ($TERMINAL_STATUSES_SQL)""".trimIndent(),
            criticVerdict,
            workspaceRunId,
            pullRequestUrl,
            commitSha,
            iterationId,
        )
        if (updated == 0) {
            log.warn("Genegeerde schrijfpoging: iteratie {} staat al in een terminale staat, ACCEPTED-conclusie wordt niet overschreven", iterationId)
        }
    }

    fun recordOutcome(iterationId: String, generatedCount: Int, acceptedCount: Int, revisionRounds: Int, reason: String) {
        jdbc.update(
            "update shadow_iteration set generated_candidate_count = ?, accepted_candidate_count = ?, revision_rounds = ?, outcome_reason = ? where id = ? and status not in ($TERMINAL_STATUSES_SQL)",
            generatedCount,
            acceptedCount,
            revisionRounds,
            reason,
            iterationId,
        )
    }

    fun markReviewed(iterationId: String, verdict: String, status: String) {
        require(status in setOf("NEEDS_REVISION", "REJECTED"))
        val updated = jdbc.update(
            "update shadow_iteration set status = ?, current_agent_role = null, critic_verdict = ?, completed_at = current_timestamp where id = ? and status not in ($TERMINAL_STATUSES_SQL)",
            status,
            verdict,
            iterationId,
        )
        if (updated == 0) {
            log.warn("Genegeerde schrijfpoging: iteratie {} staat al in een terminale staat, $status-conclusie wordt niet overschreven", iterationId)
        }
    }

    /** Een exact reeds geleverd resultaat is nuttige bevestiging, geen mislukte cyclus. */
    fun markNoChange(iterationId: String, verdict: String) {
        val updated = jdbc.update(
            "update shadow_iteration set status = 'NO_CHANGE', current_agent_role = null, critic_verdict = ?, completed_at = current_timestamp where id = ? and status not in ($TERMINAL_STATUSES_SQL)",
            verdict,
            iterationId,
        )
        if (updated == 0) {
            log.warn("Genegeerde schrijfpoging: iteratie {} staat al in een terminale staat, NO_CHANGE-conclusie wordt niet overschreven", iterationId)
        }
    }

    fun markFailed(iterationId: String, error: String) {
        val updated = jdbc.update(
            "update shadow_iteration set status = 'FAILED', current_agent_role = null, error_message = ?, outcome_reason = 'TECHNICAL_FAILURE', completed_at = current_timestamp where id = ? and status not in ($TERMINAL_STATUSES_SQL)",
            error.take(MAX_ERROR_CHARS),
            iterationId,
        )
        if (updated == 0) {
            log.warn("Genegeerde schrijfpoging: iteratie {} staat al in een terminale staat, FAILED-conclusie wordt niet overschreven", iterationId)
        }
    }

    /**
     * Schrijft de terminale overgang en de menselijke provenance met exact dezelfde tijdswaarde.
     * De omringende servicetransactie rolt de statuswijziging terug als het unieke beslisrecord niet
     * kan worden opgeslagen; een gelijktijdig afgeronde iteratie raakt geen van beide tabellen.
     */
    fun markManuallyCancelled(iterationId: String, error: String): Boolean {
        val decidedAt = Timestamp.from(Instant.now())
        val updated = jdbc.update(
            """update shadow_iteration set status = 'FAILED', current_agent_role = null,
                error_message = ?, outcome_reason = 'TECHNICAL_FAILURE', completed_at = ?
                where id = ? and status in ('QUEUED', 'RUNNING')""".trimIndent(),
            error.take(MAX_ERROR_CHARS),
            decidedAt,
            iterationId,
        )
        if (updated == 0) return false
        jdbc.update(
            """insert into shadow_iteration_decision(iteration_id, actor_type, mechanism, reason_code, decided_at)
                values (?, 'HUMAN', 'MANUAL_CANCELLATION', 'MANUALLY_CANCELLED', ?)""".trimIndent(),
            iterationId,
            decidedAt,
        )
        return true
    }

    /** Een geaccepteerde richting die pas in de leveringslaag uitvalt is een technische fout, geen afwijzing. */
    fun markDeliveryFailed(iterationId: String, criticVerdict: String, reason: String, error: String) {
        val updated = jdbc.update(
            "update shadow_iteration set status = 'FAILED', current_agent_role = null, critic_verdict = ?, error_message = ?, outcome_reason = ?, completed_at = current_timestamp where id = ? and status not in ($TERMINAL_STATUSES_SQL)",
            criticVerdict,
            error.take(MAX_ERROR_CHARS),
            reason,
            iterationId,
        )
        if (updated == 0) {
            log.warn("Genegeerde schrijfpoging: iteratie {} staat al in een terminale staat, leveringsfout wordt niet overschreven", iterationId)
        }
    }

    /** Voor dummies geschreven samenvatting van de cyclus (onderzoek, productbesluit, resulterende stories). */
    fun saveSummary(iterationId: String, summary: String) {
        jdbc.update("update shadow_iteration set summary = ? where id = ?", summary, iterationId)
    }

    private val mapper = { row: java.sql.ResultSet, _: Int ->
        val decisionIterationId = row.getString("decision_iteration_id")
        ShadowIterationView(
            row.getString("id"), row.getString("product_slug"), row.getInt("sequence_number"), row.getString("focus"),
            row.getString("mode"), row.getString("status"), row.getString("current_agent_role"), row.getString("critic_verdict"), row.getInt("candidate_count"),
            row.getString("workspace_run_id"), row.getString("workspace_pull_request_url"), row.getString("workspace_commit_sha"),
            row.getString("error_message"), row.getString("summary"), row.getTimestamp("created_at").toInstant(), row.getTimestamp("started_at")?.toInstant(),
            row.getTimestamp("completed_at")?.toInstant(),
            row.getInt("accepted_candidate_count"), row.getInt("revision_rounds"), row.getString("outcome_reason"),
            row.getString("resume_from_iteration_id"),
            decisionIterationId?.let {
                ShadowIterationDecisionView(
                    it,
                    row.getString("decision_actor_type"),
                    row.getString("decision_mechanism"),
                    row.getString("decision_reason_code"),
                    row.getTimestamp("decision_decided_at").toInstant(),
                )
            },
            row.getString("manual_start_origin")?.let(ManualStartOrigin::valueOf),
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ShadowIterationRepository::class.java)
        private const val MAX_AGENT_OUTPUT_CHARS = 200_000
        private const val MAX_ERROR_CHARS = 4_000

        /** Zelfde marge als AgentDispatchPort.BRIDGE_GRACE_SECONDS: geeft de poll-lus de tijd om zijn eigen timeout af te ronden voordat wij die stap als weeskind bestempelen. */
        private const val TIMEOUT_GRACE_SECONDS = 30L
        private const val VIEW_SELECT = """select i.*,
            greatest(i.generated_candidate_count, (select count(*) from story_candidate s where s.iteration_id = i.id)) as candidate_count,
            d.iteration_id as decision_iteration_id, d.actor_type as decision_actor_type,
            d.mechanism as decision_mechanism, d.reason_code as decision_reason_code,
            d.decided_at as decision_decided_at
            from shadow_iteration i
            left join shadow_iteration_decision d on d.iteration_id = i.id"""

        /** Terminale statussen: de conclusie (status/critic_verdict) van een iteratie mag hierna niet meer overschreven worden. */
        private const val TERMINAL_STATUSES_SQL = "'ACCEPTED', 'NO_CHANGE', 'NEEDS_REVISION', 'REJECTED', 'FAILED'"
    }
}
