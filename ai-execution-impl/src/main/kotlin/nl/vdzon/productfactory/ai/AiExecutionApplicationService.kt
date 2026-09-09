package nl.vdzon.productfactory.ai

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.math.BigDecimal
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
    private val artifactStore: AiArtifactStore,
    private val schemaValidator: AiJsonSchemaValidator,
    private val transactions: TransactionTemplate,
    @Value("\${PF_ENVIRONMENT:local}") environment: String,
    @Value("\${PF_AGENT_RUNTIME_API_VERSION:v1}") apiVersion: String,
) : AiExecutionService, AiExecutionQueryService {
    private val environment = environment.lowercase()
    private val apiVersion = apiVersion.lowercase().also {
        require(it in setOf("v1", "v2")) { "PF_AGENT_RUNTIME_API_VERSION moet v1 of v2 zijn." }
    }
    private val coordinatorId = "pf-${UUID.randomUUID()}"

    @Transactional
    override fun updateAiJobConfiguration(command: UpdateAiJobConfigurationCommand) = settings.updateAiJobConfiguration(command)

    override fun getAiJobConfiguration(jobKey: AiJobKey) = settings.getAiJobConfiguration(jobKey)
    override fun getAiJobConfigurations() = settings.getAiJobConfigurations()

    @Transactional
    override fun retainAiArtifacts(command: RetainAiArtifactsCommand) {
        if (!DOMAIN_TYPE.matches(command.domainType) || command.domainId.isBlank() || command.domainId.length > 160 || command.domainVersion < 1) {
            throw InvalidCommand("Ongeldige domeinreferentie voor AI-artifacts.")
        }
        command.references.forEach { reference ->
            val match = LOCAL_ARTIFACT_URI.matchEntire(reference.uri)
                ?: throw InvalidCommand("Alleen duurzame Product Factory-artifacts kunnen worden gepubliceerd.")
            val artifactId = match.groupValues[2]
            val count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pf_ai_artifact WHERE id=? AND task_id=? AND logical_name=? AND mime_type=? AND artifact_status='READY'",
                Long::class.java, artifactId, match.groupValues[1], reference.name, reference.mediaType,
            ) ?: 0
            if (count == 0L && taskApiVersion(match.groupValues[1]) == "v1") return@forEach
            if (count != 1L) throw InvalidCommand("Gepubliceerd AI-artifact is niet duurzaam of wijkt af van het manifest.")
            val exists = jdbc.queryForObject(
                """SELECT COUNT(*) FROM pf_ai_artifact_domain_reference
                    WHERE artifact_id=? AND domain_type=? AND domain_id=? AND domain_version=? AND reference_name=?""".trimIndent(),
                Long::class.java, artifactId, command.domainType, command.domainId, command.domainVersion, reference.name,
            ) ?: 0
            if (exists == 0L) jdbc.update(
                """INSERT INTO pf_ai_artifact_domain_reference(artifact_id,domain_type,domain_id,domain_version,reference_name,created_at)
                    VALUES (?,?,?,?,?,?)""".trimIndent(),
                artifactId, command.domainType, command.domainId, command.domainVersion, reference.name, clock.instant(),
            )
        }
    }

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
        if (configuration.execution != command.execution || configuration.version != command.configurationVersion) {
            throw VersionConflict("De bevroren AI-jobconfiguratie is niet meer actueel.")
        }
        requireTrustedRole(command.productId, command.agentRole)
        val environmentKeys = command.productId?.let { selectedEnvironmentKeys(it, command.agentRole) }.orEmpty()
        val runtimeIdempotencyKey = "pf-${command.idempotencyKey}".take(160)
        val runtimeRequest = RuntimeCreateJobRequest(
            idempotencyKey = runtimeIdempotencyKey,
            provider = legacyProvider(command.execution),
            model = command.execution.model,
            prompt = command.prompt,
            responseSchema = mapper.readTree(command.responseSchema),
            repositorySnapshot = command.repository?.let { RuntimeRepositorySnapshot(it.publicGitUrl, it.commitSha) },
            environmentKeys = environmentKeys,
            attachments = command.attachments.map { RuntimeAttachmentRequest(it.filename, it.mediaType, Base64.getEncoder().encodeToString(it.content)) },
            executionTimeoutSeconds = command.executionTimeout.seconds.toInt(),
        )
        val id = UUID.randomUUID().toString()
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_ai_task(id,idempotency_key,request_fingerprint,job_key,product_id,requester_capability,requester_session_id,agent_role,provider,vendor_id,model,execution_mode,configuration_version,prompt_template_version,status,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            id, command.idempotencyKey, fingerprint, command.jobKey.value, command.productId?.value,
            command.requesterCapability, command.requesterSessionId?.value, command.agentRole, legacyProvider(command.execution), command.execution.vendorId,
            command.execution.model, command.execution.mode.name, command.configurationVersion, command.promptTemplateVersion, AiTaskStatus.PENDING_SUBMISSION.name, now, now,
        )
        persistInput(id, 0, "prompt", "prompt.md", "text/markdown", AiInputRole.PROMPT, command.prompt.toByteArray(), now)
        command.attachments.forEachIndexed { index, attachment ->
            persistInput(id, index + 1, attachment.name, attachment.filename, attachment.mediaType, attachment.role, attachment.content, now)
        }
        jdbc.update(
            """INSERT INTO pf_ai_task_specification(task_id,instruction,response_schema,repository_url,repository_commit_sha,environment_keys_json,output_artifacts_json,execution_timeout_seconds,created_at)
                VALUES (?,?,?,?,?,?,?,?,?)""".trimIndent(),
            id, V2_INSTRUCTION, command.responseSchema, command.repository?.publicGitUrl, command.repository?.commitSha,
            mapper.writeValueAsString(environmentKeys), mapper.writeValueAsString(command.outputArtifacts), command.executionTimeout.seconds.toInt(), now,
        )
        jdbc.update(
            "INSERT INTO pf_ai_runtime_outbox(task_id,runtime_idempotency_key,frozen_request_json,api_version,request_fingerprint,dispatch_state,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
            id, runtimeIdempotencyKey, if (apiVersion == "v1") mapper.writeValueAsString(runtimeRequest) else null, apiVersion, fingerprint,
            if (apiVersion == "v1") "REQUEST_FROZEN" else "PENDING_UPLOAD", now, now,
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
            runCatching { if (taskApiVersion(taskId.value) == "v2") runtime.cancelV2Job(runtimeId) else runtime.cancelJob(runtimeId) }
                .onSuccess { applyRuntimeStatus(taskId.value, it) }
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
        val runtimeKeys = if (apiVersion == "v2") runtime.listV2EnvironmentKeys(command.projectPrefix) else runtime.listEnvironmentKeys(command.projectPrefix)
        runtimeKeys.forEach { key ->
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
    override fun refreshExecutionCatalog(command: RefreshExecutionCatalogCommand): List<ExecutionCatalogEntry> {
        if (command.taskType != "STRUCTURED_GENERATION") throw InvalidCommand("Onbekend Runtime-tasktype.")
        val now = clock.instant()
        if (apiVersion == "v2") {
            jdbc.update("UPDATE pf_ai_model_catalog SET available=FALSE,matching_online_workers=0,refreshed_at=?", now)
            runtime.listV2ExecutionOptions(command.taskType).forEach { entry ->
                val selection = entry.execution
                val updated = jdbc.update(
                    "UPDATE pf_ai_model_catalog SET available=?,matching_online_workers=?,last_seen_at=?,refreshed_at=? WHERE vendor_id=? AND model=? AND execution_mode=?",
                    entry.available, entry.matchingOnlineWorkers, entry.lastSeenAt, now, selection.vendorId, selection.model, selection.mode,
                )
                if (updated == 0) jdbc.update(
                    "INSERT INTO pf_ai_model_catalog(provider,vendor_id,model,execution_mode,available,matching_online_workers,last_seen_at,refreshed_at) VALUES (?,?,?,?,?,?,?,?)",
                    legacyProvider(AiExecutionSelection(selection.vendorId, selection.model, AiExecutionMode.valueOf(selection.mode))), selection.vendorId, selection.model,
                    selection.mode, entry.available, entry.matchingOnlineWorkers, entry.lastSeenAt, now,
                )
            }
            return getExecutionCatalog(command.taskType)
        }
        val selections = settings.getAiJobConfigurations().map { it.execution }.distinct()
        selections.filter { it.mode != AiExecutionMode.MOCK }.forEach { selection ->
            val provider = legacyProvider(selection)
            jdbc.update(
                "UPDATE pf_ai_model_catalog SET available=FALSE,matching_online_workers=0,refreshed_at=? WHERE vendor_id=? AND execution_mode=?",
                now, selection.vendorId, selection.mode.name,
            )
            runtime.listModels(provider).forEach { entry ->
                val updated = jdbc.update(
                    "UPDATE pf_ai_model_catalog SET available=?,matching_online_workers=?,last_seen_at=?,refreshed_at=? WHERE vendor_id=? AND model=? AND execution_mode=?",
                    entry.available, entry.matchingOnlineWorkers, entry.lastSeenAt, now, selection.vendorId, entry.model, selection.mode.name,
                )
                if (updated == 0) jdbc.update(
                    "INSERT INTO pf_ai_model_catalog(provider,vendor_id,model,execution_mode,available,matching_online_workers,last_seen_at,refreshed_at) VALUES (?,?,?,?,?,?,?,?)",
                    provider, selection.vendorId, entry.model, selection.mode.name, entry.available, entry.matchingOnlineWorkers, entry.lastSeenAt, now,
                )
            }
        }
        return getExecutionCatalog(command.taskType)
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
    override fun getExecutionCatalog(taskType: String): List<ExecutionCatalogEntry> {
        if (taskType != "STRUCTURED_GENERATION") throw InvalidCommand("Onbekend Runtime-tasktype.")
        val entries = jdbc.query(
            "SELECT vendor_id,model,execution_mode,available,matching_online_workers,last_seen_at FROM pf_ai_model_catalog ORDER BY vendor_id,model,execution_mode",
        ) { rs, _ ->
            ExecutionCatalogEntry(
                AiExecutionSelection(rs.getString(1), rs.getString(2), AiExecutionMode.valueOf(rs.getString(3))),
                setOf(taskType), rs.getBoolean(4), rs.getInt(5), rs.getTimestamp(6).toInstant(),
            )
        }
        if (environment == "production" || entries.any { it.execution.mode == AiExecutionMode.MOCK }) return entries
        return entries + ExecutionCatalogEntry(
            AiExecutionSelection("mock", "mock", AiExecutionMode.MOCK), setOf(taskType), true, 1, clock.instant(),
        )
    }

    @Transactional(readOnly = true)
    override fun getAiTaskEvents(taskId: AiTaskId): List<AiTaskEventDetails> {
        getAiTask(taskId)
        return jdbc.query(
            "SELECT event_sequence,event_type,safe_message,progress_percent,occurred_at FROM pf_ai_runtime_event WHERE task_id=? ORDER BY event_sequence",
            { rs, _ -> AiTaskEventDetails(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getObject(4)?.let { rs.getInt(4) }, rs.getTimestamp(5).toInstant(),
            ) }, taskId.value,
        )
    }

    @Transactional(readOnly = true)
    override fun getAiTaskUsage(taskId: AiTaskId): AiTaskUsageDetails? = jdbc.query(
        "SELECT task_type,vendor_id,model,execution_mode,attempt_count,usage_quality,usage_json,cost_json,captured_at FROM pf_ai_runtime_usage WHERE task_id=?",
        { rs, _ ->
            val usage = mapper.readTree(rs.getString("usage_json"))
            val costs = mapper.readTree(rs.getString("cost_json")).takeIf { it.isArray }?.map { cost ->
                AiCostDetails(cost.path("kind").asText(), cost.path("status").asText(), cost.path("amount").takeUnless { it.isMissingNode || it.isNull }?.asText(), cost.path("currency").takeUnless { it.isMissingNode || it.isNull }?.asText())
            }.orEmpty()
            AiTaskUsageDetails(
                taskId, rs.getString("task_type"),
                AiExecutionSelection(rs.getString("vendor_id"), rs.getString("model"), AiExecutionMode.valueOf(rs.getString("execution_mode"))),
                rs.getInt("attempt_count"), rs.getString("usage_quality"), usage.longOrNull("inputTokens"), usage.longOrNull("cachedInputTokens"),
                usage.longOrNull("outputTokens"), usage.longOrNull("reasoningTokens"), costs, rs.getTimestamp("captured_at").toInstant(),
            )
        }, taskId.value,
    ).singleOrNull()

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
            """SELECT t.id FROM pf_ai_task t WHERE t.runtime_job_id IS NOT NULL AND
                (t.status NOT IN ('SUCCEEDED','FAILED','CANCELLED') OR
                 (t.status='SUCCEEDED' AND NOT EXISTS (SELECT 1 FROM pf_ai_task_result r WHERE r.task_id=t.id)))
                ORDER BY t.updated_at""".trimIndent(),
            { rs, _ -> rs.getString(1) },
        ).take(limit).forEach(::reconcileOne)
    }

    override fun openAiTaskArtifact(taskId: AiTaskId, artifactId: String, offset: Long): AiArtifactContent {
        val task = getAiTask(taskId)
        val result = getAiTaskResult(taskId) ?: throw AggregateNotFound("AI-taak heeft geen resultaat.")
        val artifact = result.artifacts.singleOrNull { it.uri.endsWith("/$artifactId") }
            ?: throw AggregateNotFound("Artifact bestaat niet voor deze AI-taak.")
        if (taskApiVersion(taskId.value) == "v2") {
            return jdbc.query(
                "SELECT filename,mime_type,size_bytes,sha256,storage_key FROM pf_ai_artifact WHERE id=? AND task_id=? AND artifact_status='READY'",
                { rs, _ ->
                    val size = rs.getLong("size_bytes")
                    if (offset !in 0 until size) throw InvalidCommand("Artifactoffset valt buiten het bestand.")
                    AiArtifactContent(
                        rs.getString("filename"), rs.getString("mime_type"), size, rs.getString("sha256"), offset,
                        artifactStore.open(rs.getString("storage_key"), offset),
                    )
                }, artifactId, taskId.value,
            ).singleOrNull() ?: throw AggregateNotFound("Duurzaam artifact bestaat niet voor deze AI-taak.")
        }
        if (offset != 0L) throw InvalidCommand("Range-download is alleen beschikbaar voor duurzame artifacts.")
        val content = runtime.downloadArtifact(task.runtimeJobId ?: throw AggregateNotFound("Runtimecorrelatie ontbreekt."), artifactId)
        return AiArtifactContent(artifact.name, artifact.mediaType, content.size.toLong(), null, 0, ByteArrayInputStream(content))
    }

    @Transactional
    fun deleteAllOwnedExecutionData() {
        jdbc.update("DELETE FROM pf_meeting_ai_work")
        jdbc.update("DELETE FROM pf_ai_artifact_domain_reference")
        jdbc.update("DELETE FROM pf_ai_artifact")
        jdbc.update("DELETE FROM pf_ai_runtime_attempt_usage")
        jdbc.update("DELETE FROM pf_ai_runtime_usage")
        jdbc.update("DELETE FROM pf_ai_runtime_event")
        jdbc.update("DELETE FROM pf_ai_runtime_event_cursor")
        jdbc.update("DELETE FROM pf_ai_runtime_upload")
        jdbc.update("DELETE FROM pf_ai_task_specification")
        jdbc.update("DELETE FROM pf_ai_task_input")
        jdbc.update("DELETE FROM pf_ai_task_result")
        jdbc.update("DELETE FROM pf_ai_runtime_outbox")
        jdbc.update("DELETE FROM pf_ai_task")
        jdbc.update("DELETE FROM pf_agent_environment_grant")
        jdbc.update("DELETE FROM pf_product_environment_key")
        jdbc.update("DELETE FROM pf_environment_access_command")
        jdbc.update("DELETE FROM pf_environment_key_catalog")
    }

    private fun dispatchOne(taskId: String, retryDelaySeconds: Long) {
        val now = clock.instant()
        val claimed = jdbc.update(
            """UPDATE pf_ai_runtime_outbox SET claimed_by=?,claimed_until=?,updated_at=?
                WHERE task_id=? AND dispatched_at IS NULL AND (claimed_until IS NULL OR claimed_until<? OR claimed_by=?)""".trimIndent(),
            coordinatorId, now.plusSeconds(DISPATCH_CLAIM_SECONDS), now, taskId, now, coordinatorId,
        )
        if (claimed == 0) return
        try {
            if (isCancelledBeforeCreate(taskId)) {
                cancelBeforeCreate(taskId)
                return
            }
            when (taskApiVersion(taskId)) {
                "v2" -> dispatchV2(taskId)
                else -> dispatchV1(taskId)
            }
        } catch (error: RuntimeCallException) {
            if (taskApiVersion(taskId) == "v2" && error.code == "INPUT_OBJECT_NOT_READY") {
                releaseFrozenV2Request(taskId)
            }
            val failedAt = clock.instant()
            jdbc.update(
                "UPDATE pf_ai_runtime_outbox SET last_error_code=?,last_error_message=?,retry_after=?,updated_at=? WHERE task_id=?",
                error.code, error.safeMessage.take(1000), failedAt.plusSeconds(retryDelaySeconds), failedAt, taskId,
            )
            if (!error.responseMayHaveBeenLost && error.code !in RETRYABLE_CODES) failBeforeSubmission(taskId, error)
        } finally {
            jdbc.update(
                "UPDATE pf_ai_runtime_outbox SET claimed_by=NULL,claimed_until=NULL WHERE task_id=? AND claimed_by=?",
                taskId, coordinatorId,
            )
        }
    }

    private fun dispatchV1(taskId: String) {
        val json = frozenRequest(taskId) ?: throw RuntimeCallException("RUNTIME_REQUEST_MISSING", "De bevroren v1-aanvraag ontbreekt.")
        acceptRuntimeJob(taskId, runtime.createJob(mapper.readValue(json, RuntimeCreateJobRequest::class.java)))
    }

    private fun dispatchV2(taskId: String) {
        val frozen = frozenRequest(taskId)
        val request = if (frozen != null) {
            mapper.readValue(frozen, RuntimeV2CreateJobRequest::class.java)
        } else {
            val inputs = inputRows(taskId)
            if (inputs.isEmpty()) throw RuntimeCallException("RUNTIME_INPUT_MISSING", "De opgeslagen Runtime-invoer ontbreekt.")
            val objectIds = inputs.associate { input -> input.sequence to uploadInput(taskId, input) }
            val built = buildV2Request(taskId, inputs, objectIds)
            val json = mapper.writeValueAsString(built)
            jdbc.update(
                "UPDATE pf_ai_runtime_outbox SET frozen_request_json=?,dispatch_state='REQUEST_FROZEN',updated_at=? WHERE task_id=? AND frozen_request_json IS NULL",
                json, clock.instant(), taskId,
            )
            mapper.readValue(frozenRequest(taskId) ?: json, RuntimeV2CreateJobRequest::class.java)
        }
        acceptRuntimeJob(taskId, runtime.createV2Job(request))
        jdbc.update("UPDATE pf_ai_task_input SET content_bytes=NULL,content_cleared_at=? WHERE task_id=?", clock.instant(), taskId)
    }

    private fun uploadInput(taskId: String, input: InputRow): String {
        var upload = uploadRow(taskId, input.sequence)
        if (upload?.state == "READY" && upload.objectId != null) return upload.objectId
        if (upload == null || upload.uploadId == null || upload.expiresAt?.isBefore(clock.instant()) != false) {
            val created = runtime.createUpload(RuntimeCreateUploadRequest(input.filename, input.mimeType, input.sizeBytes, input.sha256))
            val updated = jdbc.update(
                """UPDATE pf_ai_runtime_upload SET upload_id=?,object_id=?,upload_url=?,chunk_size_bytes=?,confirmed_offset=?,upload_state='UPLOADING',expires_at=?,updated_at=?
                    WHERE task_id=? AND input_sequence=?""".trimIndent(),
                created.uploadId, created.objectId, created.uploadUrl, created.chunkSizeBytes.toInt(), created.offset, created.expiresAt,
                clock.instant(), taskId, input.sequence,
            )
            if (updated == 0) jdbc.update(
                """INSERT INTO pf_ai_runtime_upload(task_id,input_sequence,upload_id,object_id,upload_url,chunk_size_bytes,confirmed_offset,upload_state,expires_at,created_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,'UPLOADING',?,?,?)""".trimIndent(),
                taskId, input.sequence, created.uploadId, created.objectId, created.uploadUrl, created.chunkSizeBytes.toInt(), created.offset,
                created.expiresAt, clock.instant(), clock.instant(),
            )
            upload = uploadRow(taskId, input.sequence)
        }
        val active = upload ?: throw RuntimeCallException("RUNTIME_UPLOAD_STATE_MISSING", "De lokale uploadcorrelatie ontbreekt.")
        val content = input.content ?: throw RuntimeCallException("RUNTIME_INPUT_CONTENT_MISSING", "De lokale invoerbytes zijn niet meer beschikbaar.")
        val uploadUrl = active.uploadUrl ?: throw RuntimeCallException("RUNTIME_UPLOAD_URL_MISSING", "De upload-URL ontbreekt.")
        var offset = runtime.getUploadOffset(uploadUrl)
        if (offset !in 0..content.size.toLong()) throw RuntimeCallException("RUNTIME_UPLOAD_OFFSET_INVALID", "Agent Runtime gaf een ongeldige uploadoffset terug.")
        val chunkSize = (active.chunkSizeBytes ?: DEFAULT_UPLOAD_CHUNK).coerceIn(1, MAX_UPLOAD_CHUNK)
        while (offset < content.size) {
            val end = minOf(content.size, offset.toInt() + chunkSize)
            val next = runtime.patchUpload(uploadUrl, offset, content.copyOfRange(offset.toInt(), end))
            if (next <= offset || next > content.size) throw RuntimeCallException("RUNTIME_UPLOAD_OFFSET_INVALID", "Agent Runtime bevestigde een ongeldige uploadoffset.")
            offset = next
            jdbc.update(
                "UPDATE pf_ai_runtime_upload SET confirmed_offset=?,updated_at=? WHERE task_id=? AND input_sequence=?",
                offset, clock.instant(), taskId, input.sequence,
            )
            renewDispatchClaim(taskId)
        }
        val ready = runtime.completeUpload(active.uploadId ?: throw RuntimeCallException("RUNTIME_UPLOAD_ID_MISSING", "Het upload-ID ontbreekt."))
        if (ready.state != "READY" || ready.objectId != active.objectId || ready.sizeBytes != input.sizeBytes || ready.sha256 != input.sha256 || ready.mimeType != input.mimeType) {
            throw RuntimeCallException("RUNTIME_UPLOAD_VERIFICATION_FAILED", "Agent Runtime bevestigde het inputobject niet correct.")
        }
        jdbc.update(
            "UPDATE pf_ai_runtime_upload SET object_id=?,confirmed_offset=?,upload_state='READY',updated_at=? WHERE task_id=? AND input_sequence=?",
            ready.objectId, ready.sizeBytes, clock.instant(), taskId, input.sequence,
        )
        return ready.objectId
    }

    private fun buildV2Request(taskId: String, inputs: List<InputRow>, objectIds: Map<Int, String>): RuntimeV2CreateJobRequest {
        val task = getAiTask(AiTaskId(taskId))
        val specification = taskSpecification(taskId)
        val idempotencyKey = jdbc.queryForObject(
            "SELECT runtime_idempotency_key FROM pf_ai_runtime_outbox WHERE task_id=?", String::class.java, taskId,
        ) ?: throw RuntimeCallException("RUNTIME_IDEMPOTENCY_MISSING", "De Runtime-idempotentiesleutel ontbreekt.")
        return RuntimeV2CreateJobRequest(
            idempotencyKey = idempotencyKey,
            execution = RuntimeV2Execution(task.execution.vendorId, task.execution.model, task.execution.mode.name),
            input = RuntimeV2JobInput(specification.instruction, inputs.map { input ->
                RuntimeV2InputObjectRef(objectIds.getValue(input.sequence), input.name, input.role)
            }),
            output = RuntimeV2Output(mapper.readTree(specification.responseSchema), specification.outputArtifacts.map {
                RuntimeV2ArtifactDeclaration(it.name, it.required, it.mimeTypes, it.maxBytes)
            }),
            repositorySnapshot = specification.repositoryUrl?.let { RuntimeRepositorySnapshot(it, specification.repositoryCommitSha!!) },
            environmentKeys = specification.environmentKeys,
            executionTimeoutSeconds = specification.executionTimeoutSeconds,
        )
    }

    private fun acceptRuntimeJob(taskId: String, view: RuntimeJobView) {
        val acceptedAt = clock.instant()
        jdbc.update("UPDATE pf_ai_task SET runtime_job_id=?,updated_at=? WHERE id=? AND (runtime_job_id IS NULL OR runtime_job_id=?)", view.id, acceptedAt, taskId, view.id)
        jdbc.update(
            "UPDATE pf_ai_runtime_outbox SET dispatched_at=?,dispatch_state='DISPATCHED',last_error_code=NULL,last_error_message=NULL,retry_after=NULL,updated_at=? WHERE task_id=?",
            acceptedAt, acceptedAt, taskId,
        )
        applyRuntimeStatus(taskId, view)
    }

    private fun reconcileOne(taskId: String) {
        val task = getAiTask(AiTaskId(taskId))
        val runtimeId = task.runtimeJobId ?: return
        try {
            val v2 = taskApiVersion(taskId) == "v2"
            if (task.cancelReason != null) if (v2) runtime.cancelV2Job(runtimeId) else runtime.cancelJob(runtimeId)
            val view = if (v2) runtime.getV2Job(runtimeId) else runtime.getJob(runtimeId)
            applyRuntimeStatus(taskId, view)
            if (v2) storeRuntimeEvents(taskId, runtimeId)
            if (view.status == "SUCCEEDED") {
                if (v2) storeRuntimeV2Result(taskId, task, runtime.getV2Result(runtimeId)) else storeRuntimeResult(taskId, runtime.getResult(runtimeId))
            }
            if (view.status == "FAILED" || view.status == "CANCELLED") {
                if (v2) storeRuntimeAttempts(taskId, task, runtimeId, runtime.getV2Attempts(runtimeId))
                storeTerminalFailure(taskId, view)
            }
        } catch (error: RuntimeCallException) {
            if (error.code in LOCAL_RESULT_FATAL_CODES) failResultProjection(taskId, error)
            // Transient projection failures remain eligible for the next restart-safe reconciliation.
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

    private fun storeRuntimeV2Result(taskId: String, task: AiTaskDetails, result: RuntimeV2JobResult) {
        val exists = jdbc.queryForObject("SELECT COUNT(*) FROM pf_ai_task_result WHERE task_id=?", Long::class.java, taskId) ?: 0
        if (exists > 0) return
        val specification = taskSpecification(taskId)
        if (!schemaValidator.isValid(specification.responseSchema, result.result)) {
            throw RuntimeCallException("RUNTIME_RESULT_SCHEMA_INVALID", "Agent Runtime gaf een resultaat buiten het bevroren responseschema terug.")
        }
        val declarations = specification.outputArtifacts.associateBy { it.name }
        val duplicateName = result.artifacts.groupingBy { it.name }.eachCount().entries.firstOrNull { it.value > 1 }
        if (duplicateName != null) throw RuntimeCallException("RUNTIME_ARTIFACT_CONTRACT_INVALID", "Agent Runtime gaf een artifactnaam meer dan eenmaal terug.")
        val unknown = result.artifacts.firstOrNull { artifact ->
            val declaration = declarations[artifact.name]
            declaration == null || artifact.mimeType !in declaration.mimeTypes || artifact.sizeBytes > declaration.maxBytes || artifact.state != "READY" ||
                !SHA256.matches(artifact.sha256)
        }
        if (unknown != null) throw RuntimeCallException("RUNTIME_ARTIFACT_CONTRACT_INVALID", "Agent Runtime gaf een artifact buiten het bevroren outputcontract terug.")
        val missing = declarations.values.firstOrNull { it.required && result.artifacts.none { artifact -> artifact.name == it.name } }
        if (missing != null) throw RuntimeCallException("RUNTIME_REQUIRED_ARTIFACT_MISSING", "Agent Runtime gaf een verplicht artifact niet terug.")
        val artifacts = result.artifacts.map { artifact ->
            val localId = copyRuntimeV2Artifact(taskId, result.jobId, artifact)
            ArtifactReference(artifact.name, artifact.mimeType, "/api/ai/tasks/$taskId/artifacts/$localId")
        }
        transactions.executeWithoutResult {
            val alreadyStored = jdbc.queryForObject("SELECT COUNT(*) FROM pf_ai_task_result WHERE task_id=?", Long::class.java, taskId) ?: 0
            if (alreadyStored == 0L) {
                jdbc.update(
                    "INSERT INTO pf_ai_task_result(task_id,status,response_json,artifacts_json,completed_at) VALUES (?,?,?,?,?)",
                    taskId, AiTaskResultStatus.SUCCEEDED.name, mapper.writeValueAsString(result.result), mapper.writeValueAsString(artifacts), result.completedAt,
                )
                storeUsage(taskId, task, result.jobId, result.usageSummary)
            }
        }
    }

    private fun copyRuntimeV2Artifact(taskId: String, runtimeJobId: String, artifact: RuntimeV2ArtifactView): String {
        var row = artifactRow(taskId, artifact.name)
        if (row == null) {
            val id = UUID.randomUUID().toString()
            val storageKey = "$taskId/$id"
            val now = clock.instant()
            jdbc.update(
                """INSERT INTO pf_ai_artifact(id,task_id,runtime_job_id,runtime_object_id,logical_name,filename,mime_type,size_bytes,sha256,storage_key,artifact_status,created_at,retention_until)
                    VALUES (?,?,?,?,?,?,?,?,?,?, 'COPYING',?,?)""".trimIndent(),
                id, taskId, runtimeJobId, artifact.objectId, artifact.name, artifact.filename, artifact.mimeType,
                artifact.sizeBytes, artifact.sha256, storageKey, now, now.plusSeconds(TEMPORARY_ARTIFACT_RETENTION_SECONDS),
            )
            row = ArtifactRow(id, storageKey, "COPYING", artifact.objectId, artifact.sizeBytes, artifact.sha256)
        }
        if (row.runtimeObjectId != artifact.objectId || row.sizeBytes != artifact.sizeBytes || row.sha256 != artifact.sha256) {
            throw RuntimeCallException("RUNTIME_ARTIFACT_CHANGED", "Agent Runtime wijzigde een eerder waargenomen artifact.")
        }
        if (row.status == "READY") return row.id
        if (row.status == "FAILED") {
            jdbc.update("UPDATE pf_ai_artifact SET artifact_status='COPYING' WHERE id=? AND artifact_status='FAILED'", row.id)
        }
        try {
            var offset = artifactStore.partialSize(row.storageKey)
            if (offset > artifact.sizeBytes) {
                artifactStore.delete(row.storageKey)
                offset = 0
            }
            artifactStore.append(row.storageKey).use { output ->
                val copied = runtime.copyV2Artifact(artifact.downloadUrl, offset, artifact.sizeBytes, output)
                if (!copied.complete || copied.confirmedBytes != artifact.sizeBytes) {
                    throw RuntimeCallException("RUNTIME_ARTIFACT_INCOMPLETE", "De duurzame artifactkopie is nog niet compleet.", true)
                }
            }
            artifactStore.verifyAndPromote(row.storageKey, artifact.sizeBytes, artifact.sha256)
            jdbc.update(
                "UPDATE pf_ai_artifact SET artifact_status='READY',ready_at=? WHERE id=? AND artifact_status='COPYING'",
                clock.instant(), row.id,
            )
            return row.id
        } catch (error: RuntimeCallException) {
            throw error
        } catch (_: Exception) {
            jdbc.update("UPDATE pf_ai_artifact SET artifact_status='FAILED' WHERE id=?", row.id)
            throw RuntimeCallException("RUNTIME_ARTIFACT_COPY_FAILED", "Het Runtime-artifact kon niet duurzaam worden overgenomen.", true)
        }
    }

    private fun artifactRow(taskId: String, logicalName: String): ArtifactRow? = jdbc.query(
        "SELECT id,storage_key,artifact_status,runtime_object_id,size_bytes,sha256 FROM pf_ai_artifact WHERE task_id=? AND logical_name=?",
        { rs, _ -> ArtifactRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6)) },
        taskId, logicalName,
    ).singleOrNull()

    private fun storeRuntimeEvents(taskId: String, runtimeJobId: String) {
        val after = jdbc.query(
            "SELECT last_sequence FROM pf_ai_runtime_event_cursor WHERE task_id=?", { rs, _ -> rs.getLong(1) }, taskId,
        ).singleOrNull() ?: 0L
        val page = runtime.getV2Events(runtimeJobId, after)
        var last = after
        page.items.sortedBy { it.sequence }.forEach { event ->
            val safeText = when (event.logKind) {
                "REASONING_SUMMARY" -> event.logText
                "TOOL_CALL", "TOOL_OUTPUT" -> event.message
                null -> event.message
                else -> null
            }?.take(MAX_EVENT_TEXT)
            val exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM pf_ai_runtime_event WHERE runtime_job_id=? AND event_sequence=?",
                Long::class.java, runtimeJobId, event.sequence,
            ) ?: 0
            if (exists == 0L) jdbc.update(
                """INSERT INTO pf_ai_runtime_event(task_id,runtime_job_id,event_sequence,event_type,safe_message,progress_percent,occurred_at,stored_at)
                    VALUES (?,?,?,?,?,?,?,?)""".trimIndent(),
                taskId, runtimeJobId, event.sequence, event.type, safeText, event.progressPercent, event.createdAt, clock.instant(),
            )
            last = maxOf(last, event.sequence)
        }
        val updated = jdbc.update(
            "UPDATE pf_ai_runtime_event_cursor SET last_sequence=?,updated_at=? WHERE task_id=?",
            last, clock.instant(), taskId,
        )
        if (updated == 0) jdbc.update(
            "INSERT INTO pf_ai_runtime_event_cursor(task_id,runtime_job_id,last_sequence,updated_at) VALUES (?,?,?,?)",
            taskId, runtimeJobId, last, clock.instant(),
        )
    }

    private fun storeUsage(taskId: String, task: AiTaskDetails, runtimeJobId: String, summary: RuntimeUsageSummary) {
        val metrics = summary.metrics.associate { it.metric to it.quantity }
        val usageJson = mapper.writeValueAsString(mapOf(
            "inputTokens" to metrics["INPUT_TOKENS"]?.toLongOrNull(),
            "cachedInputTokens" to metrics["CACHED_INPUT_TOKENS"]?.toLongOrNull(),
            "outputTokens" to metrics["OUTPUT_TOKENS"]?.toLongOrNull(),
            "reasoningTokens" to metrics["REASONING_TOKENS"]?.toLongOrNull(),
            "metrics" to summary.metrics,
        ))
        val updated = jdbc.update(
            """UPDATE pf_ai_runtime_usage SET attempt_count=?,usage_quality=?,usage_json=?,cost_json=?,captured_at=? WHERE task_id=?""".trimIndent(),
            summary.attemptCount, summary.usageQuality, usageJson, mapper.writeValueAsString(summary.costs), clock.instant(), taskId,
        )
        if (updated == 0) jdbc.update(
            """INSERT INTO pf_ai_runtime_usage(task_id,runtime_job_id,task_type,vendor_id,model,execution_mode,attempt_count,usage_quality,usage_json,cost_json,captured_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            taskId, runtimeJobId, "STRUCTURED_GENERATION", task.execution.vendorId, task.execution.model, task.execution.mode.name,
            summary.attemptCount, summary.usageQuality, usageJson, mapper.writeValueAsString(summary.costs), clock.instant(),
        )
    }

    private fun storeRuntimeAttempts(taskId: String, task: AiTaskDetails, runtimeJobId: String, attempts: List<RuntimeAttemptView>) {
        attempts.forEach { attempt ->
            val updated = jdbc.update(
                """UPDATE pf_ai_runtime_attempt_usage SET attempt_number=?,status=?,usage_quality=?,usage_json=?,cost_json=?,captured_at=?
                    WHERE task_id=? AND runtime_attempt_id=?""".trimIndent(),
                attempt.number, attempt.status, attempt.usageQuality, mapper.writeValueAsString(attempt.usageSummary.metrics),
                mapper.writeValueAsString(attempt.usageSummary.costs), clock.instant(), taskId, attempt.id,
            )
            if (updated == 0) jdbc.update(
                """INSERT INTO pf_ai_runtime_attempt_usage(task_id,runtime_attempt_id,attempt_number,status,usage_quality,usage_json,cost_json,captured_at)
                    VALUES (?,?,?,?,?,?,?,?)""".trimIndent(),
                taskId, attempt.id, attempt.number, attempt.status, attempt.usageQuality, mapper.writeValueAsString(attempt.usageSummary.metrics),
                mapper.writeValueAsString(attempt.usageSummary.costs), clock.instant(),
            )
        }
        if (attempts.isNotEmpty()) storeUsage(taskId, task, runtimeJobId, aggregateUsage(attempts))
    }

    private fun aggregateUsage(attempts: List<RuntimeAttemptView>): RuntimeUsageSummary {
        val metrics = attempts.flatMap { it.usageSummary.metrics }.groupBy { it.metric to it.unit }.map { (key, values) ->
            RuntimeUsageMetric(key.first, values.mapNotNull { runCatching { BigDecimal(it.quantity) }.getOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add).stripTrailingZeros().toPlainString(), key.second)
        }
        val costs = attempts.flatMap { it.usageSummary.costs }.groupBy { Triple(it.kind, it.status, it.currency) }.map { (key, values) ->
            RuntimeCostValue(key.first, key.second, values.mapNotNull { runCatching { BigDecimal(it.amount) }.getOrNull() }.fold(BigDecimal.ZERO, BigDecimal::add).toPlainString(), key.third)
        }
        val qualities = attempts.map { it.usageQuality }.toSet()
        val quality = when {
            qualities == setOf("MOCK") -> "MOCK"
            qualities == setOf("COMPLETE") -> "COMPLETE"
            qualities == setOf("UNAVAILABLE") -> "UNAVAILABLE"
            else -> "PARTIAL"
        }
        return RuntimeUsageSummary(attempts.size, quality, metrics, costs)
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

    private fun failResultProjection(taskId: String, error: RuntimeCallException) {
        val now = clock.instant()
        jdbc.update("UPDATE pf_ai_task SET status='FAILED',error_code=?,safe_error_message=?,updated_at=? WHERE id=?", error.code, error.safeMessage, now, taskId)
        val exists = jdbc.queryForObject("SELECT COUNT(*) FROM pf_ai_task_result WHERE task_id=?", Long::class.java, taskId) ?: 0
        if (exists == 0L) jdbc.update(
            "INSERT INTO pf_ai_task_result(task_id,status,artifacts_json,error_code,safe_message,completed_at) VALUES (?,?,?,?,?,?)",
            taskId, AiTaskResultStatus.FAILED.name, "[]", error.code, error.safeMessage, now,
        )
    }

    fun validateFixtureResult(taskId: AiTaskId, result: JsonNode) {
        getAiTask(taskId)
        if (!schemaValidator.isValid(taskSpecification(taskId.value).responseSchema, result)) {
            throw InvalidCommand("Mockresultaat voldoet niet aan het bevroren jobschema.")
        }
    }

    fun cleanupExecutionContent() {
        val now = clock.instant()
        jdbc.query(
            """SELECT a.id,a.storage_key FROM pf_ai_artifact a
                WHERE a.artifact_status IN ('READY','FAILED') AND a.retention_until IS NOT NULL AND a.retention_until<=?
                  AND NOT EXISTS (SELECT 1 FROM pf_ai_artifact_domain_reference r WHERE r.artifact_id=a.id AND r.released_at IS NULL)""".trimIndent(),
            { rs, _ -> rs.getString(1) to rs.getString(2) }, now,
        ).forEach { (id, storageKey) ->
            if (jdbc.update("UPDATE pf_ai_artifact SET artifact_status='DELETING' WHERE id=? AND artifact_status IN ('READY','FAILED')", id) == 1) {
                runCatching { artifactStore.delete(storageKey) }
                    .onSuccess { jdbc.update("UPDATE pf_ai_artifact SET artifact_status='DELETED',deleted_at=? WHERE id=?", clock.instant(), id) }
                    .onFailure { jdbc.update("UPDATE pf_ai_artifact SET artifact_status='FAILED' WHERE id=?", id) }
            }
        }
        jdbc.update(
            """DELETE FROM pf_ai_runtime_upload u WHERE u.updated_at<? AND EXISTS (
                SELECT 1 FROM pf_ai_runtime_outbox o WHERE o.task_id=u.task_id AND (o.dispatched_at IS NOT NULL OR o.dispatch_state='CANCELLED'))""".trimIndent(),
            now.minusSeconds(UPLOAD_CORRELATION_RETENTION_SECONDS),
        )
    }

    fun prepareFixture(taskId: AiTaskId, result: JsonNode?, outputSequence: List<String>, artifactNames: Set<String>): String {
        val task = getAiTask(taskId)
        if (task.execution != AiExecutionSelection("mock", "mock", AiExecutionMode.MOCK)) {
            throw InvalidCommand("Alleen een expliciete mock/mock/MOCK-taak mag een acceptatiefixture krijgen.")
        }
        if (result != null) validateFixtureResult(taskId, result)
        if (outputSequence.isNotEmpty()) {
            val finalResult = runCatching { mapper.readTree(outputSequence.last()) }.getOrNull()
                ?: throw InvalidCommand("De laatste mockoutput moet geldige JSON zijn.")
            validateFixtureResult(taskId, finalResult)
        }
        val declared = taskSpecification(taskId.value).outputArtifacts.map { it.name }.toSet()
        if (!declared.containsAll(artifactNames)) throw InvalidCommand("Mockartifact is niet in het bevroren taakcontract gedeclareerd.")
        return jdbc.query(
            "SELECT runtime_idempotency_key FROM pf_ai_runtime_outbox WHERE task_id=? AND dispatched_at IS NULL",
            { rs, _ -> rs.getString(1) }, taskId.value,
        ).singleOrNull() ?: throw InvalidCommand("De AI-taak kan geen nieuwe mockfixture meer ontvangen.")
    }

    private fun frozenRequest(taskId: String): String? = jdbc.query(
        "SELECT frozen_request_json FROM pf_ai_runtime_outbox WHERE task_id=?", { rs, _ -> rs.getString(1) }, taskId,
    ).singleOrNull()

    private fun taskApiVersion(taskId: String): String = jdbc.queryForObject(
        "SELECT api_version FROM pf_ai_runtime_outbox WHERE task_id=?", String::class.java, taskId,
    ) ?: "v1"

    private fun isCancelledBeforeCreate(taskId: String): Boolean = jdbc.queryForObject(
        "SELECT COUNT(*) FROM pf_ai_task WHERE id=? AND runtime_job_id IS NULL AND cancel_requested=TRUE", Long::class.java, taskId,
    ) == 1L

    private fun renewDispatchClaim(taskId: String) {
        val now = clock.instant()
        jdbc.update(
            "UPDATE pf_ai_runtime_outbox SET claimed_until=?,updated_at=? WHERE task_id=? AND claimed_by=? AND dispatched_at IS NULL",
            now.plusSeconds(DISPATCH_CLAIM_SECONDS), now, taskId, coordinatorId,
        )
    }

    private fun releaseFrozenV2Request(taskId: String) {
        jdbc.query(
            "SELECT upload_id FROM pf_ai_runtime_upload WHERE task_id=? AND upload_id IS NOT NULL",
            { rs, _ -> rs.getString(1) }, taskId,
        ).forEach { uploadId -> runCatching { runtime.deleteUpload(uploadId) } }
        val now = clock.instant()
        jdbc.update(
            "UPDATE pf_ai_runtime_upload SET upload_state='EXPIRED',expires_at=?,updated_at=? WHERE task_id=?",
            now, now, taskId,
        )
        jdbc.update(
            "UPDATE pf_ai_runtime_outbox SET frozen_request_json=NULL,dispatch_state='PENDING_UPLOAD',updated_at=? WHERE task_id=? AND dispatched_at IS NULL",
            now, taskId,
        )
    }

    private fun cancelBeforeCreate(taskId: String) {
        jdbc.query(
            "SELECT upload_id FROM pf_ai_runtime_upload WHERE task_id=? AND upload_id IS NOT NULL",
            { rs, _ -> rs.getString(1) }, taskId,
        ).forEach { uploadId -> runCatching { runtime.deleteUpload(uploadId) } }
        val now = clock.instant()
        jdbc.update("UPDATE pf_ai_runtime_outbox SET dispatch_state='CANCELLED',dispatched_at=?,updated_at=? WHERE task_id=?", now, now, taskId)
        jdbc.update("UPDATE pf_ai_task SET status='CANCELLED',updated_at=? WHERE id=?", now, taskId)
        storeTerminalFailure(taskId, RuntimeJobView("", "CANCELLED", "CANCELLED", 0, null, null, "CANCELLED", "Taak vóór indiening geannuleerd.", now, now))
    }

    private fun inputRows(taskId: String): List<InputRow> = jdbc.query(
        "SELECT input_sequence,logical_name,filename,mime_type,input_role,content_bytes,size_bytes,sha256 FROM pf_ai_task_input WHERE task_id=? ORDER BY input_sequence",
        { rs, _ -> InputRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBytes(6), rs.getLong(7), rs.getString(8)) },
        taskId,
    )

    private fun uploadRow(taskId: String, sequence: Int): UploadRow? = jdbc.query(
        "SELECT upload_id,object_id,upload_url,chunk_size_bytes,upload_state,expires_at FROM pf_ai_runtime_upload WHERE task_id=? AND input_sequence=?",
        { rs, _ -> UploadRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4)?.let { rs.getInt(4) }, rs.getString(5), rs.getTimestamp(6)?.toInstant()) },
        taskId, sequence,
    ).singleOrNull()

    private fun taskSpecification(taskId: String): TaskSpecification = jdbc.query(
        """SELECT instruction,response_schema,repository_url,repository_commit_sha,environment_keys_json,output_artifacts_json,execution_timeout_seconds
            FROM pf_ai_task_specification WHERE task_id=?""".trimIndent(),
        { rs, _ -> TaskSpecification(
            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
            mapper.readValue(rs.getString(5), object : TypeReference<List<String>>() {}),
            mapper.readValue(rs.getString(6), object : TypeReference<List<AiOutputArtifactDeclaration>>() {}), rs.getInt(7),
        ) }, taskId,
    ).singleOrNull() ?: throw RuntimeCallException("RUNTIME_SPECIFICATION_MISSING", "De bevroren Runtime-specificatie ontbreekt.")

    private data class InputRow(
        val sequence: Int,
        val name: String,
        val filename: String,
        val mimeType: String,
        val role: String,
        val content: ByteArray?,
        val sizeBytes: Long,
        val sha256: String,
    )
    private data class UploadRow(
        val uploadId: String?,
        val objectId: String?,
        val uploadUrl: String?,
        val chunkSizeBytes: Int?,
        val state: String,
        val expiresAt: Instant?,
    )
    private data class TaskSpecification(
        val instruction: String,
        val responseSchema: String,
        val repositoryUrl: String?,
        val repositoryCommitSha: String?,
        val environmentKeys: List<String>,
        val outputArtifacts: List<AiOutputArtifactDeclaration>,
        val executionTimeoutSeconds: Int,
    )
    private data class ArtifactRow(
        val id: String,
        val storageKey: String,
        val status: String,
        val runtimeObjectId: String,
        val sizeBytes: Long,
        val sha256: String,
    )

    private fun validateTask(command: RequestAiTaskCommand) {
        if (command.idempotencyKey.isBlank() || command.idempotencyKey.length > 150) throw InvalidCommand("Ongeldige AI-taak-idempotentiesleutel.")
        if (command.requesterCapability.isBlank() || command.requesterCapability.length > 160) throw InvalidCommand("Ongeldige aanvragende capability.")
        if (command.agentRole.isBlank() || command.agentRole.length > 120) throw InvalidCommand("Een vertrouwde agentrol is verplicht.")
        if (command.prompt.isBlank() || command.prompt.length > 200_000) throw InvalidCommand("De complete AI-prompt is leeg of te groot.")
        if (command.promptTemplateVersion < 1) throw InvalidCommand("Een positieve prompttemplateversie is verplicht.")
        if (command.executionTimeout.seconds !in 30..86_400 || command.executionTimeout.nano != 0) throw InvalidCommand("Uitvoeringstime-out moet tussen 30 seconden en 24 uur liggen.")
        if (environment == "production" && command.execution.mode == AiExecutionMode.MOCK) throw InvalidCommand("Mock-AI-uitvoering is in productie niet toegestaan.")
        if (command.execution.vendorId.isBlank() || command.execution.model.isBlank()) throw InvalidCommand("Een expliciete leverancier en model zijn verplicht.")
        if (command.execution.mode == AiExecutionMode.MOCK && command.execution != AiExecutionSelection("mock", "mock", AiExecutionMode.MOCK)) {
            throw InvalidCommand("Mockuitvoering vereist exact mock / mock / MOCK.")
        }
        runCatching { mapper.readTree(command.responseSchema) }.getOrElse { throw InvalidCommand("Responseschema is geen geldige JSON.") }
        command.repository?.let {
            if (!it.publicGitUrl.startsWith("https://") || !SHA.matches(it.commitSha)) throw InvalidCommand("Repositorysnapshot moet HTTPS en een exacte commit-SHA gebruiken.")
        }
        if (command.attachments.size > 10 || command.attachments.sumOf { it.content.size } > 10 * 1024 * 1024) throw InvalidCommand("Te veel of te grote inputattachments.")
        command.attachments.forEach {
            if (!LOGICAL_NAME.matches(it.name) || !FILENAME.matches(it.filename) || it.content.size > 2 * 1024 * 1024 || it.mediaType !in ALLOWED_MEDIA_TYPES) {
                throw InvalidCommand("Inputattachment ${it.filename} heeft een onveilige naam, type of grootte.")
            }
        }
        if (command.attachments.map { it.name }.toSet().size != command.attachments.size || command.attachments.any { it.name == "prompt" }) {
            throw InvalidCommand("Inputobjectnamen moeten uniek zijn; prompt is gereserveerd.")
        }
        if (command.outputArtifacts.size > 50 || command.outputArtifacts.map { it.name }.toSet().size != command.outputArtifacts.size) {
            throw InvalidCommand("Outputartifactnamen moeten uniek zijn en tot vijftig declaraties beperkt blijven.")
        }
        command.outputArtifacts.forEach {
            if (!LOGICAL_NAME.matches(it.name) || it.mimeTypes.isEmpty() || it.mimeTypes.any { mime -> mime !in ALLOWED_OUTPUT_MEDIA_TYPES } || it.maxBytes !in 1..10L * 1024 * 1024) {
                throw InvalidCommand("Outputartifact ${it.name} heeft een onveilige declaratie.")
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
        """SELECT id,job_key,product_id,requester_capability,requester_session_id,agent_role,vendor_id,model,execution_mode,configuration_version,prompt_template_version,status,runtime_job_id,runtime_phase,runtime_attempt_count,safe_progress_percent,safe_progress,error_code,cancel_reason,created_at,updated_at
            FROM pf_ai_task $where ORDER BY created_at DESC""".trimIndent(),
        { rs, _ ->
            AiTaskDetails(
                AiTaskId(rs.getString("id")), AiJobKey(rs.getString("job_key")), rs.getString("product_id")?.let(::ProductId),
                rs.getString("requester_capability"),
                AiExecutionSelection(rs.getString("vendor_id"), rs.getString("model"), AiExecutionMode.valueOf(rs.getString("execution_mode"))),
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

    private fun persistInput(
        taskId: String,
        sequence: Int,
        name: String,
        filename: String,
        mediaType: String,
        role: AiInputRole,
        content: ByteArray,
        now: Instant,
    ) {
        jdbc.update(
            "INSERT INTO pf_ai_task_input(task_id,input_sequence,logical_name,filename,mime_type,input_role,content_bytes,size_bytes,sha256,staged_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
            taskId, sequence, name, filename, mediaType, role.name, content, content.size.toLong(), sha256(content), now,
        )
    }

    private fun legacyProvider(selection: AiExecutionSelection): String = when {
        selection.mode == AiExecutionMode.MOCK -> "MOCKED"
        selection.vendorId == "anthropic" -> "CLAUDE"
        else -> "CODEX"
    }

    private fun sha256(content: ByteArray) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))

    private fun JsonNode.longOrNull(field: String): Long? = path(field).takeUnless { it.isMissingNode || it.isNull }?.asLong()

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
        private const val V2_INSTRUCTION = "Lees de volledige opdracht uit invoerobject prompt, behandel alle andere invoerobjecten volgens hun rol en retourneer uitsluitend JSON volgens het responseschema."
        private const val DEFAULT_UPLOAD_CHUNK = 256 * 1024
        private const val MAX_UPLOAD_CHUNK = 4 * 1024 * 1024
        private const val DISPATCH_CLAIM_SECONDS = 300L
        private const val MAX_EVENT_TEXT = 2_000
        private const val TEMPORARY_ARTIFACT_RETENTION_SECONDS = 7 * 24 * 60 * 60L
        private const val UPLOAD_CORRELATION_RETENTION_SECONDS = 7 * 24 * 60 * 60L
        private val TERMINAL_STATUSES = setOf(AiTaskStatus.SUCCEEDED, AiTaskStatus.FAILED, AiTaskStatus.CANCELLED)
        private val RETRYABLE_CODES = setOf(
            "RUNTIME_NOT_CONFIGURED", "RUNTIME_SUBMISSION_FAILED", "RUNTIME_EMPTY_RESPONSE",
            "RUNTIME_UPLOAD_CREATE_FAILED", "RUNTIME_UPLOAD_HEAD_FAILED", "RUNTIME_UPLOAD_PATCH_FAILED",
            "RUNTIME_UPLOAD_COMPLETE_FAILED", "OBJECT_STORE_LOW_SPACE", "INPUT_OBJECT_NOT_READY",
        )
        private val LOCAL_RESULT_FATAL_CODES = setOf(
            "RUNTIME_RESULT_SCHEMA_INVALID", "RUNTIME_ARTIFACT_CONTRACT_INVALID", "RUNTIME_REQUIRED_ARTIFACT_MISSING",
            "RUNTIME_ARTIFACT_CHANGED", "RUNTIME_ARTIFACT_TOO_LARGE", "RUNTIME_ARTIFACT_OFFSET_INVALID", "RUNTIME_URL_REJECTED",
        )
        private val SHA = Regex("[0-9a-fA-F]{40}")
        private val SHA256 = Regex("[0-9a-f]{64}")
        private val DOMAIN_TYPE = Regex("[A-Z][A-Z0-9_]{0,79}")
        private val LOCAL_ARTIFACT_URI = Regex("/api/ai/tasks/([A-Za-z0-9-]{1,80})/artifacts/([A-Za-z0-9-]{1,80})")
        private val FILENAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,254}")
        private val LOGICAL_NAME = Regex("[a-z][a-z0-9-]{0,99}")
        private val PROJECT_PREFIX = Regex("[A-Z][A-Z0-9_]*")
        private val ALLOWED_MEDIA_TYPES = setOf("image/png", "image/jpeg", "image/webp", "application/pdf", "text/plain", "text/markdown", "application/json")
        private val ALLOWED_OUTPUT_MEDIA_TYPES = setOf("image/png", "image/jpeg", "image/webp", "application/pdf", "text/plain", "text/markdown", "application/json")
        private val JOB_ROLE = mapOf(
            "MEETING.CONVERSE" to "MEETING_AGENT",
            "MEETING.SUMMARIZE" to "MEETING_MINUTES_AGENT",
            "PRODUCT_ADVISOR.CONVERSE" to "PRODUCT_ADVISOR",
            "PRODUCT_DESIGN.CREATE_EPIC" to "PRODUCT_DESIGNER_MVP",
            "PLANNING.SELECT_WORK" to "PLANNER_MVP",
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

    @Scheduled(fixedDelayString = "\${PF_AI_RUNTIME_CLEANUP_DELAY_MS:3600000}")
    fun cleanup() {
        if (enabled) service.cleanupExecutionContent()
    }
}
