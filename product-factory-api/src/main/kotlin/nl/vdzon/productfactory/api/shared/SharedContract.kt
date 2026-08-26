package nl.vdzon.productfactory.api.shared

import java.time.Instant

@JvmInline value class ProductId(val value: String)
@JvmInline value class EpicId(val value: String)
@JvmInline value class StoryId(val value: String)
@JvmInline value class BugId(val value: String)
@JvmInline value class VerificationId(val value: String)
@JvmInline value class UserSignalId(val value: String)
@JvmInline value class DecisionId(val value: String)
@JvmInline value class MeetingId(val value: String)
@JvmInline value class StakeholderQuestionId(val value: String)
@JvmInline value class ProcessSessionId(val value: String)
@JvmInline value class PlanningWorkItemId(val value: String)
@JvmInline value class QualityWorkItemId(val value: String)
@JvmInline value class AiTaskId(val value: String)
@JvmInline value class MemoryItemId(val value: String)
@JvmInline value class MemoryVersionId(val value: String)
@JvmInline value class DeliveryAttemptId(val value: String)

enum class ProductFactoryEnvironment { LOCAL, ACCEPTANCE, PRODUCTION }
enum class ActorType { STAKEHOLDER, PROCESS, MEETING_MINUTES_AGENT, FACTORY, SYSTEM }
enum class ProcessSessionStatus { RUNNING, WAITING_FOR_AI, BLOCKED, SUCCEEDED, FAILED, CANCELLED }
enum class WorkItemStatus { PENDING, IN_PROGRESS, DONE, BLOCKED, FAILED }
enum class ScheduledProcess { PRODUCT_DESIGN, PRODUCT_PLANNING, QUALITY_ASSURANCE, SOFTWARE_FACTORY_DISPATCHER }

data class TimeRange(val from: Instant? = null, val until: Instant? = null)
data class ActorReference(val type: ActorType, val id: String)
data class SourceReference(val type: String, val id: String, val version: Long)
data class ArtifactReference(val name: String, val mediaType: String, val uri: String)
data class EvidenceDetails(val description: String, val artifacts: List<ArtifactReference> = emptyList())
data class ImplementationIdentity(val artifact: String, val variant: String, val version: String, val sourceRevision: String)

data class ProcessSessionFilter(
    val productId: ProductId? = null,
    val statuses: Set<ProcessSessionStatus> = emptySet(),
    val timeRange: TimeRange = TimeRange(),
)

data class ProcessSessionDetails(
    val id: ProcessSessionId,
    val productId: ProductId,
    val status: ProcessSessionStatus,
    val implementation: ImplementationIdentity,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val inputs: List<SourceReference> = emptyList(),
    val aiTaskIds: List<AiTaskId> = emptyList(),
    val publications: List<SourceReference> = emptyList(),
    val resultSummary: String? = null,
    val blockedReason: String? = null,
    val errorCode: String? = null,
)

class ProcessAlreadyRunning(val productId: ProductId) : RuntimeException("Er draait al een processessie voor ${productId.value}")
class AggregateNotFound(message: String) : RuntimeException(message)
class VersionConflict(message: String) : RuntimeException(message)
class IdempotencyConflict(message: String) : RuntimeException(message)
class InvalidCommand(message: String) : RuntimeException(message)
class CapabilityNotAvailable(message: String) : RuntimeException(message)
