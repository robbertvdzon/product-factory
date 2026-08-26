package nl.vdzon.productfactory.api.design

import nl.vdzon.productfactory.api.shared.*
import java.time.Instant

enum class EpicStatus { AVAILABLE, IN_PLANNING, ACTIVE, VERIFYING, COMPLETED, NOT_SUCCESSFUL, SUPERSEDED, WITHDRAWN, CANCELLED }
enum class EpicVerificationOutcome { PASSED, NOT_SUCCESSFUL, NEEDS_WORK, BLOCKED }

data class EpicFilter(
    val productId: ProductId? = null,
    val statuses: Set<EpicStatus> = emptySet(),
    val timeRange: TimeRange = TimeRange(),
)
data class EpicDetails(
    val id: EpicId,
    val productId: ProductId,
    val title: String,
    val summary: String,
    val problem: String,
    val solution: String,
    val directionReferences: List<SourceReference>,
    val uxDesign: String? = null,
    val acceptanceCriteria: List<String>,
    val slicabilityRationale: String,
    val status: EpicStatus,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val verificationId: VerificationId? = null,
)
data class ClaimEpicForPlanningCommand(val epicId: EpicId, val expectedVersion: Long, val actor: ActorReference, val idempotencyKey: String)
data class MarkEpicActiveCommand(val epicId: EpicId, val plannedEpicVersion: Long, val expectedVersion: Long, val actor: ActorReference, val idempotencyKey: String)
data class MarkEpicReadyForVerificationCommand(val epicId: EpicId, val expectedVersion: Long, val actor: ActorReference, val idempotencyKey: String)
data class RecordEpicVerificationCommand(
    val epicId: EpicId,
    val verificationId: VerificationId,
    val outcome: EpicVerificationOutcome,
    val explanation: String,
    val expectedVersion: Long,
    val actor: ActorReference,
    val idempotencyKey: String,
)
data class WithdrawEpicCommand(val epicId: EpicId, val reason: String, val expectedVersion: Long, val actor: ActorReference, val idempotencyKey: String)
data class CancelEpicCommand(val epicId: EpicId, val reason: String, val expectedVersion: Long, val actor: ActorReference, val idempotencyKey: String)

interface ProductDesignService {
    fun runProcessSession(productId: ProductId)
    fun claimEpicForPlanning(command: ClaimEpicForPlanningCommand)
    fun markEpicActive(command: MarkEpicActiveCommand)
    fun markEpicReadyForVerification(command: MarkEpicReadyForVerificationCommand)
    fun recordEpicVerification(command: RecordEpicVerificationCommand)
    fun withdrawEpic(command: WithdrawEpicCommand)
    fun cancelEpic(command: CancelEpicCommand)
}

interface ProductDesignQueryService {
    fun getEpic(epicId: EpicId): EpicDetails
    fun getEpicHistory(epicId: EpicId): List<EpicDetails>
    fun findEpics(filter: EpicFilter): List<EpicDetails>
    fun getProcessSession(processSessionId: ProcessSessionId): ProcessSessionDetails
    fun findProcessSessions(filter: ProcessSessionFilter): List<ProcessSessionDetails>
}
