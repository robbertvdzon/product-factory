package nl.vdzon.productfactory.autonomy

import com.fasterxml.jackson.databind.JsonNode
import nl.vdzon.productfactory.contracts.ProductView
import nl.vdzon.productfactory.contracts.AgentTask
import nl.vdzon.productfactory.autonomy.api.StoryDeliveryPort
import nl.vdzon.productfactory.agentruntime.api.AgentRunRegistry
import nl.vdzon.productfactory.iteration.ShadowIterationRepository
import nl.vdzon.productfactory.iteration.ShadowIterationService
import nl.vdzon.productfactory.agentruntime.api.AgentDispatchPort
import nl.vdzon.productfactory.product.api.ProductCatalog
import nl.vdzon.productfactory.workspace.WorkspacePublisher
import nl.vdzon.productfactory.workspace.api.WorkspaceArtifact
import nl.vdzon.productfactory.workspace.api.WorkspacePublicationPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.client.RestClient
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneId
import java.sql.Timestamp
import java.util.UUID

data class StoryDeliveryView(
    val id: Long,
    val productSlug: String,
    val candidateId: Long,
    val iterationId: String,
    val title: String,
    val externalStoryKey: String?,
    val status: String,
    val remotePhase: String?,
    val workspaceCommitSha: String,
    val artifactPath: String,
    val errorMessage: String?,
    val createdAt: Instant,
    val deliveredAt: Instant?,
    val completedAt: Instant?,
)

data class HumanActionView(
    val id: Long,
    val productSlug: String,
    val deliveryId: Long?,
    val category: String,
    val title: String,
    val reason: String,
    val instructions: String?,
    val status: String,
    val createdAt: Instant,
)

data class DeliveryCandidate(
    val candidateId: Long,
    val productSlug: String,
    val iterationId: String,
    val title: String,
    val description: String,
    val acceptanceCriteria: String?,
    val criticReason: String?,
    val workspaceRunId: String,
    val workspaceCommitSha: String,
    val artifactPath: String,
)

data class SoftwareFactoryStoryRequest(
    val productSlug: String,
    val projectKey: String,
    val title: String,
    val description: String,
    val repo: String,
    val aiSupplier: String?,
    val aiModel: String?,
    val workspaceRunId: String,
    val workspaceCommitSha: String,
    val artifactPath: String,
    val deliveryMode: String = "start-next",
    // Product Factory handelt storyverloop zelf af (zie AutonomousQuestionResolver); alleen een ERROR
    // is iets waar de eigenaar zelf voor moet ingrijpen, dus alleen dat event triggert een melding.
    val notificationEvents: Set<String> = setOf("ERROR"),
    val questionsAllowed: Boolean = true,
    val approvalMode: String = "automatisch",
)

data class SoftwareFactoryStoryResponse(val storyKey: String, val created: Boolean, val deliveryMode: String)
data class SoftwareFactoryAnswerRequest(val targetType: String, val targetKey: String, val phase: String, val answer: String)
data class StoryQuestionRecord(
    val id: Long,
    val deliveryId: Long,
    val targetKey: String,
    val phase: String,
    val question: String,
    val status: String,
)
data class CompleteHumanActionRequest(val result: String)
data class HumanActionCompletion(
    val productSlug: String,
    val deliveryId: Long,
    val questionId: Long?,
    val storyKey: String,
    val targetKey: String?,
    val questionPhase: String?,
)

internal fun JsonNode.hasNonBlankTextValue(): Boolean = !isMissingNode && !isNull && asText().isNotBlank()

interface SoftwareFactoryGateway {
    fun createStory(request: SoftwareFactoryStoryRequest, idempotencyKey: String): SoftwareFactoryStoryResponse
    fun story(storyKey: String): JsonNode
    fun answer(storyKey: String, request: SoftwareFactoryAnswerRequest)
}

