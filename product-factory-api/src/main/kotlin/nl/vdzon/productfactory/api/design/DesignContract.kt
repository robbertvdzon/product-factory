package nl.vdzon.productfactory.api.design

import nl.vdzon.productfactory.api.shared.*
import java.time.Instant

enum class EpicStatus { NEEDS_RESEARCH, NEEDS_REFINEMENT, AWAITING_APPROVAL, AVAILABLE, IN_PLANNING, ACTIVE, VERIFYING, COMPLETED, NOT_SUCCESSFUL, SUPERSEDED, WITHDRAWN, CANCELLED }
enum class EpicVerificationOutcome { PASSED, NOT_SUCCESSFUL, NEEDS_WORK, BLOCKED }
enum class ResearchSourceStatus { CANDIDATE, VALIDATED, BLOCKED }
enum class UxScreenState { INITIAL, MAIN, DETAIL, EMPTY, ERROR, OTHER }
enum class UxViewport { DESKTOP, MOBILE, OTHER }

data class EpicUxScreen(
    val screenKey: String,
    val state: UxScreenState,
    val purpose: String,
    val artifacts: Map<UxViewport, String>,
)

data class EpicResearchSource(
    val name: String,
    val provider: String,
    val uri: String,
    val accessMethod: String,
    val license: String,
    val coverage: String,
    val status: ResearchSourceStatus,
    val validationEvidence: String,
)

data class EpicReadinessDetails(
    val readyForPlanning: Boolean,
    val requiresExternalData: Boolean,
    val unmetConditions: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
)

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
    val researchSources: List<EpicResearchSource> = emptyList(),
    val readiness: EpicReadinessDetails = EpicReadinessDetails(false, false, listOf("Gereedheid is nog niet beoordeeld.")),
    val uxArtifacts: List<ArtifactReference> = emptyList(),
    val uxScreens: List<EpicUxScreen> = emptyList(),
    val refinementReason: String? = null,
)
data class ApproveEpicCommand(val epicId: EpicId, val expectedVersion: Long, val actor: ActorReference, val idempotencyKey: String)
data class RequestEpicRefinementCommand(val epicId: EpicId, val reason: String, val expectedVersion: Long, val actor: ActorReference, val idempotencyKey: String)
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
    fun approveEpic(command: ApproveEpicCommand)
    fun requestEpicRefinement(command: RequestEpicRefinementCommand)
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
