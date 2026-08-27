package nl.vdzon.productfactory.api.planning

import nl.vdzon.productfactory.api.shared.*
import java.time.Instant

enum class StoryType { PRODUCT_STORY, BUGFIX }
enum class StoryStatus { TODO, IN_PROGRESS, DONE, CANCELLED }
enum class PlanningWorkItemType { PLAN_BUGFIX, PLAN_EPIC_GAP, REPLAN_CANCELLED_DEPENDENCY, REPRIORITIZE_EPIC, MANUAL_REPLAN }
enum class DispatchReservationStatus { RESERVED, DISPATCHED, RELEASED, CANCELLED }

data class StoryFilter(
    val productId: ProductId? = null,
    val epicId: EpicId? = null,
    val statuses: Set<StoryStatus> = emptySet(),
    val types: Set<StoryType> = emptySet(),
    val timeRange: TimeRange = TimeRange(),
)
data class StoryDetails(
    val id: StoryId,
    val productId: ProductId,
    val epicId: EpicId,
    val epicVersion: Long,
    val sequenceNumber: Long,
    val type: StoryType,
    val title: String,
    val summary: String,
    val content: String,
    val acceptanceCriteria: List<String>,
    val uxDesign: String? = null,
    val dependencies: Set<StoryId> = emptySet(),
    val status: StoryStatus,
    val deliveredCommitSha: String? = null,
    val cancellationReason: String? = null,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val priorityReason: String? = null,
    val bugId: BugId? = null,
    val bugVersion: Long? = null,
    val externalStoryId: String? = null,
    val dispatchReservationId: String? = null,
    val dispatchReservationStatus: DispatchReservationStatus? = null,
    val verificationId: VerificationId? = null,
    val verificationPassed: Boolean? = null,
    val uxArtifacts: List<ArtifactReference> = emptyList(),
)
data class PlanningWorkItemDetails(
    val id: PlanningWorkItemId,
    val productId: ProductId,
    val type: PlanningWorkItemType,
    val source: SourceReference,
    val explanation: String,
    val priority: Int,
    val status: WorkItemStatus,
    val createdAt: Instant,
    val version: Long,
    val claimedBySessionId: ProcessSessionId? = null,
    val resultSummary: String? = null,
    val errorCode: String? = null,
)
data class RequestBugfixCommand(val productId: ProductId, val bugId: BugId, val bugVersion: Long, val evidenceId: VerificationId, val priority: Int, val actor: ActorReference, val idempotencyKey: String)
data class RequestEpicGapPlanningCommand(val productId: ProductId, val epicId: EpicId, val epicVersion: Long, val verificationId: VerificationId, val missingCoverage: List<String>, val actor: ActorReference, val idempotencyKey: String)
data class RequestEpicReprioritizationCommand(val productId: ProductId, val epicId: EpicId, val reason: String, val priority: Int, val actor: ActorReference, val idempotencyKey: String)
data class RequestManualReplanCommand(val productId: ProductId, val reason: String, val linkedObjects: List<SourceReference>, val actor: ActorReference, val idempotencyKey: String)
data class ReserveNextStoryForDispatchCommand(val productId: ProductId, val actor: ActorReference, val idempotencyKey: String)
data class RevalidateDispatchReservationCommand(val reservationId: String, val expectedStoryVersion: Long, val externalStoryExists: Boolean, val actor: ActorReference, val idempotencyKey: String)
data class StoryDispatchReservationDetails(
    val reservationId: String,
    val story: StoryDetails,
    val status: DispatchReservationStatus,
    val reservedAt: Instant,
    val expiresAt: Instant,
)
data class DispatchReservationValidation(val valid: Boolean, val reason: String? = null, val reservation: StoryDispatchReservationDetails? = null)
data class MarkStoryAsDispatchedCommand(val reservationId: String, val externalStoryId: String, val expectedStoryVersion: Long, val actor: ActorReference, val idempotencyKey: String)
data class MarkStoryAsDevelopedCommand(val storyId: StoryId, val externalStoryId: String, val deliveredCommitSha: String, val expectedVersion: Long, val actor: ActorReference, val idempotencyKey: String)
data class MarkStoryAsCancelledCommand(val storyId: StoryId, val externalStoryId: String, val reason: String, val expectedVersion: Long, val actor: ActorReference, val idempotencyKey: String)
data class RecordStoryVerificationCommand(val storyId: StoryId, val verificationId: VerificationId, val passed: Boolean, val expectedVersion: Long, val actor: ActorReference, val idempotencyKey: String)
data class CancelStoriesForEpicCommand(val productId: ProductId, val epicId: EpicId, val epicVersion: Long, val reason: String, val actor: ActorReference, val idempotencyKey: String)

interface ProductPlanningService {
    fun runProcessSession(productId: ProductId)
    fun requestBugfix(command: RequestBugfixCommand): PlanningWorkItemId
    fun requestEpicGapPlanning(command: RequestEpicGapPlanningCommand): PlanningWorkItemId
    fun requestEpicReprioritization(command: RequestEpicReprioritizationCommand): PlanningWorkItemId
    fun requestManualReplan(command: RequestManualReplanCommand): PlanningWorkItemId
    fun reserveNextStoryForDispatch(command: ReserveNextStoryForDispatchCommand): StoryDispatchReservationDetails?
    fun revalidateDispatchReservation(command: RevalidateDispatchReservationCommand): DispatchReservationValidation
    fun markStoryAsDispatched(command: MarkStoryAsDispatchedCommand)
    fun markStoryAsDeveloped(command: MarkStoryAsDevelopedCommand)
    fun markStoryAsCancelled(command: MarkStoryAsCancelledCommand)
    fun recordStoryVerification(command: RecordStoryVerificationCommand)
    fun cancelStoriesForEpic(command: CancelStoriesForEpicCommand)
    fun flushPendingEffects()
}

interface ProductPlanningQueryService {
    fun getStory(storyId: StoryId): StoryDetails
    fun getBacklog(productId: ProductId): List<StoryDetails>
    fun findStories(filter: StoryFilter): List<StoryDetails>
    fun findPlanningWorkItems(productId: ProductId, status: WorkItemStatus? = null): List<PlanningWorkItemDetails>
    fun getProcessSession(processSessionId: ProcessSessionId): ProcessSessionDetails
    fun findProcessSessions(filter: ProcessSessionFilter): List<ProcessSessionDetails>
}
