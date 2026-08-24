package nl.vdzon.productfactory.api.product

import nl.vdzon.productfactory.api.shared.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

enum class ProductStatus { ACTIVE, INACTIVE }
enum class UserSignalStatus { OPEN, IN_REVIEW, PROCESSED }
enum class UserSignalCategory { FEEDBACK, PROBLEM, CONCERN, OPPORTUNITY, QUALITY_CONCERN, QUALITY_PATTERN }
enum class UserSignalUrgency { LOW, NORMAL, HIGH, URGENT }
enum class StakeholderQuestionStatus { OPEN, ANSWERED, WITHDRAWN }
enum class MeetingStatus { REQUESTED, OPEN, CLOSED }
enum class MeetingSenderRole { STAKEHOLDER, MEETING_AGENT, SYSTEM }

data class CreateProductCommand(val name: String, val idempotencyKey: String)
data class UpdateProductAssignmentCommand(
    val productId: ProductId,
    val audience: String,
    val goal: String,
    val hardBoundaries: List<String>,
    val publicGitUrl: String,
    val expectedVersion: Long,
    val idempotencyKey: String,
)
data class TestEnvironmentConfiguration(
    val name: String,
    val baseUrl: String,
    val allowedRoutes: List<String>,
    val revisionEndpoint: String,
    val revisionJsonPath: String,
    val credentialReferences: List<String> = emptyList(),
    val dataBoundaries: List<String> = emptyList(),
)
data class ConfigureTestableProductCommand(
    val productId: ProductId,
    val acceptance: TestEnvironmentConfiguration,
    val production: TestEnvironmentConfiguration? = null,
    val expectedVersion: Long,
    val idempotencyKey: String,
)
data class SetProductDispatchingCommand(val productId: ProductId, val enabled: Boolean, val expectedVersion: Long, val idempotencyKey: String)
data class WeeklyScheduleRule(val days: Set<DayOfWeek>, val times: Set<LocalTime>)
data class SchedulePattern(val weeklyRules: List<WeeklyScheduleRule> = emptyList(), val intervalMinutes: Long? = null)
data class UpdateProcessScheduleCommand(
    val productId: ProductId,
    val process: ScheduledProcess,
    val enabled: Boolean,
    val timezone: String,
    val pattern: SchedulePattern,
    val expectedVersion: Long,
    val idempotencyKey: String,
)

data class ProductDetails(
    val id: ProductId,
    val name: String,
    val status: ProductStatus,
    val dispatchingEnabled: Boolean,
    val createdAt: Instant,
    val version: Long,
)
data class ProductAssignmentDetails(
    val productId: ProductId,
    val audience: String,
    val goal: String,
    val hardBoundaries: List<String>,
    val publicGitUrl: String,
    val version: Long,
)
data class TestableProductDetails(
    val productId: ProductId,
    val acceptance: TestEnvironmentConfiguration,
    val production: TestEnvironmentConfiguration?,
    val version: Long,
)
data class ProcessScheduleDetails(
    val productId: ProductId,
    val process: ScheduledProcess,
    val enabled: Boolean,
    val timezone: String,
    val pattern: SchedulePattern,
    val nextRunAt: Instant?,
    val updatedAt: Instant,
    val version: Long,
)

data class SubmitUserSignalCommand(
    val productId: ProductId,
    val category: UserSignalCategory,
    val urgency: UserSignalUrgency,
    val source: String,
    val text: String,
    val attachments: List<ArtifactReference> = emptyList(),
    val idempotencyKey: String,
)
data class MarkUserSignalInReviewCommand(val signalId: UserSignalId, val expectedVersion: Long, val idempotencyKey: String)
data class RecordSignalInvestigationCommand(
    val signalId: UserSignalId,
    val verificationId: VerificationId,
    val outcome: String,
    val expectedVersion: Long,
    val idempotencyKey: String,
)
data class LinkSignalToEpicCommand(val signalId: UserSignalId, val epicId: EpicId, val epicVersion: Long, val expectedVersion: Long, val idempotencyKey: String)
data class UserSignalFilter(
    val productId: ProductId? = null,
    val statuses: Set<UserSignalStatus> = emptySet(),
    val categories: Set<UserSignalCategory> = emptySet(),
    val urgencies: Set<UserSignalUrgency> = emptySet(),
    val source: String? = null,
    val timeRange: TimeRange = TimeRange(),
)
data class UserSignalDetails(
    val id: UserSignalId,
    val productId: ProductId,
    val category: UserSignalCategory,
    val urgency: UserSignalUrgency,
    val source: String,
    val text: String,
    val attachments: List<ArtifactReference>,
    val status: UserSignalStatus,
    val verificationId: VerificationId? = null,
    val outcome: String? = null,
    val epicId: EpicId? = null,
    val createdAt: Instant,
    val version: Long,
)

