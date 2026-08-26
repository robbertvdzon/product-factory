package nl.vdzon.productfactory.product

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import nl.vdzon.productfactory.api.ai.*
import nl.vdzon.productfactory.api.decisions.CreateDecisionCommand
import nl.vdzon.productfactory.api.decisions.DecisionOrigin
import nl.vdzon.productfactory.api.decisions.DecisionQueryService
import nl.vdzon.productfactory.api.decisions.DecisionService
import nl.vdzon.productfactory.api.memory.*
import nl.vdzon.productfactory.api.product.*
import nl.vdzon.productfactory.api.shared.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration

data class MeetingAiWorkDetails(val taskId: AiTaskId, val meetingId: MeetingId, val type: String, val status: String)

@Service
class MeetingAiOrchestrator(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
    private val clock: Clock,
    private val products: ProductCommandService,
    private val productQueries: ProductQueryService,
    private val decisions: DecisionService,
    private val decisionQueries: DecisionQueryService,
    private val memory: AgentMemoryService,
    private val memoryQueries: AgentMemoryQueryService,
    private val ai: AiExecutionService,
    private val aiQueries: AiExecutionQueryService,
    transactionManager: PlatformTransactionManager,
) {
    private val transactions = TransactionTemplate(transactionManager)

    @Transactional
    fun addStakeholderMessage(meetingId: MeetingId, request: MeetingMessageRequest, actor: ActorReference): MeetingAiWorkDetails {
        existing(request.idempotencyKey)?.let { return it }
        products.recordMeetingMessage(RecordMeetingMessageCommand(
            meetingId, MeetingSenderRole.STAKEHOLDER, request.text, null, request.expectedVersion, actor, request.idempotencyKey,
        ))
        val meeting = productQueries.getMeeting(meetingId)
        return requestWork(meeting, "CONVERSE", request.targetAgentRole ?: "MEETING_AGENT", request.idempotencyKey)
    }

    @Transactional
    fun requestMinutes(meetingId: MeetingId, request: CloseMeetingRequest): MeetingAiWorkDetails {
        existing(request.idempotencyKey)?.let { return it }
        val meeting = productQueries.getMeeting(meetingId)
        if (meeting.status != MeetingStatus.OPEN) throw InvalidCommand("Alleen een open overleg kan door de notulenagent worden verwerkt.")
        if (meeting.version != request.expectedVersion) throw VersionConflict("Het overleg is intussen gewijzigd.")
        return requestWork(meeting, "SUMMARIZE", "MEETING_MINUTES_AGENT", request.idempotencyKey)
    }

    fun resumeReady(limit: Int = 20) {
        jdbc.query(
            """SELECT w.idempotency_key FROM pf_meeting_ai_work w JOIN pf_ai_task t ON t.id=w.task_id
                WHERE w.status='WAITING_FOR_AI' AND t.status IN ('SUCCEEDED','FAILED','CANCELLED') ORDER BY w.created_at""".trimIndent(),
            { rs, _ -> rs.getString(1) },
        ).take(limit).forEach { idempotencyKey ->
            runCatching {
                transactions.executeWithoutResult { resumeOne(idempotencyKey) }
            }.onFailure {
                transactions.executeWithoutResult {
                    jdbc.update(
                        "UPDATE pf_meeting_ai_work SET status='FAILED',safe_error=?,applied_at=? WHERE idempotency_key=? AND status='WAITING_FOR_AI'",
                        "AI_RESULT_APPLY_FAILED", clock.instant(), idempotencyKey,
                    )
                }
            }
        }
    }

    @Transactional
    fun resumeOne(idempotencyKey: String) {
        val row = jdbc.query(
            "SELECT meeting_id,work_type,target_agent_role,task_id,status FROM pf_meeting_ai_work WHERE idempotency_key=?",
            { rs, _ -> WorkRow(MeetingId(rs.getString(1)), rs.getString(2), rs.getString(3), AiTaskId(rs.getString(4)), rs.getString(5)) }, idempotencyKey,
        ).singleOrNull() ?: return
        if (row.status != "WAITING_FOR_AI") return
        val task = aiQueries.getAiTask(row.taskId)
        if (task.status == AiTaskStatus.FAILED || task.status == AiTaskStatus.CANCELLED) {
            jdbc.update(
                "UPDATE pf_meeting_ai_work SET status='FAILED',safe_error=?,applied_at=? WHERE idempotency_key=? AND status='WAITING_FOR_AI'",
                (task.errorCode ?: task.status.name).take(1000), clock.instant(), idempotencyKey,
            )
            return
        }
        if (task.status != AiTaskStatus.SUCCEEDED) return
        val result = aiQueries.getAiTaskResult(row.taskId)?.responseJson?.let(mapper::readTree)
            ?: throw InvalidCommand("De geslaagde overlegtaak heeft geen JSON-resultaat.")
        when (row.type) {
            "CONVERSE" -> applyConversation(row, result, idempotencyKey)
            "SUMMARIZE" -> applyMinutes(row, result, idempotencyKey)
        }
        jdbc.update(
            "UPDATE pf_meeting_ai_work SET status='APPLIED',safe_error=NULL,applied_at=? WHERE idempotency_key=? AND status='WAITING_FOR_AI'",
            clock.instant(), idempotencyKey,
        )
    }

    private fun requestWork(meeting: MeetingDetails, type: String, targetRole: String, idempotencyKey: String): MeetingAiWorkDetails {
        val configuration = aiQueries.getAiJobConfiguration(AiJobKey(if (type == "CONVERSE") "MEETING.CONVERSE" else "MEETING.SUMMARIZE"))
        val prompt = prompt(meeting, type, targetRole)
        val taskId = ai.requestAiTask(RequestAiTaskCommand(
            configuration.jobKey, meeting.productId, "stakeholder-meeting", null, targetRole,
            configuration.provider, configuration.model, configuration.version, PROMPT_TEMPLATE_VERSION,
            prompt, if (type == "CONVERSE") CONVERSATION_SCHEMA else MINUTES_SCHEMA,
            executionTimeout = Duration.ofMinutes(15), idempotencyKey = "meeting-$type-$idempotencyKey".take(150),
        ))
        memoryQueries.getMeetingMemorySnapshot(MeetingExecutionContext(meeting.productId, meeting.id, taskId, ORCHESTRATOR))
        jdbc.update(
            "INSERT INTO pf_meeting_ai_work(idempotency_key,meeting_id,work_type,source_meeting_version,target_agent_role,task_id,status,created_at) VALUES (?,?,?,?,?,?,?,?)",
            idempotencyKey, meeting.id.value, type, meeting.version, targetRole, taskId.value, "WAITING_FOR_AI", clock.instant(),
        )
        return MeetingAiWorkDetails(taskId, meeting.id, type, "WAITING_FOR_AI")
    }

    private fun prompt(meeting: MeetingDetails, type: String, targetRole: String): String {
        val roleCatalog = memoryQueries.getAgentRoleCatalog(meeting.productId)
        if (roleCatalog.none { it.key.value == targetRole }) throw InvalidCommand("De gekozen overlegagentrol is niet actief.")
        val context = linkedMapOf<String, Any?>(
            "product" to productQueries.getProduct(meeting.productId),
            "assignment" to runCatching { productQueries.getProductAssignment(meeting.productId) }.getOrNull(),
            "meeting" to meeting,
            "agentRoles" to roleCatalog,
            "openQuestions" to productQueries.findStakeholderQuestions(StakeholderQuestionFilter(meeting.productId, statuses = setOf(StakeholderQuestionStatus.OPEN))),
            "decisions" to decisionQueries.getDecisions(meeting.productId),
            "memory" to roleCatalog.associate { role -> role.key.value to memoryQueries.getMemoryAt(meeting.productId, role.key, clock.instant()) },
        )
        val instruction = if (type == "CONVERSE") {
            "Reageer brongetrouw als de gevraagde agentrol $targetRole op het nieuwste Stakeholderbericht. Stel een concrete vervolgvraag wanneer informatie ontbreekt. Voeg geen credentials of keynamen toe."
        } else {
            "Maak brongetrouwe notulen en uitsluitend uitvoerbare voorstellen voor antwoorden, besluiten en een atomaire geheugenbatch. Verzin geen uitkomsten."
        }
        return """$instruction

Gebruik uitsluitend deze geldige, server-side samengestelde overlegcontext:
${mapper.writerWithDefaultPrettyPrinter().writeValueAsString(context)}

Geef uitsluitend JSON volgens het opgegeven responseschema terug."""
    }

    private fun applyConversation(row: WorkRow, result: JsonNode, idempotencyKey: String) {
        val message = result.path("message").asText().trim()
        val representedRole = result.path("representedAgentRole").asText(row.targetRole).trim()
        if (message.isBlank() || representedRole != row.targetRole) throw InvalidCommand("Overlegagentresultaat is leeg of beantwoordt als een andere rol.")
        val meeting = productQueries.getMeeting(row.meetingId)
        products.recordMeetingMessage(RecordMeetingMessageCommand(
            row.meetingId, MeetingSenderRole.MEETING_AGENT, message, representedRole, meeting.version,
            ORCHESTRATOR, "apply-conversation-$idempotencyKey",
        ))
    }

    private fun applyMinutes(row: WorkRow, result: JsonNode, idempotencyKey: String) {
        val minutes = result.path("minutes").asText().trim()
        if (minutes.isBlank()) throw InvalidCommand("Notulenagentresultaat bevat geen notulen.")
        val meetingBefore = productQueries.getMeeting(row.meetingId)
        val outcomes = mutableListOf<MeetingOutcomeDetails>()

        result.path("answers").takeIf(JsonNode::isArray)?.forEachIndexed { index, answer ->
            val questionId = StakeholderQuestionId(answer.path("questionId").asText())
            val text = answer.path("answer").asText().trim()
            val question = productQueries.getStakeholderQuestion(questionId)
            if (question.productId != meetingBefore.productId || question.status != StakeholderQuestionStatus.OPEN || text.isBlank()) {
                throw InvalidCommand("Notulenagent leverde een ongeldig vraagantwoord.")
            }
            val meeting = productQueries.getMeeting(row.meetingId)
            products.recordMeetingMessage(RecordMeetingMessageCommand(
                row.meetingId, MeetingSenderRole.MEETING_AGENT, text, question.agentRole, meeting.version,
                MINUTES_AGENT, "minutes-answer-message-$idempotencyKey-$index",
            ))
            val messageId = productQueries.getMeeting(row.meetingId).messages.last().id
            products.recordStakeholderAnswer(RecordStakeholderAnswerCommand(
                questionId, row.meetingId, messageId, text, question.version, MINUTES_AGENT,
                "minutes-answer-$idempotencyKey-$index",
            ))
            outcomes += MeetingOutcomeDetails("Vraag ${questionId.value} beantwoord", "recordStakeholderAnswer", SourceReference("STAKEHOLDER_QUESTION", questionId.value, question.version), MeetingOutcomeStatus.SUCCEEDED)
        }

        result.path("decisions").takeIf(JsonNode::isArray)?.forEachIndexed { index, decisionNode ->
            val text = if (decisionNode.isTextual) decisionNode.asText() else decisionNode.path("decision").asText()
            if (text.isBlank()) throw InvalidCommand("Notulenagent leverde een leeg besluit.")
            val decisionId = decisions.createDecision(CreateDecisionCommand(
                meetingBefore.productId, text.trim(), DecisionOrigin.FACTORY, MINUTES_AGENT, "minutes-decision-$idempotencyKey-$index",
            ))
            outcomes += MeetingOutcomeDetails("Besluit vastgelegd", "createDecision", SourceReference("DECISION", decisionId.value, 1), MeetingOutcomeStatus.SUCCEEDED)
        }

        val changes = result.path("memoryChanges").takeIf(JsonNode::isArray)?.map { change ->
            MeetingMemoryChange(
                AgentRoleKey(change.path("agentRole").asText()), MemoryChangeType.valueOf(change.path("type").asText()),
                change.path("itemId").takeIf { !it.isMissingNode && !it.isNull }?.asText()?.let(::MemoryItemId),
                change.path("expectedVersionId").takeIf { !it.isMissingNode && !it.isNull }?.asText()?.let(::MemoryVersionId),
                change.path("title").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
                change.path("content").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
                change.path("reason").asText(),
            )
        }.orEmpty()
        if (changes.isNotEmpty()) {
            val changed = memory.applyMeetingMemoryChanges(ApplyMeetingMemoryChangesCommand(
                MeetingExecutionContext(meetingBefore.productId, row.meetingId, row.taskId, MINUTES_AGENT), changes,
                "minutes-memory-$idempotencyKey",
            ))
            outcomes += changed.versionIds.map { version -> MeetingOutcomeDetails("Agentgeheugen bijgewerkt", "applyMeetingMemoryChanges", SourceReference("MEMORY_VERSION", version.value, 1), MeetingOutcomeStatus.SUCCEEDED) }
        }
        val meeting = productQueries.getMeeting(row.meetingId)
        products.closeMeeting(CloseMeetingCommand(row.meetingId, minutes, outcomes, meeting.version, MINUTES_AGENT, "minutes-close-$idempotencyKey"))
    }

    private fun existing(idempotencyKey: String): MeetingAiWorkDetails? = jdbc.query(
        "SELECT task_id,meeting_id,work_type,status FROM pf_meeting_ai_work WHERE idempotency_key=?",
        { rs, _ -> MeetingAiWorkDetails(AiTaskId(rs.getString(1)), MeetingId(rs.getString(2)), rs.getString(3), rs.getString(4)) }, idempotencyKey,
    ).singleOrNull()

    private data class WorkRow(val meetingId: MeetingId, val type: String, val targetRole: String, val taskId: AiTaskId, val status: String)

    companion object {
        private const val PROMPT_TEMPLATE_VERSION = 1L
        private val ORCHESTRATOR = ActorReference(ActorType.SYSTEM, "meeting-ai-orchestrator")
        private val MINUTES_AGENT = ActorReference(ActorType.MEETING_MINUTES_AGENT, "meeting-minutes-agent")
        private const val CONVERSATION_SCHEMA = """{"type":"object","additionalProperties":false,"required":["message","representedAgentRole"],"properties":{"message":{"type":"string","minLength":1},"representedAgentRole":{"type":"string","minLength":1}}}"""
        private const val MINUTES_SCHEMA = """{"type":"object","additionalProperties":false,"required":["minutes","answers","decisions","memoryChanges"],"properties":{"minutes":{"type":"string","minLength":1},"answers":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["questionId","answer"],"properties":{"questionId":{"type":"string"},"answer":{"type":"string"}}}},"decisions":{"type":"array","items":{"type":"string"}},"memoryChanges":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["agentRole","type","itemId","expectedVersionId","title","content","reason"],"properties":{"agentRole":{"type":"string"},"type":{"enum":["ADD","REPLACE","RETRACT"]},"itemId":{"type":["string","null"]},"expectedVersionId":{"type":["string","null"]},"title":{"type":["string","null"]},"content":{"type":["string","null"]},"reason":{"type":"string"}}}}}}"""
    }
}

@Component
class MeetingAiCoordinator(
    private val orchestrator: MeetingAiOrchestrator,
    @Value("\${PF_AI_RUNTIME_SCHEDULING_ENABLED:false}") private val enabled: Boolean,
) {
    @Scheduled(fixedDelayString = "\${PF_MEETING_AI_RECONCILE_DELAY_MS:2000}")
    fun resume() {
        if (enabled) orchestrator.resumeReady()
    }
}
