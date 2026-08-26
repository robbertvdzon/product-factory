package nl.vdzon.productfactory.api.memory

import nl.vdzon.productfactory.api.shared.*
import java.time.Instant

@JvmInline value class AgentRoleKey(val value: String)

enum class MemoryChangeType { ADD, REPLACE, RETRACT }
enum class MemoryVersionStatus { ACTIVE, SUPERSEDED, RETRACTED }

data class AgentExecutionContext(
    val productId: ProductId,
    val agentRole: AgentRoleKey,
    val processSessionId: ProcessSessionId,
    val aiTaskId: AiTaskId,
)

data class MeetingExecutionContext(
    val productId: ProductId,
    val meetingId: MeetingId,
    val aiTaskId: AiTaskId,
    val trustedActor: ActorReference,
)

data class MemoryWriteContext(
    val productId: ProductId,
    val agentRole: AgentRoleKey,
    val actor: ActorReference,
    val processSessionId: ProcessSessionId? = null,
    val aiTaskId: AiTaskId? = null,
    val sourceMeetingId: MeetingId? = null,
)

data class AgentRoleDefinitionDetails(
    val key: AgentRoleKey,
    val displayName: String,
    val capability: String,
    val implementationVariant: String,
    val purpose: String,
    val responsibilities: List<String>,
    val boundaries: List<String>,
    val active: Boolean,
)

data class AgentMemoryBudgetDetails(
    val productId: ProductId,
    val agentRole: AgentRoleKey,
    val maximumActiveItems: Int,
    val maximumItemCharacters: Int,
    val maximumTotalCharacters: Int,
    val usedItems: Int,
    val usedCharacters: Int,
)

data class MemoryReadReference(
    val processSessionId: ProcessSessionId? = null,
    val meetingId: MeetingId? = null,
    val aiTaskId: AiTaskId,
    val readAt: Instant,
)

data class AgentMemoryItemDetails(
    val id: MemoryItemId,
    val productId: ProductId,
    val agentRole: AgentRoleKey,
    val activeVersionId: MemoryVersionId,
    val title: String,
    val content: String,
    val validFrom: Instant,
    val actor: ActorReference,
    val reason: String,
    val sourceMeetingId: MeetingId? = null,
    val readBy: List<MemoryReadReference> = emptyList(),
)

data class AgentMemoryVersionDetails(
    val id: MemoryVersionId,
    val itemId: MemoryItemId,
    val productId: ProductId,
    val agentRole: AgentRoleKey,
    val versionNumber: Int,
    val status: MemoryVersionStatus,
    val title: String,
    val content: String,
    val predecessorId: MemoryVersionId? = null,
    val successorId: MemoryVersionId? = null,
    val actor: ActorReference,
    val reason: String,
    val validFrom: Instant,
    val validUntil: Instant? = null,
    val sourceMeetingId: MeetingId? = null,
    val retractionActor: ActorReference? = null,
    val retractionReason: String? = null,
    val readBy: List<MemoryReadReference> = emptyList(),
)

data class AddAgentMemoryCommand(
    val context: MemoryWriteContext,
    val title: String,
    val content: String,
    val reason: String,
    val idempotencyKey: String,
)

data class ReplaceAgentMemoryCommand(
    val context: MemoryWriteContext,
    val itemId: MemoryItemId,
    val expectedVersionId: MemoryVersionId,
    val title: String,
    val content: String,
    val reason: String,
    val idempotencyKey: String,
)

data class RetractAgentMemoryCommand(
    val context: MemoryWriteContext,
    val itemId: MemoryItemId,
    val expectedVersionId: MemoryVersionId,
    val reason: String,
    val idempotencyKey: String,
)

data class MeetingMemorySnapshot(
    val productId: ProductId,
    val meetingId: MeetingId,
    val roles: List<AgentRoleDefinitionDetails>,
    val memory: Map<AgentRoleKey, List<AgentMemoryItemDetails>>,
)

data class MeetingMemoryChange(
    val agentRole: AgentRoleKey,
    val type: MemoryChangeType,
    val itemId: MemoryItemId? = null,
    val expectedVersionId: MemoryVersionId? = null,
    val title: String? = null,
    val content: String? = null,
    val reason: String,
)

data class ApplyMeetingMemoryChangesCommand(
    val context: MeetingExecutionContext,
    val changes: List<MeetingMemoryChange>,
    val idempotencyKey: String,
)

data class MeetingMemoryChangeResult(val versionIds: List<MemoryVersionId>)

interface AgentMemoryService {
    fun addAgentMemory(command: AddAgentMemoryCommand): MemoryItemId
    fun replaceAgentMemory(command: ReplaceAgentMemoryCommand): MemoryVersionId
    fun retractAgentMemory(command: RetractAgentMemoryCommand): MemoryVersionId
    fun applyMeetingMemoryChanges(command: ApplyMeetingMemoryChangesCommand): MeetingMemoryChangeResult
}

interface AgentMemoryQueryService {
    fun getActiveMemory(context: AgentExecutionContext): List<AgentMemoryItemDetails>
    fun getMemoryAt(productId: ProductId, agentRole: AgentRoleKey, validAt: Instant): List<AgentMemoryItemDetails>
    fun getMemoryHistory(productId: ProductId, agentRole: AgentRoleKey, memoryItemId: MemoryItemId): List<AgentMemoryVersionDetails>
    fun getAgentRoleCatalog(productId: ProductId): List<AgentRoleDefinitionDetails>
    fun getMemoryBudget(productId: ProductId, agentRole: AgentRoleKey): AgentMemoryBudgetDetails
    fun getMeetingMemorySnapshot(context: MeetingExecutionContext): MeetingMemorySnapshot
}
