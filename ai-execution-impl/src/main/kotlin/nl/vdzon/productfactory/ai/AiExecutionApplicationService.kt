package nl.vdzon.productfactory.ai

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

@Service
class AiExecutionApplicationService(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val settings: AiSettingsApplicationService,
    private val runtime: AgentRuntimeClient,
    @Value("\${PF_ENVIRONMENT:local}") environment: String,
) : AiExecutionService, AiExecutionQueryService {
    private val environment = environment.lowercase()

    @Transactional
    override fun updateAiJobConfiguration(command: UpdateAiJobConfigurationCommand) = settings.updateAiJobConfiguration(command)

    override fun getAiJobConfiguration(jobKey: AiJobKey) = settings.getAiJobConfiguration(jobKey)
    override fun getAiJobConfigurations() = settings.getAiJobConfigurations()

    @Transactional
    override fun requestAiTask(command: RequestAiTaskCommand): AiTaskId {
        validateTask(command)
        val fingerprint = fingerprint(command)
        existingTask(command.idempotencyKey)?.let { (id, savedFingerprint) ->
            if (fingerprint != savedFingerprint) throw IdempotencyConflict("Idempotentiesleutel is al voor een andere AI-taak gebruikt.")
            return AiTaskId(id)
        }
        val configuration = settings.getAiJobConfiguration(command.jobKey)
        if (!configuration.enabled) throw InvalidCommand("AI-job ${command.jobKey.value} is uitgeschakeld.")
        if (configuration.provider != command.provider || configuration.model != command.model || configuration.version != command.configurationVersion) {
            throw VersionConflict("De bevroren AI-jobconfiguratie is niet meer actueel.")
        }
        requireTrustedRole(command.productId, command.agentRole)
        val environmentKeys = command.productId?.let { selectedEnvironmentKeys(it, command.agentRole) }.orEmpty()
        val runtimeIdempotencyKey = "pf-${command.idempotencyKey}".take(160)
        val runtimeRequest = RuntimeCreateJobRequest(
            idempotencyKey = runtimeIdempotencyKey,
            provider = command.provider.name,
            model = command.model,
            prompt = command.prompt,
            responseSchema = command.responseSchema?.let(mapper::readTree),
            repositorySnapshot = command.repository?.let { RuntimeRepositorySnapshot(it.publicGitUrl, it.commitSha) },
            environmentKeys = environmentKeys,
            attachments = command.attachments.map { RuntimeAttachmentRequest(it.filename, it.mediaType, Base64.getEncoder().encodeToString(it.content)) },
            executionTimeoutSeconds = command.executionTimeout.seconds.toInt(),
        )
        val id = UUID.randomUUID().toString()
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_ai_task(id,idempotency_key,request_fingerprint,job_key,product_id,requester_capability,requester_session_id,agent_role,provider,model,configuration_version,prompt_template_version,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            id, command.idempotencyKey, fingerprint, command.jobKey.value, command.productId?.value,
            command.requesterCapability, command.requesterSessionId?.value, command.agentRole, command.provider.name,
            command.model, command.configurationVersion, command.promptTemplateVersion, AiTaskStatus.PENDING_SUBMISSION.name, now, now,
        )
        jdbc.update(
            "INSERT INTO pf_ai_runtime_outbox(task_id,runtime_idempotency_key,frozen_request_json,created_at,updated_at) VALUES (?,?,?,?,?)",
            id, runtimeIdempotencyKey, mapper.writeValueAsString(runtimeRequest), now, now,
        )
        return AiTaskId(id)
    }

    @Transactional
    override fun cancelAiTask(taskId: AiTaskId, reason: String) {
        if (reason.isBlank() || reason.length > 500) throw InvalidCommand("Een begrensde annuleringsreden is verplicht.")
        val task = getAiTask(taskId)
        if (task.status in TERMINAL_STATUSES) return
        val now = clock.instant()
        jdbc.update("UPDATE pf_ai_task SET cancel_requested=TRUE,cancel_reason=?,updated_at=? WHERE id=?", reason.trim(), now, taskId.value)
        task.runtimeJobId?.let { runtimeId ->
            runCatching { runtime.cancelJob(runtimeId) }.onSuccess { applyRuntimeStatus(taskId.value, it) }
        }
    }

    @Transactional
    override fun refreshEnvironmentCatalog(command: RefreshEnvironmentCatalogCommand): List<EnvironmentKeyDetails> {
        validateProjectPrefix(command.projectPrefix)
        val now = clock.instant()
        jdbc.update(
            "UPDATE pf_environment_key_catalog SET available=FALSE,matching_online_workers=0,refreshed_at=? WHERE project_prefix=?",
            now, command.projectPrefix,
        )
        runtime.listEnvironmentKeys(command.projectPrefix).forEach { key ->
            val updated = jdbc.update(
                "UPDATE pf_environment_key_catalog SET project_prefix=?,available=?,matching_online_workers=?,last_seen_at=?,refreshed_at=? WHERE name=?",
                key.projectPrefix, key.available, key.matchingOnlineWorkers, key.lastSeenAt, now, key.name,
            )
            if (updated == 0) jdbc.update(
                "INSERT INTO pf_environment_key_catalog(name,project_prefix,available,matching_online_workers,last_seen_at,refreshed_at) VALUES (?,?,?,?,?,?)",
                key.name, key.projectPrefix, key.available, key.matchingOnlineWorkers, key.lastSeenAt, now,
            )
        }
        return getEnvironmentCatalog(command.projectPrefix)
    }

    @Transactional
    override fun setProductEnvironmentKey(command: SetProductEnvironmentKeyCommand): ProductEnvironmentKeyDetails {
        validateActor(command.actor)
        replayEnvironmentCommand(command.idempotencyKey, fingerprint(command))?.let { return productEnvironmentKey(command.productId, command.name) }
        catalogKey(command.name)
        val currentVersion = jdbc.query(
            "SELECT version FROM pf_product_environment_key WHERE product_id=? AND name=?",
            { rs, _ -> rs.getLong(1) }, command.productId.value, command.name,
        ).singleOrNull() ?: 0L
        if (currentVersion != command.expectedVersion) throw VersionConflict("De productkeyconfiguratie is intussen gewijzigd.")
        val nextVersion = currentVersion + 1
        val now = clock.instant()
        if (currentVersion == 0L) {
            jdbc.update(
                "INSERT INTO pf_product_environment_key(product_id,name,active,version,updated_at,actor_type,actor_id) VALUES (?,?,?,?,?,?,?)",
                command.productId.value, command.name, command.active, nextVersion, now, command.actor.type.name, command.actor.id,
            )
        } else {
            jdbc.update(
                "UPDATE pf_product_environment_key SET active=?,version=?,updated_at=?,actor_type=?,actor_id=? WHERE product_id=? AND name=?",
                command.active, nextVersion, now, command.actor.type.name, command.actor.id, command.productId.value, command.name,
            )
            if (!command.active) jdbc.update("DELETE FROM pf_agent_environment_grant WHERE product_id=? AND name=?", command.productId.value, command.name)
        }
        recordEnvironmentCommand(command.idempotencyKey, fingerprint(command), command.productId, command.name, now)
        return productEnvironmentKey(command.productId, command.name)
    }

    @Transactional
    override fun setAgentEnvironmentGrant(command: SetAgentEnvironmentGrantCommand): ProductEnvironmentKeyDetails {
        validateActor(command.actor)
        val commandFingerprint = fingerprint(command)
        replayEnvironmentCommand(command.idempotencyKey, commandFingerprint)?.let { return productEnvironmentKey(command.productId, command.name) }
        val key = productEnvironmentKey(command.productId, command.name)
        if (!key.active) throw InvalidCommand("Alleen een actieve productkey kan aan een agentrol worden toegekend.")
        requireTrustedRole(command.productId, command.agentRole)
        val now = clock.instant()
        if (command.granted) {
            val exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pf_agent_environment_grant WHERE product_id=? AND name=? AND agent_role=?",
                Long::class.java, command.productId.value, command.name, command.agentRole,
            ) ?: 0
            if (exists == 0L) jdbc.update(
                "INSERT INTO pf_agent_environment_grant(product_id,name,agent_role,granted_at,actor_type,actor_id) VALUES (?,?,?,?,?,?)",
                command.productId.value, command.name, command.agentRole, now, command.actor.type.name, command.actor.id,
            )
        } else {
            jdbc.update("DELETE FROM pf_agent_environment_grant WHERE product_id=? AND name=? AND agent_role=?", command.productId.value, command.name, command.agentRole)
        }
        recordEnvironmentCommand(command.idempotencyKey, commandFingerprint, command.productId, command.name, now)
        return productEnvironmentKey(command.productId, command.name)
    }

    @Transactional(readOnly = true)
    override fun getAiTask(taskId: AiTaskId): AiTaskDetails = taskRows("WHERE id=?", taskId.value).singleOrNull()
        ?: throw AggregateNotFound("AI-taak ${taskId.value} bestaat niet.")

    @Transactional(readOnly = true)
    override fun getAiTaskResult(taskId: AiTaskId): AiTaskResultDetails? = jdbc.query(
        "SELECT status,response_json,artifacts_json,error_code,safe_message,completed_at FROM pf_ai_task_result WHERE task_id=?",
        { rs, _ ->
            AiTaskResultDetails(
                taskId, AiTaskResultStatus.valueOf(rs.getString("status")), rs.getString("response_json"),
                mapper.readValue(rs.getString("artifacts_json"), object : TypeReference<List<ArtifactReference>>() {}),
                rs.getString("error_code"), rs.getString("safe_message"), rs.getTimestamp("completed_at").toInstant(),
            )
        }, taskId.value,
    ).singleOrNull()

    @Transactional(readOnly = true)
    override fun findAiTasks(filter: AiTaskFilter): List<AiTaskDetails> = taskRows().filter { task ->
        (filter.productId == null || task.productId == filter.productId) &&
            (filter.statuses.isEmpty() || task.status in filter.statuses) &&
            (filter.jobKey == null || task.jobKey == filter.jobKey) &&
            (filter.timeRange.from == null || !task.createdAt.isBefore(filter.timeRange.from)) &&
            (filter.timeRange.until == null || task.createdAt.isBefore(filter.timeRange.until))
    }

    @Transactional(readOnly = true)
    override fun getEnvironmentCatalog(projectPrefix: String): List<EnvironmentKeyDetails> {
        validateProjectPrefix(projectPrefix)
        return jdbc.query(
            """SELECT c.name,c.project_prefix,c.available,c.matching_online_workers,c.last_seen_at,
                       CASE WHEN p.name IS NULL THEN FALSE ELSE TRUE END known_to_product
                FROM pf_environment_key_catalog c
                LEFT JOIN pf_product_environment_key p ON p.name=c.name
                WHERE c.project_prefix=? GROUP BY c.name,c.project_prefix,c.available,c.matching_online_workers,c.last_seen_at,p.name ORDER BY c.name""".trimIndent(),
            { rs, _ -> EnvironmentKeyDetails(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4), rs.getTimestamp(5).toInstant(), rs.getBoolean(6)) },
            projectPrefix,
        )
    }

    @Transactional(readOnly = true)
    override fun getProductEnvironmentKeys(productId: ProductId): List<ProductEnvironmentKeyDetails> = jdbc.query(
        """SELECT p.name,c.project_prefix,p.active,c.available,c.matching_online_workers,c.last_seen_at,p.version
            FROM pf_product_environment_key p JOIN pf_environment_key_catalog c ON c.name=p.name
            WHERE p.product_id=? ORDER BY p.name""".trimIndent(),
        { rs, _ -> productEnvironmentDetails(productId, rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4), rs.getInt(5), rs.getTimestamp(6).toInstant(), rs.getLong(7)) },
        productId.value,
    )

    fun dispatchPending(limit: Int = 20, retryDelaySeconds: Long = 10) {
        val ids = jdbc.query(
            "SELECT task_id FROM pf_ai_runtime_outbox WHERE dispatched_at IS NULL AND (retry_after IS NULL OR retry_after<=?) ORDER BY created_at",
            { rs, _ -> rs.getString(1) }, clock.instant(),
        ).take(limit)
        ids.forEach { dispatchOne(it, retryDelaySeconds) }
    }

    fun reconcileActive(limit: Int = 100) {
        jdbc.query(
            "SELECT id FROM pf_ai_task WHERE runtime_job_id IS NOT NULL AND status NOT IN ('SUCCEEDED','FAILED','CANCELLED') ORDER BY updated_at",
            { rs, _ -> rs.getString(1) },
        ).take(limit).forEach(::reconcileOne)
    }

    fun downloadArtifact(taskId: AiTaskId, artifactId: String): ByteArray {
        val task = getAiTask(taskId)
        val result = getAiTaskResult(taskId) ?: throw AggregateNotFound("AI-taak heeft geen resultaat.")
        val artifact = result.artifacts.singleOrNull { it.uri.endsWith("/$artifactId") }
            ?: throw AggregateNotFound("Artifact bestaat niet voor deze AI-taak.")
        return runtime.downloadArtifact(task.runtimeJobId ?: throw AggregateNotFound("Runtimecorrelatie ontbreekt."), artifact.uri.substringAfterLast('/'))
    }

    @Transactional
    fun deleteAllOwnedExecutionData() {
        jdbc.update("DELETE FROM pf_meeting_ai_work")
        jdbc.update("DELETE FROM pf_ai_task_result")
        jdbc.update("DELETE FROM pf_ai_runtime_outbox")
        jdbc.update("DELETE FROM pf_ai_task")
        jdbc.update("DELETE FROM pf_agent_environment_grant")
        jdbc.update("DELETE FROM pf_product_environment_key")
        jdbc.update("DELETE FROM pf_environment_access_command")
        jdbc.update("DELETE FROM pf_environment_key_catalog")
    }

    private fun dispatchOne(taskId: String, retryDelaySeconds: Long) {
        val json = jdbc.queryForObject("SELECT frozen_request_json FROM pf_ai_runtime_outbox WHERE task_id=?", String::class.java, taskId) ?: return
        val request = mapper.readValue(json, RuntimeCreateJobRequest::class.java)
        try {
            val view = runtime.createJob(request)
            val now = clock.instant()
            jdbc.update("UPDATE pf_ai_task SET runtime_job_id=?,updated_at=? WHERE id=? AND (runtime_job_id IS NULL OR runtime_job_id=?)", view.id, now, taskId, view.id)
            jdbc.update("UPDATE pf_ai_runtime_outbox SET dispatched_at=?,last_error_code=NULL,last_error_message=NULL,retry_after=NULL,updated_at=? WHERE task_id=?", now, now, taskId)
            applyRuntimeStatus(taskId, view)
        } catch (error: RuntimeCallException) {
            val now = clock.instant()
            jdbc.update(
                "UPDATE pf_ai_runtime_outbox SET last_error_code=?,last_error_message=?,retry_after=?,updated_at=? WHERE task_id=?",
                error.code, error.safeMessage.take(1000), now.plusSeconds(retryDelaySeconds), now, taskId,
            )
            if (!error.responseMayHaveBeenLost && error.code !in RETRYABLE_CODES) failBeforeSubmission(taskId, error)
        }
    }

    private fun reconcileOne(taskId: String) {
        val task = getAiTask(AiTaskId(taskId))
        val runtimeId = task.runtimeJobId ?: return
        try {
            if (task.cancelReason != null) runtime.cancelJob(runtimeId)
            val view = runtime.getJob(runtimeId)
            applyRuntimeStatus(taskId, view)
            if (view.status == "SUCCEEDED") storeRuntimeResult(taskId, runtime.getResult(runtimeId))
            if (view.status == "FAILED" || view.status == "CANCELLED") storeTerminalFailure(taskId, view)
        } catch (_: RuntimeCallException) {
            // Durable projection remains unchanged; the next reconciliation retries safely.
        }
    }

    private fun applyRuntimeStatus(taskId: String, view: RuntimeJobView) {
        val status = runCatching { AiTaskStatus.valueOf(view.status) }.getOrElse { throw RuntimeCallException("RUNTIME_STATUS_UNKNOWN", "Agent Runtime gaf een onbekende status terug.") }
        jdbc.update(
            """UPDATE pf_ai_task SET runtime_job_id=?,status=?,runtime_phase=?,runtime_attempt_count=?,safe_progress_percent=?,safe_progress=?,error_code=?,safe_error_message=?,updated_at=? WHERE id=?""".trimIndent(),
            view.id, status.name, view.phase.take(120), view.attemptCount, view.progressPercent, view.progressMessage?.take(1000),
            view.errorCode?.take(160), view.errorMessage?.take(1000), clock.instant(), taskId,
        )
    }

    private fun storeRuntimeResult(taskId: String, result: RuntimeJobResult) {
        val exists = jdbc.queryForObject("SELECT COUNT(*) FROM pf_ai_task_result WHERE task_id=?", Long::class.java, taskId) ?: 0
        if (exists > 0) return
        val artifacts = result.artifacts.map { artifact ->
            ArtifactReference(artifact.filename, artifact.mimeType, "/api/ai/tasks/$taskId/artifacts/${artifact.id}")
        }
        jdbc.update(
            "INSERT INTO pf_ai_task_result(task_id,status,response_json,artifacts_json,completed_at) VALUES (?,?,?,?,?)",
            taskId, AiTaskResultStatus.SUCCEEDED.name, mapper.writeValueAsString(result.result), mapper.writeValueAsString(artifacts), result.completedAt,
        )
    }

    private fun storeTerminalFailure(taskId: String, view: RuntimeJobView) {
        val exists = jdbc.queryForObject("SELECT COUNT(*) FROM pf_ai_task_result WHERE task_id=?", Long::class.java, taskId) ?: 0
        if (exists > 0) return
        jdbc.update(
            "INSERT INTO pf_ai_task_result(task_id,status,response_json,artifacts_json,error_code,safe_message,completed_at) VALUES (?,?,?,?,?,?,?)",
            taskId, AiTaskResultStatus.FAILED.name, null, "[]", view.errorCode ?: view.status, view.errorMessage?.take(1000), clock.instant(),
        )
    }

    private fun failBeforeSubmission(taskId: String, error: RuntimeCallException) {
        val now = clock.instant()
        jdbc.update("UPDATE pf_ai_task SET status='FAILED',error_code=?,safe_error_message=?,updated_at=? WHERE id=?", error.code, error.safeMessage, now, taskId)
        val exists = jdbc.queryForObject("SELECT COUNT(*) FROM pf_ai_task_result WHERE task_id=?", Long::class.java, taskId) ?: 0
        if (exists == 0L) jdbc.update(
            "INSERT INTO pf_ai_task_result(task_id,status,artifacts_json,error_code,safe_message,completed_at) VALUES (?,?,?,?,?,?)",
            taskId, AiTaskResultStatus.FAILED.name, "[]", error.code, error.safeMessage, now,
        )
    }

    private fun validateTask(command: RequestAiTaskCommand) {
        if (command.idempotencyKey.isBlank() || command.idempotencyKey.length > 150) throw InvalidCommand("Ongeldige AI-taak-idempotentiesleutel.")
        if (command.requesterCapability.isBlank() || command.requesterCapability.length > 160) throw InvalidCommand("Ongeldige aanvragende capability.")
        if (command.agentRole.isBlank() || command.agentRole.length > 120) throw InvalidCommand("Een vertrouwde agentrol is verplicht.")
        if (command.prompt.isBlank() || command.prompt.length > 200_000) throw InvalidCommand("De complete AI-prompt is leeg of te groot.")
        if (command.promptTemplateVersion < 1) throw InvalidCommand("Een positieve prompttemplateversie is verplicht.")
        if (command.executionTimeout.seconds !in 30..86_400 || command.executionTimeout.nano != 0) throw InvalidCommand("Uitvoeringstime-out moet tussen 30 seconden en 24 uur liggen.")
        if (environment == "production" && command.provider == AiProvider.MOCKED) throw InvalidCommand("MOCKED AI-uitvoering is in productie niet toegestaan.")
        command.responseSchema?.let { runCatching { mapper.readTree(it) }.getOrElse { throw InvalidCommand("Responseschema is geen geldige JSON.") } }
        command.repository?.let {
            if (!it.publicGitUrl.startsWith("https://") || !SHA.matches(it.commitSha)) throw InvalidCommand("Repositorysnapshot moet HTTPS en een exacte commit-SHA gebruiken.")
        }
        if (command.attachments.size > 10 || command.attachments.sumOf { it.content.size } > 10 * 1024 * 1024) throw InvalidCommand("Te veel of te grote inputattachments.")
        command.attachments.forEach {
            if (!FILENAME.matches(it.filename) || it.content.size > 2 * 1024 * 1024 || it.mediaType !in ALLOWED_MEDIA_TYPES) {
                throw InvalidCommand("Inputattachment ${it.filename} heeft een onveilige naam, type of grootte.")
            }
        }
    }

    private fun requireTrustedRole(productId: ProductId?, agentRole: String) {
        if (productId == null) {
            if (agentRole !in AiSettingsApplicationService.TRUSTED_JOBS.mapNotNull { job -> JOB_ROLE[job.jobKey.value] }) throw InvalidCommand("Onbekende vertrouwde agentrol.")
            return
        }
        val exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pf_agent_role_definition r WHERE r.role_key=? AND r.active=TRUE AND EXISTS (SELECT 1 FROM pf_product p WHERE p.product_id=?)",
            Long::class.java, agentRole, productId.value,
        ) ?: 0
        if (exists == 0L) throw InvalidCommand("Agentrol is niet actief voor dit product.")
    }

    private fun selectedEnvironmentKeys(productId: ProductId, agentRole: String): List<String> = jdbc.query(
        """SELECT p.name FROM pf_product_environment_key p
            JOIN pf_agent_environment_grant g ON g.product_id=p.product_id AND g.name=p.name
            JOIN pf_environment_key_catalog c ON c.name=p.name
            WHERE p.product_id=? AND g.agent_role=? AND p.active=TRUE ORDER BY p.name""".trimIndent(),
        { rs, _ -> rs.getString(1) }, productId.value, agentRole,
    )

    private fun taskRows(where: String = "", vararg args: Any): List<AiTaskDetails> = jdbc.query(
        """SELECT id,job_key,product_id,requester_capability,requester_session_id,agent_role,provider,model,configuration_version,prompt_template_version,status,runtime_job_id,runtime_phase,runtime_attempt_count,safe_progress_percent,safe_progress,error_code,cancel_reason,created_at,updated_at
            FROM pf_ai_task $where ORDER BY created_at DESC""".trimIndent(),
        { rs, _ ->
            AiTaskDetails(
                AiTaskId(rs.getString("id")), AiJobKey(rs.getString("job_key")), rs.getString("product_id")?.let(::ProductId),
                rs.getString("requester_capability"), AiProvider.valueOf(rs.getString("provider")), rs.getString("model"),
                rs.getLong("configuration_version"), rs.getLong("prompt_template_version"), rs.getString("requester_session_id")?.let(::ProcessSessionId),
                rs.getString("agent_role"), AiTaskStatus.valueOf(rs.getString("status")), rs.getString("runtime_job_id"), rs.getString("runtime_phase"),
                rs.getInt("runtime_attempt_count"), rs.getObject("safe_progress_percent")?.let { rs.getInt("safe_progress_percent") },
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getString("safe_progress"),
                rs.getString("error_code"), rs.getString("cancel_reason"),
            )
        }, *args,
    )

    private fun existingTask(key: String) = jdbc.query(
        "SELECT id,request_fingerprint FROM pf_ai_task WHERE idempotency_key=?", { rs, _ -> rs.getString(1) to rs.getString(2) }, key,
    ).singleOrNull()

    private fun productEnvironmentKey(productId: ProductId, name: String): ProductEnvironmentKeyDetails = jdbc.query(
        """SELECT c.project_prefix,p.active,c.available,c.matching_online_workers,c.last_seen_at,p.version
            FROM pf_product_environment_key p JOIN pf_environment_key_catalog c ON c.name=p.name
            WHERE p.product_id=? AND p.name=?""".trimIndent(),
        { rs, _ -> productEnvironmentDetails(productId, name, rs.getString(1), rs.getBoolean(2), rs.getBoolean(3), rs.getInt(4), rs.getTimestamp(5).toInstant(), rs.getLong(6)) },
        productId.value, name,
    ).singleOrNull() ?: throw AggregateNotFound("Productenvironmentkey bestaat niet.")

    private fun productEnvironmentDetails(productId: ProductId, name: String, prefix: String, active: Boolean, available: Boolean, workers: Int, lastSeen: Instant, version: Long) =
        ProductEnvironmentKeyDetails(productId, name, prefix, active, available, workers, lastSeen, version, jdbc.query(
            "SELECT agent_role FROM pf_agent_environment_grant WHERE product_id=? AND name=? ORDER BY agent_role",
            { rs, _ -> rs.getString(1) }, productId.value, name,
        ).toSet())

    private fun catalogKey(name: String): EnvironmentKeyDetails = jdbc.query(
        "SELECT name,project_prefix,available,matching_online_workers,last_seen_at FROM pf_environment_key_catalog WHERE name=?",
        { rs, _ -> EnvironmentKeyDetails(rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4), rs.getTimestamp(5).toInstant()) }, name,
    ).singleOrNull() ?: throw InvalidCommand("Environmentkey is niet bekend in de Runtime-catalogus.")

    private fun replayEnvironmentCommand(key: String, valueFingerprint: String): Boolean? {
        if (key.isBlank() || key.length > 200) throw InvalidCommand("Ongeldige idempotentiesleutel.")
        val saved = jdbc.query("SELECT request_fingerprint FROM pf_environment_access_command WHERE idempotency_key=?", { rs, _ -> rs.getString(1) }, key).singleOrNull() ?: return null
        if (saved != valueFingerprint) throw IdempotencyConflict("Idempotentiesleutel is al voor andere agenttoegang gebruikt.")
        return true
    }

    private fun recordEnvironmentCommand(key: String, valueFingerprint: String, productId: ProductId, name: String, now: Instant) {
        jdbc.update(
            "INSERT INTO pf_environment_access_command(idempotency_key,request_fingerprint,product_id,name,applied_at) VALUES (?,?,?,?,?)",
            key, valueFingerprint, productId.value, name, now,
        )
    }

    private fun validateActor(actor: ActorReference) {
        if (actor.id.isBlank() || actor.type !in setOf(ActorType.STAKEHOLDER, ActorType.SYSTEM)) throw InvalidCommand("Alleen de Stakeholder of vertrouwde systeemcode mag agenttoegang wijzigen.")
    }

    private fun validateProjectPrefix(prefix: String) {
        if (!PROJECT_PREFIX.matches(prefix)) throw InvalidCommand("Ongeldig Runtime-projectprefix.")
    }

    private fun fingerprint(value: Any) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)))

    companion object {
        private val TERMINAL_STATUSES = setOf(AiTaskStatus.SUCCEEDED, AiTaskStatus.FAILED, AiTaskStatus.CANCELLED)
        private val RETRYABLE_CODES = setOf("RUNTIME_NOT_CONFIGURED", "RUNTIME_SUBMISSION_FAILED", "RUNTIME_EMPTY_RESPONSE")
        private val SHA = Regex("[0-9a-fA-F]{40}")
        private val FILENAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,254}")
        private val PROJECT_PREFIX = Regex("[A-Z][A-Z0-9_]*")
        private val ALLOWED_MEDIA_TYPES = setOf("image/png", "image/jpeg", "image/webp", "application/pdf", "text/plain", "application/json")
        private val JOB_ROLE = mapOf(
            "MEETING.CONVERSE" to "MEETING_AGENT",
            "MEETING.SUMMARIZE" to "MEETING_MINUTES_AGENT",
            "PRODUCT_DESIGN.CREATE_EPIC" to "PRODUCT_DESIGNER_MVP",
            "PLANNING.SLICE_EPIC" to "PLANNER_MVP",
            "QUALITY.VERIFY_EPIC" to "TESTER_MVP",
        )
    }
}

@Component
class AgentRuntimeCoordinator(
    private val service: AiExecutionApplicationService,
    @Value("\${PF_AI_RUNTIME_SCHEDULING_ENABLED:false}") private val enabled: Boolean,
) {
    @Scheduled(fixedDelayString = "\${PF_AI_RUNTIME_DISPATCH_DELAY_MS:1000}")
    fun dispatch() {
        if (enabled) service.dispatchPending()
    }

    @Scheduled(fixedDelayString = "\${PF_AI_RUNTIME_RECONCILE_DELAY_MS:2000}")
    fun reconcile() {
        if (enabled) service.reconcileActive()
    }
}