@Service
class SoftwareFactoryClient(
    @Value("\${product-factory.software-factory.base-url}") baseUrl: String,
    @Value("\${product-factory.software-factory.token:}") private val token: String,
) : SoftwareFactoryGateway {
    private val client = RestClient.builder().baseUrl(baseUrl.removeSuffix("/"))
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json").build()

    override fun createStory(request: SoftwareFactoryStoryRequest, idempotencyKey: String): SoftwareFactoryStoryResponse {
        require(token.isNotBlank()) { "PF_SOFTWARE_FACTORY_TOKEN ontbreekt" }
        return client.post().uri("/api/integrations/v1/stories")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header("Idempotency-Key", idempotencyKey)
            .body(request).retrieve().body(SoftwareFactoryStoryResponse::class.java)
            ?: error("Software Factory gaf geen storyresultaat")
    }

    override fun story(storyKey: String): JsonNode {
        require(token.isNotBlank()) { "PF_SOFTWARE_FACTORY_TOKEN ontbreekt" }
        return client.get().uri("/api/integrations/v1/stories/{key}", storyKey)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .retrieve().body(JsonNode::class.java) ?: error("Leeg Software Factory-resultaat")
    }

    override fun answer(storyKey: String, request: SoftwareFactoryAnswerRequest) {
        require(token.isNotBlank()) { "PF_SOFTWARE_FACTORY_TOKEN ontbreekt" }
        client.post().uri("/api/integrations/v1/stories/{key}/answers", storyKey)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .body(request).retrieve().toBodilessEntity()
    }
}

@Repository
class AutonomousDeliveryRepository(private val jdbc: JdbcTemplate) {
    fun eligible(productSlug: String): List<DeliveryCandidate> = jdbc.query(
        """select c.id, c.product_slug, c.iteration_id, c.title, c.description, c.acceptance_criteria,
                  c.critic_reason, i.workspace_run_id, w.commit_sha, w.artifact_path
            from story_candidate c
            join shadow_iteration i on i.id = c.iteration_id
            join workspace_publication w on w.run_id = i.workspace_run_id
            left join story_delivery d on d.candidate_id = c.id
            where c.product_slug = ? and c.status = 'INTERNAL' and c.critic_status = 'ACCEPT'
              and i.mode = 'autonomous' and i.status = 'ACCEPTED' and w.status = 'MERGED' and d.id is null
            order by i.sequence_number, c.id""".trimIndent(),
        { row, _ -> DeliveryCandidate(
            row.getLong(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5),
            row.getString(6), row.getString(7), row.getString(8), row.getString(9), row.getString(10),
        ) },
        productSlug,
    )

    fun eligible(productSlug: String, candidateId: Long): DeliveryCandidate? = eligible(productSlug).firstOrNull { it.candidateId == candidateId }
    fun existing(candidateId: Long): StoryDeliveryView? = byCandidate(candidateId)

    fun reserve(candidate: DeliveryCandidate): StoryDeliveryView? {
        val key = "${candidate.productSlug}:candidate:${candidate.candidateId}"
        try {
            jdbc.update(
                """insert into story_delivery(product_slug, candidate_id, iteration_id, workspace_run_id,
                    workspace_commit_sha, artifact_path, idempotency_key, status)
                    values (?, ?, ?, ?, ?, ?, ?, 'DELIVERING')""".trimIndent(),
                candidate.productSlug, candidate.candidateId, candidate.iterationId, candidate.workspaceRunId,
                candidate.workspaceCommitSha, candidate.artifactPath, key,
            )
        } catch (_: DuplicateKeyException) {
            return null
        }
        return byCandidate(candidate.candidateId)
    }

    fun markDelivered(id: Long, candidateId: Long, storyKey: String) {
        jdbc.update(
            "update story_delivery set status = 'DELIVERED', external_story_key = ?, delivered_at = current_timestamp, error_message = null where id = ?",
            storyKey, id,
        )
        jdbc.update("update story_candidate set status = 'PUBLISHED' where id = ?", candidateId)
    }

    fun markDeliveryError(id: Long, message: String) {
        jdbc.update("update story_delivery set status = 'ERROR', error_message = ? where id = ?", message.take(4000), id)
    }

    fun recordReconcileError(id: Long, message: String) {
        jdbc.update("update story_delivery set error_message = ?, last_reconciled_at = current_timestamp where id = ?", message.take(4000), id)
    }

    fun updateRemote(id: Long, status: String, phase: String?) {
        val complete = status == "DONE"
        jdbc.update(
            """update story_delivery set status = ?, remote_phase = ?, error_message = null,
                last_reconciled_at = current_timestamp, completed_at = case when ? then current_timestamp else completed_at end
                where id = ?""".trimIndent(),
            status, phase, complete, id,
        )
    }

