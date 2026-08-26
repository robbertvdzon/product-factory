package nl.vdzon.productfactory.dispatcher

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.dispatcher.*
import nl.vdzon.productfactory.api.planning.*
import nl.vdzon.productfactory.api.product.ProductQueryService
import nl.vdzon.productfactory.api.product.ProductStatus
import nl.vdzon.productfactory.api.shared.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.HexFormat
import java.util.UUID

@Service
class SoftwareFactoryDispatcherMvpService(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val products: ProductQueryService,
    private val planning: ProductPlanningService,
    private val planningQueries: ProductPlanningQueryService,
    adapters: List<SoftwareFactoryAdapter>,
    transactionManager: PlatformTransactionManager,
    @Value("\${PF_SOFTWARE_FACTORY_MODE:DISABLED}") private val configuredMode: String,
    @Value("\${PF_APPLICATION_VERSION:0.1.0-SNAPSHOT}") private val implementationVersion: String,
    @Value("\${PF_GIT_REVISION:unknown}") private val sourceRevision: String,
) : SoftwareFactoryDispatcherService, SoftwareFactoryDispatcherQueryService {
    private val transactions = TransactionTemplate(transactionManager)
    private val adaptersByMode = adapters.associateBy { it.mode }

    override fun runDispatchSession(productId: ProductId) {
        val sessionId = transactions.execute { startSession(productId) } ?: error("Dispatchersessie kon niet worden gestart.")
        try {
            val summary = dispatch(sessionId, productId)
            transactions.executeWithoutResult { finishSession(sessionId, summary) }
        } catch (failure: SoftwareFactoryFailure) {
            transactions.executeWithoutResult { blockSession(sessionId, failure.code, safeMessage(failure)) }
        } catch (error: Exception) {
            transactions.executeWithoutResult { blockSession(sessionId, "DISPATCH_FAILED", "Dispatcher kon veilig niet worden voortgezet.") }
        }
    }

    private fun dispatch(sessionId: ProcessSessionId, productId: ProductId): String {
        val product = products.getProduct(productId)
        if (product.status != ProductStatus.ACTIVE) return "Product is inactief; succesvolle no-op."
        if (!product.dispatchingEnabled) return "Dispatching staat uit; succesvolle no-op."
        val adapter = selectedAdapter()
        val connection = adapter.status()
        if (!connection.connected || connection.apiVersion != "2") {
            throw ContractFactoryFailure("FACTORY_NOT_CONNECTED", "Software Factory meldt geen verbonden v2-contract.")
        }

        val existingAttempts = attemptRows("WHERE product_id=? AND status NOT IN ('COMPLETED','CANCELLED')", productId.value)
        for (attempt in existingAttempts) {
            val outcome = reconcileAttempt(sessionId, adapter, attempt)
            if (outcome != null) return outcome
        }

        if (adapter.find(productId.value, "OPEN").isNotEmpty()) {
            return "Software Factory heeft al open werk voor dit product; succesvolle no-op."
        }

        val reservation = planning.reserveNextStoryForDispatch(
            ReserveNextStoryForDispatchCommand(productId, PROCESS_ACTOR, "dispatcher-reserve-${sessionId.value}"),
        ) ?: return "Geen uitvoerbare story beschikbaar; succesvolle no-op."
        val assignment = products.getProductAssignment(productId)
        val request = packageStory(reservation.story, assignment.publicGitUrl)
        val packageJson = mapper.writeValueAsString(request)
        val attempt = transactions.execute { createAttempt(sessionId, reservation, packageJson) }
            ?: error("Deliveryattempt kon niet worden vastgelegd.")
        return createOrRecover(sessionId, adapter, attempt)
    }

    private fun reconcileAttempt(sessionId: ProcessSessionId, adapter: SoftwareFactoryAdapter, attempt: AttemptRow): String? {
        val found = try {
            attempt.externalStoryId?.let(adapter::get)
                ?: adapter.find(idempotencyKey = attempt.idempotencyKey).singleOrNull()
        } catch (failure: SoftwareFactoryFailure) {
            recordFailure(attempt, failure)
            throw failure
        }
        if (found != null) return applyExternalState(sessionId, attempt, found)
        if (attempt.retryAfter?.isAfter(clock.instant()) == true) {
            return "Deliveryattempt wacht tot ${attempt.retryAfter}; succesvolle no-op."
        }
        if (attempt.externalStoryId != null) {
            throw ContractFactoryFailure("EXTERNAL_STORY_MISSING", "Een gekoppelde externe story ontbreekt onverwacht.")
        }
        if (attempt.status == DeliveryAttemptStatus.CONTRACT_FAILURE) {
            throw ContractFactoryFailure(attempt.lastErrorCode ?: "CONTRACT_FAILURE", "De eerdere contractbreuk blokkeert dit product.")
        }
        val validation = planning.revalidateDispatchReservation(
            RevalidateDispatchReservationCommand(
                attempt.reservationId, attempt.storyVersion, false, PROCESS_ACTOR,
                "dispatcher-revalidate-${attempt.id.value}",
            ),
        )
        if (!validation.valid) {
            updateAttempt(attempt.id, DeliveryAttemptStatus.CANCELLED, localCommand = LocalCommandStatus.APPLIED)
            return "Reservering is vóór externe aanmaak vervallen: ${validation.reason}"
        }
        return createOrRecover(sessionId, adapter, attempt)
    }

    private fun createOrRecover(sessionId: ProcessSessionId, adapter: SoftwareFactoryAdapter, attempt: AttemptRow): String {
        val request = mapper.readValue(attempt.packageJson, FactoryStoryRequest::class.java)
        incrementAttempt(attempt.id, sessionId)
        return try {
            val created = adapter.create(attempt.idempotencyKey, request)
            if (created.status !in EXTERNAL_STATUSES) throw ContractFactoryFailure("INVALID_STATUS", "Software Factory gaf een onbekende status terug.")
            val work = adapter.get(created.storyKey)
                ?: FactoryWork(created.storyKey, request.productId, request.sourceStoryId, request.sourceStoryVersion, created.status, null, null, null)
            applyExternalState(sessionId, attempt.copy(attemptCount = attempt.attemptCount + 1), work)
        } catch (failure: SoftwareFactoryFailure) {
            recordFailure(attempt, failure)
            throw failure
        }
    }

    private fun applyExternalState(sessionId: ProcessSessionId, attempt: AttemptRow, work: FactoryWork): String {
        validateWork(attempt, work)
        val externalStatus = ExternalStoryStatus.valueOf(work.status)
        var story = planningQueries.getStory(attempt.storyId)
        if (story.status == StoryStatus.TODO) {
            updateLocalCommand(attempt.id, LocalCommandStatus.PENDING)
            try {
                planning.markStoryAsDispatched(
                    MarkStoryAsDispatchedCommand(
                        attempt.reservationId, work.storyKey, attempt.storyVersion, PROCESS_ACTOR,
                        "dispatcher-dispatched-${attempt.id.value}",
                    ),
                )
                updateLocalCommand(attempt.id, LocalCommandStatus.APPLIED)
                story = planningQueries.getStory(attempt.storyId)
            } catch (error: Exception) {
                updateLocalCommand(attempt.id, LocalCommandStatus.FAILED)
                throw error
            }
        }
        when (externalStatus) {
            ExternalStoryStatus.OPEN -> {
                updateAccepted(attempt.id, sessionId, work)
                clearProductBlock(attempt.productId, attempt.id)
                return "Story ${attempt.storyId.value} is idempotent gekoppeld aan ${work.storyKey}."
            }
            ExternalStoryStatus.DONE -> {
                val sha = work.deliveredCommitSha ?: throw ContractFactoryFailure("DONE_WITHOUT_SHA", "DONE mist een volledige oplevercommit.")
                if (!FULL_SHA.matches(sha)) throw ContractFactoryFailure("DONE_WITHOUT_SHA", "DONE bevat geen volledige oplevercommit.")
                if (story.status == StoryStatus.IN_PROGRESS) {
                    updateLocalCommand(attempt.id, LocalCommandStatus.PENDING)
                    planning.markStoryAsDeveloped(
                        MarkStoryAsDevelopedCommand(story.id, work.storyKey, sha, story.version, PROCESS_ACTOR, "dispatcher-developed-${attempt.id.value}"),
                    )
                }
                updateTerminal(attempt.id, sessionId, work, DeliveryAttemptStatus.COMPLETED, sha)
                planning.flushPendingEffects()
                clearProductBlock(attempt.productId, attempt.id)
                return "Externe story ${work.storyKey} is opgeleverd met commit $sha."
            }
            ExternalStoryStatus.CANCELLED -> {
                if (story.status == StoryStatus.IN_PROGRESS) {
                    updateLocalCommand(attempt.id, LocalCommandStatus.PENDING)
                    planning.markStoryAsCancelled(
                        MarkStoryAsCancelledCommand(
                            story.id, work.storyKey, work.cancelReason ?: "Software Factory annuleerde het werk.", story.version,
                            PROCESS_ACTOR, "dispatcher-cancelled-${attempt.id.value}",
                        ),
                    )
                }
                updateTerminal(attempt.id, sessionId, work, DeliveryAttemptStatus.CANCELLED, null)
                clearProductBlock(attempt.productId, attempt.id)
                return "Externe story ${work.storyKey} is geannuleerd."
            }
        }
    }

    private fun packageStory(story: StoryDetails, repositoryUrl: String): FactoryStoryRequest {
        val description = buildString {
            appendLine("## Samenvatting")
            appendLine(story.summary.trim())
            appendLine()
            appendLine("## Gedrag")
            appendLine(story.content.trim())
            appendLine()
            appendLine("## Gebruikerswaarde")
            appendLine(story.summary.trim())
            appendLine()
            appendLine("## Acceptatiecriteria")
            story.acceptanceCriteria.forEach { appendLine("- ${it.trim()}") }
            appendLine()
            appendLine("## UX")
            appendLine(story.uxDesign?.trim().orEmpty().ifBlank { "Geen aanvullende UX-specificatie." })
            appendLine()
            appendLine("## Afhankelijkheden")
            appendLine(story.dependencies.sortedBy { it.value }.joinToString("\n") { "- Story `${it.value}` moet opgeleverd zijn." }.ifBlank { "Geen." })
            appendLine()
            appendLine("## Bronversies")
            appendLine("- Product Factory-story `${story.id.value}` versie ${story.version}")
            appendLine("- Epic `${story.epicId.value}` versie ${story.epicVersion}")
            story.bugId?.let { appendLine("- Bug `${it.value}` versie ${story.bugVersion}") }
            appendLine()
            appendLine("## Technische grens")
            appendLine("Implementeer uitsluitend deze zelfstandige story in de doelrepository en lever een volledige commit-SHA op.")
        }.trim()
        return FactoryStoryRequest(
            story.productId.value, story.id.value, story.version, story.type.name, repositoryUrl,
            story.title.trim(), description, emptyList(),
        )
    }

    private fun selectedAdapter(): SoftwareFactoryAdapter {
        val mode = configuredMode.trim().uppercase()
        if (mode == "DISABLED") throw ConfigurationFactoryFailure("DISPATCH_DISABLED", "Software Factory-dispatching is geconfigureerd als DISABLED.")
        return adaptersByMode[mode] ?: throw ConfigurationFactoryFailure("INVALID_MODE", "Onbekende Software Factory-modus.")
    }

    private fun startSession(productId: ProductId): ProcessSessionId {
        products.getProduct(productId)
        val id = ProcessSessionId(UUID.randomUUID().toString())
        val now = clock.instant()
        try {
            jdbc.update(
                """INSERT INTO pf_dispatcher_process_session(id,product_id,active_product_id,status,implementation_artifact,implementation_variant,
                    implementation_version,implementation_revision,inputs_json,publications_json,started_at,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
                id.value, productId.value, productId.value, "RUNNING", ARTIFACT, VARIANT, implementationVersion, sourceRevision,
                mapper.writeValueAsString(listOf(SourceReference("PRODUCT", productId.value, products.getProduct(productId).version))), "[]", now, now,
            )
        } catch (_: DuplicateKeyException) {
            throw ProcessAlreadyRunning(productId)
        }
        return id
    }

    private fun createAttempt(sessionId: ProcessSessionId, reservation: StoryDispatchReservationDetails, packageJson: String): AttemptRow {
        val id = DeliveryAttemptId(UUID.randomUUID().toString())
        val key = "product-factory:${reservation.story.productId.value}:story:${reservation.story.id.value}:v${reservation.story.version}"
        val hash = sha256(packageJson.toByteArray())
        val now = clock.instant()
        jdbc.update(
            """INSERT INTO pf_delivery_attempt(id,product_id,story_id,story_version,reservation_id,idempotency_key,package_hash,package_json,status,
                attempt_count,local_command_status,last_session_id,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(),
            id.value, reservation.story.productId.value, reservation.story.id.value, reservation.story.version, reservation.reservationId,
            key, hash, packageJson, "PENDING", 0, "NOT_REQUIRED", sessionId.value, now, now,
        )
        return attemptRows("WHERE id=?", id.value).single()
    }

    private fun validateWork(attempt: AttemptRow, work: FactoryWork) {
        if (work.productId != attempt.productId.value || work.sourceStoryId != attempt.storyId.value || work.sourceStoryVersion != attempt.storyVersion) {
            throw ContractFactoryFailure("MISMATCHED_STORY", "Software Factory-response verwijst niet naar de bevroren storyversie.")
        }
        if (work.status !in EXTERNAL_STATUSES) throw ContractFactoryFailure("INVALID_STATUS", "Software Factory gaf een onbekende status terug.")
        if (work.status == "DONE" && work.deliveredCommitSha?.matches(FULL_SHA) != true) {
            throw ContractFactoryFailure("DONE_WITHOUT_SHA", "DONE mist een volledige oplevercommit.")
        }
    }

    private fun recordFailure(attempt: AttemptRow, failure: SoftwareFactoryFailure) {
        val status = when (failure) {
            is RetryableFactoryFailure -> DeliveryAttemptStatus.RETRYABLE_FAILURE
            is ConfigurationFactoryFailure -> DeliveryAttemptStatus.CONFIGURATION_FAILURE
            is AuthorizationFactoryFailure -> DeliveryAttemptStatus.AUTHORIZATION_FAILURE
            is ContractFactoryFailure -> DeliveryAttemptStatus.CONTRACT_FAILURE
        }
        val retry = if (failure is RetryableFactoryFailure) clock.instant().plus(retryDelay(attempt.attemptCount + 1)) else null
        val now = clock.instant()
        jdbc.update(
            """UPDATE pf_delivery_attempt SET status=?,retry_after=?,last_error_code=?,last_error_message=?,updated_at=? WHERE id=?""",
            status.name, retry, failure.code.take(160), safeMessage(failure), now, attempt.id.value,
        )
        blockProduct(attempt.productId, attempt.id, safeMessage(failure))
    }

    private fun retryDelay(attemptCount: Int) = when (attemptCount) {
        1 -> Duration.ofMinutes(1)
        2 -> Duration.ofMinutes(5)
        3 -> Duration.ofMinutes(30)
        else -> Duration.ofHours(2)
    }

    private fun incrementAttempt(id: DeliveryAttemptId, sessionId: ProcessSessionId) = jdbc.update(
        "UPDATE pf_delivery_attempt SET attempt_count=attempt_count+1,last_session_id=?,retry_after=NULL,updated_at=? WHERE id=?",
        sessionId.value, clock.instant(), id.value,
    )

    private fun updateAccepted(id: DeliveryAttemptId, sessionId: ProcessSessionId, work: FactoryWork) = jdbc.update(
        """UPDATE pf_delivery_attempt SET external_story_id=?,external_status='OPEN',status='ACCEPTED',local_command_status='APPLIED',
            last_session_id=?,retry_after=NULL,last_error_code=NULL,last_error_message=NULL,updated_at=? WHERE id=?""",
        work.storyKey, sessionId.value, clock.instant(), id.value,
    )

    private fun updateTerminal(id: DeliveryAttemptId, sessionId: ProcessSessionId, work: FactoryWork, status: DeliveryAttemptStatus, sha: String?) = jdbc.update(
        """UPDATE pf_delivery_attempt SET external_story_id=?,external_status=?,delivered_commit_sha=?,status=?,local_command_status='APPLIED',
            last_session_id=?,retry_after=NULL,last_error_code=NULL,last_error_message=NULL,updated_at=? WHERE id=?""",
        work.storyKey, work.status, sha, status.name, sessionId.value, clock.instant(), id.value,
    )

    private fun updateAttempt(id: DeliveryAttemptId, status: DeliveryAttemptStatus, localCommand: LocalCommandStatus) = jdbc.update(
        "UPDATE pf_delivery_attempt SET status=?,local_command_status=?,retry_after=NULL,updated_at=? WHERE id=?",
        status.name, localCommand.name, clock.instant(), id.value,
    )
    private fun updateLocalCommand(id: DeliveryAttemptId, status: LocalCommandStatus) = jdbc.update(
        "UPDATE pf_delivery_attempt SET local_command_status=?,updated_at=? WHERE id=?", status.name, clock.instant(), id.value,
    )
    private fun blockProduct(productId: ProductId, attemptId: DeliveryAttemptId, reason: String) {
        val now = clock.instant()
        if (jdbc.update("UPDATE pf_dispatcher_product_state SET blocked=TRUE,blocked_reason=?,last_attempt_id=?,updated_at=? WHERE product_id=?", reason, attemptId.value, now, productId.value) == 0) {
            jdbc.update("INSERT INTO pf_dispatcher_product_state(product_id,blocked,blocked_reason,last_attempt_id,updated_at) VALUES (?,TRUE,?,?,?)", productId.value, reason, attemptId.value, now)
        }
    }
    private fun clearProductBlock(productId: ProductId, attemptId: DeliveryAttemptId) {
        val now = clock.instant()
        if (jdbc.update("UPDATE pf_dispatcher_product_state SET blocked=FALSE,blocked_reason=NULL,last_attempt_id=?,updated_at=? WHERE product_id=?", attemptId.value, now, productId.value) == 0) {
            jdbc.update("INSERT INTO pf_dispatcher_product_state(product_id,blocked,last_attempt_id,updated_at) VALUES (?,FALSE,?,?)", productId.value, attemptId.value, now)
        }
    }

    private fun finishSession(id: ProcessSessionId, summary: String) {
        val now = clock.instant()
        jdbc.update("UPDATE pf_dispatcher_process_session SET status='SUCCEEDED',active_product_id=NULL,result_summary=?,finished_at=?,updated_at=? WHERE id=?", summary.take(2000), now, now, id.value)
    }
    private fun blockSession(id: ProcessSessionId, code: String, message: String) {
        val now = clock.instant()
        jdbc.update("UPDATE pf_dispatcher_process_session SET status='BLOCKED',active_product_id=NULL,error_code=?,blocked_reason=?,finished_at=?,updated_at=? WHERE id=?", code.take(160), message.take(1000), now, now, id.value)
    }

    @Transactional(readOnly = true)
    override fun getDispatchStatus(productId: ProductId): DispatcherProductStatusDetails {
        val running = (jdbc.queryForObject("SELECT COUNT(*) FROM pf_dispatcher_process_session WHERE active_product_id=?", Long::class.java, productId.value) ?: 0) > 0
        val state = jdbc.query("SELECT blocked,blocked_reason,last_attempt_id,updated_at FROM pf_dispatcher_product_state WHERE product_id=?", { rs, _ -> StateRow(rs.getBoolean(1), rs.getString(2), rs.getString(3)?.let(::DeliveryAttemptId), rs.getTimestamp(4).toInstant()) }, productId.value).singleOrNull()
        val attempt = state?.lastAttemptId?.let { attemptRows("WHERE id=?", it.value).singleOrNull() }
        return DispatcherProductStatusDetails(
            productId, running, state?.blocked ?: false, state?.reason, attempt?.externalStoryId,
            attempt?.externalStatus, state?.lastAttemptId, attempt?.retryAfter, state?.updatedAt ?: Instant.EPOCH,
        )
    }

    @Transactional(readOnly = true)
    override fun findDeliveryAttempts(filter: DeliveryAttemptFilter): List<DeliveryAttemptDetails> = attemptRows().filter { row ->
        (filter.productId == null || row.productId == filter.productId) && (filter.storyId == null || row.storyId == filter.storyId) &&
            (filter.statuses.isEmpty() || row.status in filter.statuses) &&
            (filter.timeRange.from == null || !row.createdAt.isBefore(filter.timeRange.from)) &&
            (filter.timeRange.until == null || row.createdAt.isBefore(filter.timeRange.until))
    }.map(AttemptRow::details)

    @Transactional(readOnly = true)
    override fun getDispatchSession(processSessionId: ProcessSessionId): ProcessSessionDetails = sessionRows("WHERE id=?", processSessionId.value).singleOrNull()
        ?: throw AggregateNotFound("Dispatchersessie bestaat niet.")

    @Transactional(readOnly = true)
    override fun findDispatchSessions(filter: ProcessSessionFilter): List<ProcessSessionDetails> = sessionRows().filter { row ->
        (filter.productId == null || row.productId == filter.productId) && (filter.statuses.isEmpty() || row.status in filter.statuses) &&
            (filter.timeRange.from == null || !row.startedAt.isBefore(filter.timeRange.from)) &&
            (filter.timeRange.until == null || row.startedAt.isBefore(filter.timeRange.until))
    }

    private fun attemptRows(where: String = "", vararg args: Any): List<AttemptRow> = jdbc.query(
        """SELECT id,product_id,story_id,story_version,reservation_id,idempotency_key,package_hash,package_json,external_story_id,
            external_status,delivered_commit_sha,status,attempt_count,retry_after,last_error_code,last_error_message,local_command_status,
            created_at,updated_at FROM pf_delivery_attempt $where ORDER BY created_at DESC""".trimIndent(),
        { rs, _ -> AttemptRow(
            DeliveryAttemptId(rs.getString(1)), ProductId(rs.getString(2)), StoryId(rs.getString(3)), rs.getLong(4), rs.getString(5),
            rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10)?.let(ExternalStoryStatus::valueOf),
            rs.getString(11), DeliveryAttemptStatus.valueOf(rs.getString(12)), rs.getInt(13), rs.getTimestamp(14)?.toInstant(),
            rs.getString(15), rs.getString(16), LocalCommandStatus.valueOf(rs.getString(17)), rs.getTimestamp(18).toInstant(), rs.getTimestamp(19).toInstant(),
        ) }, *args,
    )

    private fun sessionRows(where: String = "", vararg args: Any): List<ProcessSessionDetails> = jdbc.query(
        """SELECT id,product_id,status,implementation_artifact,implementation_variant,implementation_version,implementation_revision,
            started_at,finished_at,inputs_json,publications_json,result_summary,blocked_reason,error_code
            FROM pf_dispatcher_process_session $where ORDER BY started_at DESC""".trimIndent(),
        { rs, _ -> ProcessSessionDetails(
            ProcessSessionId(rs.getString(1)), ProductId(rs.getString(2)), ProcessSessionStatus.valueOf(rs.getString(3)),
            ImplementationIdentity(rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)), rs.getTimestamp(8).toInstant(),
            rs.getTimestamp(9)?.toInstant(), readJson(rs.getString(10)), publications = readJson(rs.getString(11)), resultSummary = rs.getString(12),
            blockedReason = rs.getString(13), errorCode = rs.getString(14),
        ) }, *args,
    )

    @Transactional
    fun deleteAllOwnedData() {
        jdbc.update("DELETE FROM pf_delivery_attempt")
        jdbc.update("DELETE FROM pf_dispatcher_product_state")
        jdbc.update("DELETE FROM pf_dispatcher_process_session")
    }

    private inline fun <reified T> readJson(value: String): T = mapper.readValue(value, object : TypeReference<T>() {})
    private fun sha256(bytes: ByteArray) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun safeMessage(error: SoftwareFactoryFailure) = (error.message ?: "Software Factory-fout.").take(1000)

    private data class StateRow(val blocked: Boolean, val reason: String?, val lastAttemptId: DeliveryAttemptId?, val updatedAt: Instant)
    private data class AttemptRow(
        val id: DeliveryAttemptId, val productId: ProductId, val storyId: StoryId, val storyVersion: Long, val reservationId: String,
        val idempotencyKey: String, val packageHash: String, val packageJson: String, val externalStoryId: String?,
        val externalStatus: ExternalStoryStatus?, val deliveredCommitSha: String?, val status: DeliveryAttemptStatus, val attemptCount: Int,
        val retryAfter: Instant?, val lastErrorCode: String?, val lastErrorMessage: String?, val localCommandStatus: LocalCommandStatus,
        val createdAt: Instant, val updatedAt: Instant,
    ) {
        fun details() = DeliveryAttemptDetails(
            id, productId, storyId, reservationId, externalStoryId, externalStatus, idempotencyKey, packageHash, status, attemptCount,
            lastErrorCode, lastErrorMessage, localCommandStatus, deliveredCommitSha, retryAfter, createdAt, updatedAt,
        )
    }

    companion object {
        private const val ARTIFACT = "software-factory-dispatcher-impl"
        private const val VARIANT = "v2-idempotent"
        private val PROCESS_ACTOR = ActorReference(ActorType.SYSTEM, "software-factory-dispatcher")
        private val FULL_SHA = Regex("[a-fA-F0-9]{40}|[a-fA-F0-9]{64}")
        private val EXTERNAL_STATUSES = setOf("OPEN", "DONE", "CANCELLED")
    }
}
