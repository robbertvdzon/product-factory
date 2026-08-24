package nl.vdzon.productfactory.api.quality

import nl.vdzon.productfactory.api.shared.*
import java.time.Instant

enum class BugStatus { OPEN, IN_FIX, RESOLVED, CLOSED }
enum class BugSeverity { LOW, MEDIUM, HIGH, CRITICAL }
enum class VerificationTargetType { STORY, EPIC, BUGFIX, USER_SIGNAL }
enum class VerificationOutcome { PASSED, FAILED, BLOCKED }
enum class QualityWorkItemType { VERIFY_STORY, VERIFY_EPIC, RETEST_BUGFIX, INVESTIGATE_USER_SIGNAL }

data class BugFilter(val productId: ProductId? = null, val epicId: EpicId? = null, val statuses: Set<BugStatus> = emptySet())
data class BugDetails(
    val id: BugId,
    val productId: ProductId,
    val epicId: EpicId?,
    val title: String,
    val summary: String,
    val actualBehaviour: String,
    val expectedBehaviour: String,
    val reproductionSteps: List<String>,
    val environment: String,
    val evidence: EvidenceDetails,
    val impact: String,
    val severity: BugSeverity,
    val status: BugStatus,
    val sourceSignalIds: List<UserSignalId> = emptyList(),
    val version: Long,
)
data class VerificationFilter(
    val productId: ProductId? = null,
    val targetType: VerificationTargetType? = null,
    val targetId: String? = null,
    val outcomes: Set<VerificationOutcome> = emptySet(),
    val environment: String? = null,
    val timeRange: TimeRange = TimeRange(),
)
data class VerificationDetails(
    val id: VerificationId,
    val productId: ProductId,
    val targetType: VerificationTargetType,
    val targetId: String,
    val targetVersion: Long,
    val outcome: VerificationOutcome,
    val environment: String,
    val checks: List<String>,
    val evidence: EvidenceDetails,
    val blockedReason: String? = null,
    val missingCoverage: List<String> = emptyList(),
    val requiredCommitSha: String? = null,
    val testedRevision: String? = null,
    val createdAt: Instant,
)
data class QualitySnapshotDetails(
    val productId: ProductId,
    val capturedAt: Instant,
    val environment: String,
    val productRevision: String,
    val investigatedAreas: List<String>,
    val staleOrMissingCoverage: List<String>,
    val openBugsBySeverity: Map<BugSeverity, Int>,
    val verificationOutcomes: Map<VerificationOutcome, Int>,
    val risks: List<String>,
    val sources: List<SourceReference>,
)
data class QualityWorkItemDetails(
    val id: QualityWorkItemId,
    val productId: ProductId,
    val type: QualityWorkItemType,
    val source: SourceReference,
    val targetEnvironment: String,
    val priority: Int,
    val status: WorkItemStatus,
    val result: String? = null,
    val errorCode: String? = null,
    val blockedReason: String? = null,
    val attemptCount: Int,
    val lastAttemptAt: Instant? = null,
    val retryable: Boolean,
    val retryAfter: Instant? = null,
    val attentionNeeded: Boolean,
)
data class RequestStoryVerificationCommand(val productId: ProductId, val storyId: StoryId, val storyVersion: Long, val environment: String, val priority: Int, val idempotencyKey: String)
data class RequestEpicVerificationCommand(val productId: ProductId, val epicId: EpicId, val epicVersion: Long, val environment: String, val priority: Int, val idempotencyKey: String)
data class RequestBugfixRetestCommand(val productId: ProductId, val bugId: BugId, val storyId: StoryId, val storyVersion: Long, val environment: String, val idempotencyKey: String)
data class RequestSignalInvestigationCommand(val productId: ProductId, val signalId: UserSignalId, val signalVersion: Long, val environment: String, val idempotencyKey: String)

interface QualityService {
    fun runProcessSession(productId: ProductId)
    fun requestStoryVerification(command: RequestStoryVerificationCommand): QualityWorkItemId
    fun requestEpicVerification(command: RequestEpicVerificationCommand): QualityWorkItemId
    fun requestBugfixRetest(command: RequestBugfixRetestCommand): QualityWorkItemId
    fun requestSignalInvestigation(command: RequestSignalInvestigationCommand): QualityWorkItemId
    fun retryQualityWorkItem(workItemId: QualityWorkItemId)
    fun linkBugfixStory(bugId: BugId, storyId: StoryId)
}

interface QualityQueryService {
    fun getBug(bugId: BugId): BugDetails
    fun findBugs(filter: BugFilter): List<BugDetails>
    fun findVerifications(filter: VerificationFilter): List<VerificationDetails>
    fun getCurrentQuality(productId: ProductId): QualitySnapshotDetails?
    fun getQualityHistory(productId: ProductId, range: TimeRange): List<QualitySnapshotDetails>
    fun findQualityWorkItems(productId: ProductId, status: WorkItemStatus? = null): List<QualityWorkItemDetails>
    fun findRetryableQualityWorkItems(): List<QualityWorkItemDetails>
    fun getProcessSession(processSessionId: ProcessSessionId): ProcessSessionDetails
    fun findProcessSessions(filter: ProcessSessionFilter): List<ProcessSessionDetails>
}