    fun active(productSlug: String): List<StoryDeliveryView> = list(productSlug).filter { it.status in ACTIVE_STATUSES }
    fun toReconcile(productSlug: String): List<StoryDeliveryView> = list(productSlug).filter {
        it.externalStoryKey != null && (it.status in ACTIVE_STATUSES || it.status == "ERROR")
    }
    fun errors(productSlug: String): Int = jdbc.queryForObject(
        "select count(*) from story_delivery where product_slug = ? and status = 'ERROR'", Int::class.java, productSlug,
    ) ?: 0
    fun workspaceRunsToRefresh(productSlug: String): List<String> = jdbc.queryForList(
        """select distinct i.workspace_run_id from shadow_iteration i join workspace_publication w on w.run_id = i.workspace_run_id
            where i.product_slug = ? and i.mode = 'autonomous' and i.status = 'ACCEPTED' and w.status <> 'MERGED'""".trimIndent(),
        String::class.java, productSlug,
    )
    /** Laatste autonome cyclus die vandaag is gestart, zodat elke geconfigureerde cyclustijd zijn eigen trigger krijgt. */
    fun lastAutonomousIterationToday(productSlug: String, date: LocalDate, zone: ZoneId): Instant? = jdbc.query(
        "select max(created_at) as last_created_at from shadow_iteration where product_slug = ? and mode = 'autonomous' and created_at >= ? and created_at < ?",
        { row, _ -> row.getTimestamp("last_created_at")?.toInstant() },
        productSlug,
        databaseTimestamp(date.atStartOfDay(zone).toInstant()),
        databaseTimestamp(date.plusDays(1).atStartOfDay(zone).toInstant()),
    ).firstOrNull()
    fun openHumanActions(productSlug: String): Int = jdbc.queryForObject(
        "select count(*) from human_action where product_slug = ? and category = 'ACCESS_TOKEN' and status = 'OPEN'",
        Int::class.java,
        productSlug,
    ) ?: 0
    fun unevaluatedDone(productSlug: String): List<StoryDeliveryView> = list(productSlug).filter { it.status == "DONE" }.filter { delivery ->
        (jdbc.queryForObject("select count(*) from story_delivery where id = ? and evaluated_at is null", Int::class.java, delivery.id) ?: 0) > 0
    }
    fun markEvaluated(id: Long, workspaceRunId: String) {
        jdbc.update("update story_delivery set evaluation_workspace_run_id = ?, evaluated_at = current_timestamp where id = ?", workspaceRunId, id)
    }

    fun list(productSlug: String): List<StoryDeliveryView> = jdbc.query(
        """select d.id, d.product_slug, d.candidate_id, d.iteration_id, c.title, d.external_story_key,
                  d.status, d.remote_phase, d.workspace_commit_sha, d.artifact_path, d.error_message,
                  d.created_at, d.delivered_at, d.completed_at
            from story_delivery d join story_candidate c on c.id = d.candidate_id
            where d.product_slug = ? order by d.id desc""".trimIndent(),
        { row, _ -> StoryDeliveryView(
            row.getLong(1), row.getString(2), row.getLong(3), row.getString(4), row.getString(5), row.getString(6),
            row.getString(7), row.getString(8), row.getString(9), row.getString(10), row.getString(11),
            row.getTimestamp(12).toInstant(), row.getTimestamp(13)?.toInstant(), row.getTimestamp(14)?.toInstant(),
        ) }, productSlug,
    )

    fun humanActions(productSlug: String): List<HumanActionView> = jdbc.query(
        """select id, product_slug, delivery_id, category, title, reason, instructions, status, created_at
            from human_action where product_slug = ? and category = 'ACCESS_TOKEN' order by id desc""".trimIndent(),
        { row, _ -> HumanActionView(
            row.getLong(1), row.getString(2), row.getObject(3, java.lang.Long::class.java)?.toLong(), row.getString(4),
            row.getString(5), row.getString(6), row.getString(7), row.getString(8), row.getTimestamp(9).toInstant(),
        ) }, productSlug,
    )