data class AskStakeholderCommand(
    val productId: ProductId,
    val agentRole: String,
    val question: String,
    val context: String,
    val processSessionId: ProcessSessionId,
    val linkedObjects: List<SourceReference> = emptyList(),
    val idempotencyKey: String,
)
data class RecordStakeholderAnswerCommand(
    val questionId: StakeholderQuestionId,
    val meetingId: MeetingId,
    val messageId: String,
    val answer: String,
    val expectedVersion: Long,
    val idempotencyKey: String,
)
data class WithdrawStakeholderQuestionCommand(val questionId: StakeholderQuestionId, val reason: String, val expectedVersion: Long, val idempotencyKey: String)
data class StakeholderQuestionFilter(
    val productId: ProductId? = null,
    val agentRole: String? = null,
    val statuses: Set<StakeholderQuestionStatus> = emptySet(),
)
data class StakeholderQuestionDetails(
    val id: StakeholderQuestionId,
    val productId: ProductId,
    val agentRole: String,
    val question: String,
    val context: String,
    val processSessionId: ProcessSessionId,
    val linkedObjects: List<SourceReference>,
    val status: StakeholderQuestionStatus,
    val answer: String? = null,
    val meetingId: MeetingId? = null,
    val createdAt: Instant,
    val answeredAt: Instant? = null,
    val version: Long,
)

data class StartMeetingCommand(val productId: ProductId, val reason: String, val agenda: List<String>, val linkedObjects: List<SourceReference>, val idempotencyKey: String)
data class RecordMeetingMessageCommand(
    val meetingId: MeetingId,
    val senderRole: MeetingSenderRole,
    val text: String,
    val representedAgentRole: String? = null,
    val expectedVersion: Long,
    val idempotencyKey: String,
)
data class CloseMeetingCommand(val meetingId: MeetingId, val minutes: String, val outcomes: List<String>, val expectedVersion: Long, val idempotencyKey: String)
data class MeetingMessageDetails(
    val id: String,
    val senderRole: MeetingSenderRole,
    val representedAgentRole: String?,
    val text: String,
    val createdAt: Instant,
)
data class MeetingDetails(
    val id: MeetingId,
    val productId: ProductId,
    val reason: String,
    val agenda: List<String>,
    val linkedObjects: List<SourceReference>,
    val status: MeetingStatus,
    val messages: List<MeetingMessageDetails>,
    val minutes: String? = null,
    val outcomes: List<String> = emptyList(),
    val createdAt: Instant,
    val closedAt: Instant? = null,
    val version: Long,
)

interface ProductCommandService {
    fun createProduct(command: CreateProductCommand): ProductId
    fun updateProductAssignment(command: UpdateProductAssignmentCommand)
    fun configureTestableProduct(command: ConfigureTestableProductCommand)
    fun setProductDispatching(command: SetProductDispatchingCommand)
    fun updateProcessSchedule(command: UpdateProcessScheduleCommand)
    fun submitUserSignal(command: SubmitUserSignalCommand): UserSignalId
    fun markUserSignalInReview(command: MarkUserSignalInReviewCommand)
    fun recordSignalInvestigation(command: RecordSignalInvestigationCommand)
    fun linkSignalToEpic(command: LinkSignalToEpicCommand)
    fun askStakeholder(command: AskStakeholderCommand): StakeholderQuestionId
    fun recordStakeholderAnswer(command: RecordStakeholderAnswerCommand)
    fun withdrawStakeholderQuestion(command: WithdrawStakeholderQuestionCommand)
    fun startMeeting(command: StartMeetingCommand): MeetingId
    fun recordMeetingMessage(command: RecordMeetingMessageCommand)
    fun closeMeeting(command: CloseMeetingCommand)
}

interface ProductQueryService {
    fun getProduct(productId: ProductId): ProductDetails
    fun findProducts(): List<ProductDetails>
    fun getProductAssignment(productId: ProductId): ProductAssignmentDetails
    fun getTestableProduct(productId: ProductId): TestableProductDetails
    fun getProcessSchedule(productId: ProductId, process: ScheduledProcess): ProcessScheduleDetails
    fun getProcessSchedules(productId: ProductId): List<ProcessScheduleDetails>
    fun getUserSignal(userSignalId: UserSignalId): UserSignalDetails
    fun findUserSignals(filter: UserSignalFilter): List<UserSignalDetails>
    fun getStakeholderQuestion(questionId: StakeholderQuestionId): StakeholderQuestionDetails
    fun findStakeholderQuestions(filter: StakeholderQuestionFilter): List<StakeholderQuestionDetails>
    fun getMeeting(meetingId: MeetingId): MeetingDetails
    fun findMeetings(productId: ProductId, status: MeetingStatus? = null): List<MeetingDetails>
}
