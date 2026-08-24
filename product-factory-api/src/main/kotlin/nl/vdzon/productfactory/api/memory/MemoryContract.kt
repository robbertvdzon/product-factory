package nl.vdzon.productfactory.api.memory

import nl.vdzon.productfactory.api.shared.*
import java.time.Instant

enum class MemoryChangeType { ADDED, REPLACED, RETRACTED }
data class AgentRoleDefinitionDetails(val key: String, val displayName: String, val capability: String, val responsibilities: List<String>, val boundaries: List<String>, val active: Boolean)
data class AgentMemoryItemDetails(val id: MemoryItemId, val productId: ProductId, val agentRole: String, val activeVersionId: MemoryVersionId, val content: String, val createdAt: Instant)
data class AgentMemoryVersionDetails(
    val id: MemoryVersionId,
    val itemId: MemoryItemId,
    val changeType: MemoryChangeType,
    val content: String?,
    val actor: String,
    val reason: String,
    val validFrom: Instant,
    val validUntil: Instant? = null,
    val sourceMeetingId: MeetingId? = null,
)
data class AddAgentMemoryCommand(val productId: ProductId, val agentRole: String, val content: String, val actor: String, val reason: String, val idempotencyKey: String)
data class ReplaceAgentMemoryCommand(val itemId: MemoryItemId, val expectedVersionId: MemoryVersionId, val content: String, val actor: String, val reason: String, val idempotencyKey: String)
data class RetractAgentMemoryCommand(val itemId: MemoryItemId, val expectedVersionId: MemoryVersionId, val actor: String, val reason: String, val idempotencyKey: String)
data class MeetingExecutionContext(val productId: ProductId, val meetingId: MeetingId, val trustedActor: String)
data class MeetingMemorySnapshot(val productId: ProductId, val meetingId: MeetingId, val roles: List<AgentRoleDefinitionDetails>, val memory: Map<String, List<AgentMemoryItemDetails>>)
data class MeetingMemoryChange(val agentRole: String, val type: MemoryChangeType, val itemId: MemoryItemId? = null, val expectedVersionId: MemoryVersionId? = null, val content: String? = null, val reason: String)
data class ApplyMeetingMemoryChangesCommand(val context: MeetingExecutionContext, val changes: List<MeetingMemoryChange>, val idempotencyKey: String)
data class MeetingMemoryChangeResult(val versionIds: List<MemoryVersionId>)
interface AgentMemoryService {
    fun addAgentMemory(command: AddAgentMemoryCommand): MemoryItemId
    fun replaceAgentMemory(command: ReplaceAgentMemoryCommand): MemoryVersionId
    fun retractAgentMemory(command: RetractAgentMemoryCommand): MemoryVersionId
    fun applyMeetingMemoryChanges(command: ApplyMeetingMemoryChangesCommand): MeetingMemoryChangeResult
}
interface AgentMemoryQueryService {
    fun getActiveMemory(productId: ProductId, agentRole: String): List<AgentMemoryItemDetails>
    fun getMemoryAt(productId: ProductId, agentRole: String, validAt: Instant): List<AgentMemoryItemDetails>
    fun getMemoryHistory(productId: ProductId, agentRole: String): List<AgentMemoryVersionDetails>
    fun getAgentRoleCatalog(productId: ProductId): List<AgentRoleDefinitionDetails>
    fun getMeetingMemorySnapshot(context: MeetingExecutionContext): MeetingMemorySnapshot
}