    fun registerQuestion(deliveryId: Long, targetKey: String, phase: String, question: String): StoryQuestionRecord {
        try {
            jdbc.update(
                "insert into story_question(delivery_id, external_target_key, phase, question, status) values (?, ?, ?, ?, 'NEW')",
                deliveryId, targetKey, phase, question,
            )
        } catch (_: DuplicateKeyException) { /* hieronder bepalen we of de inhoud inmiddels is veranderd */ }
        var record = jdbc.query(
            "select id, delivery_id, external_target_key, phase, question, status from story_question where delivery_id = ? and external_target_key = ? and phase = ?",
            { row, _ -> StoryQuestionRecord(row.getLong(1), row.getLong(2), row.getString(3), row.getString(4), row.getString(5), row.getString(6)) },
            deliveryId, targetKey, phase,
        ).single()
        val resolutionIsNoLongerRunning = record.status == "RESOLVING" && (jdbc.queryForObject(
            """select count(*) from agent_run ar join story_question q on q.agent_run_id = ar.run_id
                where q.id = ? and ar.status = 'RUNNING'""".trimIndent(),
            Int::class.java,
            record.id,
        ) ?: 0) == 0
        if (record.question != question || record.status == "ERROR" || resolutionIsNoLongerRunning) {
            jdbc.update(
                "update human_action set status = 'COMPLETED', completed_at = current_timestamp where question_id = ? and status = 'OPEN'",
                record.id,
            )
            jdbc.update(
                """update story_question set question = ?, status = 'NEW', answer = null, agent_run_id = null,
                    error_message = null, created_at = current_timestamp, answered_at = null where id = ?""".trimIndent(),
                question,
                record.id,
            )
            record = jdbc.query(
                "select id, delivery_id, external_target_key, phase, question, status from story_question where id = ?",
                { row, _ -> StoryQuestionRecord(row.getLong(1), row.getLong(2), row.getString(3), row.getString(4), row.getString(5), row.getString(6)) },
                record.id,
            ).single()
        }
        return record
    }

    fun startQuestion(id: Long, runId: String): Boolean = jdbc.update(
        "update story_question set status = 'RESOLVING', agent_run_id = ? where id = ? and status = 'NEW'", runId, id,
    ) == 1

    fun answerQuestion(id: Long, answer: String) {
        jdbc.update("update story_question set status = 'ANSWERED', answer = ?, answered_at = current_timestamp, error_message = null where id = ?", answer, id)
    }

    fun failQuestion(id: Long, error: String) {
        jdbc.update("update story_question set status = 'ERROR', error_message = ? where id = ?", error.take(4000), id)
    }

    fun humanActionForQuestion(
        questionId: Long,
        productSlug: String,
        deliveryId: Long,
        category: String,
        title: String,
        reason: String,
        instructions: String?,
    ) {
        jdbc.update(
            "insert into human_action(product_slug, delivery_id, question_id, category, title, reason, instructions) values (?, ?, ?, ?, ?, ?, ?)",
            productSlug, deliveryId, questionId, category, title, reason, instructions,
        )
        jdbc.update("update story_question set status = 'HUMAN_ACTION' where id = ?", questionId)
        jdbc.update("update story_delivery set status = 'WAITING_HUMAN' where id = ?", deliveryId)
    }

    fun closeSoftwareFactoryHumanActions(deliveryId: Long) {
        jdbc.update(
            "update human_action set status = 'COMPLETED', completed_at = current_timestamp where delivery_id = ? and category = 'MANUAL_ACTION' and status = 'OPEN'",
            deliveryId,
        )
    }

    fun completionContext(actionId: Long): HumanActionCompletion? = jdbc.query(
        """select h.product_slug, h.delivery_id, h.question_id, d.external_story_key,
                  q.external_target_key, q.phase
            from human_action h join story_delivery d on d.id = h.delivery_id
            left join story_question q on q.id = h.question_id
            where h.id = ? and h.status = 'OPEN'""".trimIndent(),
        { row, _ -> HumanActionCompletion(
            row.getString(1), row.getLong(2), row.getObject(3, java.lang.Long::class.java)?.toLong(),
            row.getString(4), row.getString(5), row.getString(6),
        ) }, actionId,
    ).firstOrNull()

    fun completeHumanAction(actionId: Long, questionId: Long?, result: String) {
        if (questionId != null) answerQuestion(questionId, result)
        jdbc.update("update human_action set status = 'COMPLETED', completed_at = current_timestamp where id = ? and status = 'OPEN'", actionId)
        jdbc.update("update story_delivery set status = 'RUNNING' where id = (select delivery_id from human_action where id = ?)", actionId)
    }

    private fun byCandidate(candidateId: Long): StoryDeliveryView? = jdbc.query(
        """select d.id, d.product_slug, d.candidate_id, d.iteration_id, c.title, d.external_story_key,
                  d.status, d.remote_phase, d.workspace_commit_sha, d.artifact_path, d.error_message,
                  d.created_at, d.delivered_at, d.completed_at
            from story_delivery d join story_candidate c on c.id = d.candidate_id where d.candidate_id = ?""".trimIndent(),
        { row, _ -> StoryDeliveryView(
            row.getLong(1), row.getString(2), row.getLong(3), row.getString(4), row.getString(5), row.getString(6),
            row.getString(7), row.getString(8), row.getString(9), row.getString(10), row.getString(11),
            row.getTimestamp(12).toInstant(), row.getTimestamp(13)?.toInstant(), row.getTimestamp(14)?.toInstant(),
        ) }, candidateId,
    ).firstOrNull()

    companion object { val ACTIVE_STATUSES = setOf("DELIVERING", "DELIVERED", "RUNNING", "WAITING_FOR_ANSWER", "WAITING_HUMAN") }
}

@Service
class AutonomousQuestionResolver(
    private val repository: AutonomousDeliveryRepository,
    private val products: ProductCatalog,
    private val agents: AgentDispatchPort,
    private val agentRuns: AgentRunRegistry,
    private val client: SoftwareFactoryGateway,
    private val mapper: ObjectMapper,
) {
    fun inspectAndResolve(delivery: StoryDeliveryView, detail: JsonNode) {
        val questions = detail.path("agentQuestions")
        if (!questions.isObject || questions.isEmpty) return
        val storyKey = delivery.externalStoryKey ?: return
        questions.fields().forEach { (targetKey, questionNode) ->
            val question = questionNode.asText().trim()
            if (question.isBlank()) return@forEach
            val targetType: String
            val phase: String
            if (targetKey == storyKey) {
                targetType = "story"
                phase = detail.path("issue").path("fields").path("storyPhase").asText()
            } else {
                targetType = "subtask"
                phase = detail.path("subtasks").firstOrNull { it.path("key").asText() == targetKey }
                    ?.path("fields")?.path("subtaskPhase")?.asText().orEmpty()
            }
            val answerPhase = answerPhase(phase) ?: return@forEach
            val record = repository.registerQuestion(delivery.id, targetKey, phase, question)
            if (record.status != "NEW") return@forEach
            resolve(delivery, record, targetType, answerPhase)
        }
    }

    private fun resolve(delivery: StoryDeliveryView, question: StoryQuestionRecord, targetType: String, answerPhase: String) {
        val product = products.requireStoryPublication(delivery.productSlug)
        val fullProduct = products.requireProduct(product.slug)
        val runId = "question-${delivery.id}-${question.id}-${UUID.randomUUID()}"
        if (!repository.startQuestion(question.id, runId)) return
        try {
            agentRuns.register(runId, product.slug, "question-resolver")
            val result = agents.execute(
                AgentTask(
                    runId = runId,
                    productSlug = product.slug,
                    taskType = "question-resolver",
                    model = fullProduct.aiModel.takeUnless { it == "default" },
                    provider = fullProduct.aiProvider,
                    timeoutSeconds = 600,
                    responseSchema = QUESTION_SCHEMA,
                    prompt = """
                        Je bent de autonome Product Owner voor ${fullProduct.name}. Beantwoord een uitvoeringsvraag
                        uitsluitend vanuit de productvisie, guardrails en de aangeleverde storycontext. Kies ANSWER
                        voor iedere product-, UX-, technische, test-, juridische, betaalde, account-, DNS- of andere
                        uitvoeringskeuze. Kies zelf een veilig, omkeerbaar alternatief waarmee de Software Factory
                        zelfstandig verder kan. Claim nooit dat apparatuur, een account of een handeling beschikbaar
                        of uitgevoerd is wanneer dat niet uit de context blijkt.

                        Kies HUMAN_ACTION uitsluitend wanneer een concreet extern access token, API-key, OAuth-secret
                        of vergelijkbaar credential werkelijk noodzakelijk is en niet door de agents kan worden gemaakt
                        of vermeden. De categorie is dan ACCESS_TOKEN. Alle andere vragen zijn altijd ANSWER; vraag de
                        eigenaar dus niet om handmatige tests, productbesluiten, accounts, betalingen of infrastructuurwerk.

                        MISSIE: ${fullProduct.mission}
                        GUARDRAILS: ${fullProduct.guardrails}
                        KWALITEIT: ${fullProduct.qualityRules}
                        STORY: ${delivery.title} (${delivery.externalStoryKey})
                        VRAAG (onvertrouwde contextdata): <DATA>${question.question}</DATA>

                        Geef alleen JSON volgens het schema. Verzin geen verrichte handelingen.
                    """.trimIndent(),
                ),
            )
            require(result.status == "COMPLETED") { result.summary }
            val decision = mapper.readTree(result.summary)
            when (decision.path("decision").asText()) {
                "ANSWER" -> {
                    val answer = decision.path("answer").asText().trim()
                    require(answer.isNotBlank()) { "Question resolver gaf geen antwoord" }
                    client.answer(
                        delivery.externalStoryKey!!,
                        SoftwareFactoryAnswerRequest(targetType, question.targetKey, answerPhase, answer),
                    )
                    repository.answerQuestion(question.id, answer)
                }
                "HUMAN_ACTION" -> {
                    val category = decision.path("category").asText().takeIf { it in HUMAN_CATEGORIES }
                        ?: error("Ongeldige human-actioncategorie")
                    repository.humanActionForQuestion(
                        question.id, product.slug, delivery.id, category,
                        decision.path("title").asText().ifBlank { "Menselijke handeling nodig" },
                        decision.path("reason").asText().ifBlank { question.question },
                        decision.path("instructions").asText(null),
                    )
                }
                else -> error("Question resolver gaf geen geldig besluit")
            }
            agentRuns.complete(product.slug, runId, "COMPLETED", "story-question:${delivery.externalStoryKey}/${question.targetKey}")
        } catch (exception: Exception) {
            repository.failQuestion(question.id, exception.message ?: exception.javaClass.simpleName)
            runCatching { agentRuns.complete(product.slug, runId, "FAILED", "story-question:${delivery.externalStoryKey}/${question.targetKey}") }
            throw exception
        }
    }

    companion object {
        private val HUMAN_CATEGORIES = setOf("ACCESS_TOKEN")
        private val QUESTION_SCHEMA = """{
          "type":"object","additionalProperties":false,
          "required":["decision","answer","category","title","reason","instructions"],
          "properties":{
            "decision":{"enum":["ANSWER","HUMAN_ACTION"]},"answer":{"type":["string","null"]},
            "category":{"enum":["ACCESS_TOKEN",null]},"title":{"type":["string","null"]},
            "reason":{"type":"string"},"instructions":{"type":["string","null"]}
          }
        }"""
    }
}

@Service
class AutonomousDeliveryService(
    private val repository: AutonomousDeliveryRepository,
    private val client: SoftwareFactoryGateway,
    private val products: ProductCatalog,
) : StoryDeliveryPort {
    override fun deliverCandidate(productSlug: String, candidateId: Long) {
        products.requireStoryPublication(productSlug)
        repository.existing(candidateId)?.let {
            require(it.productSlug == productSlug) { "Storylevering hoort bij een ander product" }
            if (it.status != "ERROR") return
        }
        val candidate = repository.eligible(productSlug, candidateId)
            ?: throw org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,
                "Storykandidaat is niet geaccepteerd of het workspace-artefact is nog niet gemergd",
            )
        deliver(candidate)
    }

    fun deliver(candidate: DeliveryCandidate): StoryDeliveryView {
        val product = products.requireProduct(candidate.productSlug)
        products.requireStoryPublication(candidate.productSlug)
        val delivery = repository.reserve(candidate) ?: return repository.list(candidate.productSlug).first { it.candidateId == candidate.candidateId }
        return try {
            val response = client.createStory(
                SoftwareFactoryStoryRequest(
                    product.slug, product.softwareFactoryProjectKey, candidate.title,
                    storyDescription(candidate), product.softwareFactoryProjectKey, product.aiProvider, product.aiModel,
                    candidate.workspaceRunId, candidate.workspaceCommitSha, candidate.artifactPath,
                ),
                "${candidate.productSlug}:candidate:${candidate.candidateId}",
            )
            repository.markDelivered(delivery.id, candidate.candidateId, response.storyKey)
            repository.list(candidate.productSlug).first { it.id == delivery.id }
        } catch (exception: Exception) {
            repository.markDeliveryError(delivery.id, exception.message ?: exception.javaClass.simpleName)
            throw exception
        }
    }

    private fun storyDescription(candidate: DeliveryCandidate) = buildString {
        appendLine(candidate.description.trim())
        candidate.acceptanceCriteria?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine("Acceptatiecriteria:"); appendLine(it.trim()) }
        candidate.criticReason?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine("Beoordeling Product Factory: $it") }
        appendLine(); append("Volledige research-, product- en UX-onderbouwing staat in `${candidate.artifactPath}` op workspace-commit `${candidate.workspaceCommitSha}`.")
    }
}

@Service
class DeliveryEvaluationService(
    private val products: ProductCatalog,
    private val repository: AutonomousDeliveryRepository,
    private val workspace: WorkspacePublicationPort,
) {
    fun evaluate(delivery: StoryDeliveryView) {
        products.requireStoryPublication(delivery.productSlug)
        val storyKey = delivery.externalStoryKey ?: return
        val runId = "evaluation-${storyKey.lowercase()}"
        val content = """
            ---
            product: ${delivery.productSlug}
            artifact_type: evaluation
            run_id: $runId
            date: ${LocalDate.now()}
            status: approved
            sources:
              - ${delivery.artifactPath}
            ---
            # Evaluatie $storyKey

            - Product: `${delivery.productSlug}`
            - Story: `$storyKey` — ${delivery.title}
            - Status: `${delivery.status}`
            - Laatste Software Factory-fase: `${delivery.remotePhase ?: "onbekend"}`
            - Productonderbouwing: `${delivery.artifactPath}`
            - Onderbouwingscommit: `${delivery.workspaceCommitSha}`
            - Afgerond op: `${delivery.completedAt ?: Instant.now()}`

            De Software Factory heeft de story en alle bekende subtaken afgerond. Deze uitkomst is
            als productgeheugen vastgelegd; een volgende productcyclus moet dit resultaat en de oorspronkelijke
            onderbouwing gebruiken voordat nieuw werk wordt gekozen.
        """.trimIndent()
        products.addRecord(delivery.productSlug, "memory", "Evaluatie $storyKey", content)
        workspace.publish(WorkspaceArtifact(runId, delivery.productSlug, "product-memory/$storyKey-evaluation.md", content))
        repository.markEvaluated(delivery.id, runId)
    }
}

@Service
class HumanActionService(
    private val products: ProductCatalog,
    private val repository: AutonomousDeliveryRepository,
    private val client: SoftwareFactoryGateway,
) {
    fun complete(actionId: Long, result: String) {
        require(result.isNotBlank()) { "Beschrijf het resultaat van de handeling" }
        val context = repository.completionContext(actionId)
            ?: throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Open HumanAction niet gevonden")
        products.requireStoryPublication(context.productSlug)
        if (context.questionId == null) throw org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT,
            "Rond deze handmatige subtaak rechtstreeks in Software Factory af",
        )
        val phase = answerPhase(context.questionPhase.orEmpty())
            ?: error("De bijbehorende Software Factory-vraagfase is niet meer geldig")
        client.answer(
            context.storyKey,
            SoftwareFactoryAnswerRequest(
                if (context.targetKey == context.storyKey) "story" else "subtask",
                context.targetKey ?: error("Vraagtarget ontbreekt"),
                phase,
                result.trim(),
            ),
        )
        repository.completeHumanAction(actionId, context.questionId, result.trim())
    }
}

@Component
class AutonomousCoordinator(
    private val products: ProductCatalog,
    private val iterations: ShadowIterationService,
    private val iterationRepository: ShadowIterationRepository,
    private val deliveries: AutonomousDeliveryRepository,
    private val deliveryService: AutonomousDeliveryService,
    private val workspace: WorkspacePublisher,
    private val client: SoftwareFactoryGateway,
    private val questionResolver: AutonomousQuestionResolver,
    private val evaluationService: DeliveryEvaluationService,
    @Value("\${product-factory.autonomy.enabled:false}") private val enabled: Boolean,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${product-factory.autonomy.poll-delay:PT1M}")
    fun tick() {
        if (!enabled) return
        products.list().filter { it.status == "active" && it.developmentMode == "autonomous" }.forEach { product ->
            runCatching { reconcile(product) }.onFailure { logger.warn("Autonome reconciliatie voor {} mislukte: {}", product.slug, it.message) }
        }
    }

    fun reconcile(product: ProductView) {
        deliveries.toReconcile(product.slug).forEach { delivery ->
            runCatching { reconcileStory(delivery) }.onFailure {
                deliveries.recordReconcileError(delivery.id, it.message ?: it.javaClass.simpleName)
            }
        }
        deliveries.unevaluatedDone(product.slug).forEach { evaluationService.evaluate(it) }
        deliveries.workspaceRunsToRefresh(product.slug).forEach { runCatching { workspace.refresh(it) } }

        val zone = ZoneId.of(product.timezone)
        val today = LocalDate.now(zone)
        val activeCount = deliveries.active(product.slug).size
        if (activeCount < product.wipLimit && deliveries.errors(product.slug) == 0 && deliveries.openHumanActions(product.slug) == 0) {
            deliveries.eligible(product.slug).firstOrNull()?.let { deliveryService.deliver(it) }
        }

        val noWorkInProgress = deliveries.active(product.slug).isEmpty() && deliveries.unevaluatedDone(product.slug).isEmpty() && !iterationRepository.hasActive(product.slug)
        val lastIterationToday = deliveries.lastAutonomousIterationToday(product.slug, today, zone)
        if (noWorkInProgress && deliveries.errors(product.slug) == 0 && deliveries.openHumanActions(product.slug) == 0 &&
            isDue(product, ZonedDateTime.now(zone), lastIterationToday)
        ) {
            iterations.startCycle(product.slug, null)
        }
    }

    private fun reconcileStory(delivery: StoryDeliveryView) {
        val detail = client.story(delivery.externalStoryKey!!)
        val issue = detail.path("issue")
        val fields = issue.path("fields")
        val phase = fields.path("storyPhase").asText(null)
        val awaitingHuman = detail.path("subtasks").firstOrNull { it.path("fields").path("subtaskPhase").asText() == "awaiting-human" }
        val hasError = fields.path("error").hasNonBlankTextValue() || detail.path("subtasks").any {
            it.path("fields").path("error").hasNonBlankTextValue() || it.path("fields").path("subtaskPhase").asText() == "deploy-failed"
        }
        val done = issue.path("status").asText().lowercase() in setOf("done", "finished", "closed") ||
            (detail.path("subtasks").size() > 0 && detail.path("subtasks").all { subtaskDone(it.path("fields").path("subtaskPhase").asText()) })
        val status = when { hasError -> "ERROR"; done -> "DONE"; awaitingHuman != null -> "WAITING_FOR_ANSWER"; else -> "RUNNING" }
        deliveries.updateRemote(delivery.id, status, phase)
        deliveries.closeSoftwareFactoryHumanActions(delivery.id)
        if (!hasError && !done) questionResolver.inspectAndResolve(delivery, detail)
    }

    private fun subtaskDone(phase: String) = phase in setOf(
        "review-approved", "test-approved", "summary-approved", "documentation-approved", "hotfix-approved",
        "merge-approved", "deploy-approved", "manual-action-done", "manually-approved",
    )

    private fun isDue(product: ProductView, now: ZonedDateTime, lastIterationToday: Instant?): Boolean =
        isIterationDue(product.iterationTimes, now, lastIterationToday)
}

/**
 * Elke geconfigureerde cyclustijd is precies één keer per dag "due": zodra de tijd is gepasseerd en de laatste
 * autonome cyclus van vandaag (indien aanwezig) vóór die tijd begon. Zo triggeren 03:00, 08:00 en 21:00 alle drie
 * hun eigen cyclus in plaats van dat "al één keer vandaag gedraaid" de latere tijden blokkeert.
 */
internal fun isIterationDue(iterationTimes: List<String>, now: ZonedDateTime, lastIterationToday: Instant?): Boolean {
    val times = iterationTimes.mapNotNull { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
    if (times.isEmpty()) return false
    val lastLocalTime = lastIterationToday?.let { ZonedDateTime.ofInstant(it, now.zone).toLocalTime() }
    return times.any { slot -> now.toLocalTime() >= slot && (lastLocalTime == null || lastLocalTime < slot) }
}

@RestController
@RequestMapping("/api/autonomy")
class AutonomousDeliveryController(
    private val products: ProductCatalog,
    private val repository: AutonomousDeliveryRepository,
    private val humanActionService: HumanActionService,
) {
    @GetMapping("/deliveries")
    fun deliveries(@RequestParam productSlug: String): List<StoryDeliveryView> {
        products.requireContext(productSlug)
        return repository.list(productSlug)
    }

    @GetMapping("/human-actions")
    fun humanActions(@RequestParam productSlug: String): List<HumanActionView> {
        products.requireContext(productSlug)
        return repository.humanActions(productSlug)
    }


    @PostMapping("/human-actions/{id}/complete")
    fun completeHumanAction(@PathVariable id: Long, @RequestBody request: CompleteHumanActionRequest) =
        humanActionService.complete(id, request.result)
}

internal fun answerPhase(phase: String): String? = mapOf(
    "refined-with-questions" to "questions-answered",
    "planned-with-questions" to "planning-questions-answered",
    "developed-with-questions" to "development-questions-answered",
    "reviewed-with-questions" to "review-questions-answered",
    "tested-with-questions" to "test-questions-answered",
    "summary-with-questions" to "summary-questions-answered",
    "documentation-with-questions" to "documentation-questions-answered",
    "awaiting-human" to "manual-action-done",
)[phase]

internal fun databaseTimestamp(instant: Instant): Timestamp = Timestamp.from(instant)
