package nl.vdzon.productfactory.api.ai

import nl.vdzon.productfactory.api.shared.*
import java.time.Duration
import java.time.Instant

@JvmInline value class AiJobKey(val value: String)
enum class AiExecutionMode { SUBSCRIPTION, API, MOCK }
data class AiExecutionSelection(val vendorId: String, val model: String, val mode: AiExecutionMode)
enum class AiInputRole { SOURCE, CONTEXT, PROMPT, IMAGE, AUDIO, VIDEO, DOCUMENT }
enum class AiTaskStatus { PENDING_SUBMISSION, QUEUED, WAITING_FOR_WORKER, RUNNING, SUCCEEDED, FAILED, CANCELLED }
enum class AiTaskResultStatus { SUCCEEDED, FAILED }
data class AiJobConfigurationDetails(
    val jobKey: AiJobKey,
    val displayName: String,
    val execution: AiExecutionSelection,
    val enabled: Boolean,
    val version: Long,
    val updatedAt: Instant,
    val updatedBy: ActorReference,
)
data class UpdateAiJobConfigurationCommand(
    val jobKey: AiJobKey,
    val execution: AiExecutionSelection,
    val enabled: Boolean,
    val expectedVersion: Long,
    val actor: ActorReference,
    val idempotencyKey: String,
)
data class RepositorySnapshot(val publicGitUrl: String, val commitSha: String)
data class AiInputAttachment(
    val name: String,
    val filename: String,
    val mediaType: String,
    val role: AiInputRole,
    val content: ByteArray,
) {
    override fun equals(other: Any?) = other is AiInputAttachment && name == other.name && filename == other.filename &&
        mediaType == other.mediaType && role == other.role && content.contentEquals(other.content)
    override fun hashCode() = 31 * (31 * (31 * (31 * name.hashCode() + filename.hashCode()) + mediaType.hashCode()) + role.hashCode()) + content.contentHashCode()
}
data class AiOutputArtifactDeclaration(val name: String, val required: Boolean, val mimeTypes: Set<String>, val maxBytes: Long)
data class RequestAiTaskCommand(
    val jobKey: AiJobKey,
    val productId: ProductId?,
    val requesterCapability: String,
    val requesterSessionId: ProcessSessionId?,
    val agentRole: String,
    val execution: AiExecutionSelection,
    val configurationVersion: Long,
    val promptTemplateVersion: Long,
    val prompt: String,
    val responseSchema: String,
    val repository: RepositorySnapshot? = null,
    val attachments: List<AiInputAttachment> = emptyList(),
    val outputArtifacts: List<AiOutputArtifactDeclaration> = emptyList(),
    val executionTimeout: Duration,
    val idempotencyKey: String,
)
data class AiTaskFilter(val productId: ProductId? = null, val statuses: Set<AiTaskStatus> = emptySet(), val jobKey: AiJobKey? = null, val timeRange: TimeRange = TimeRange())
data class AiTaskDetails(
    val id: AiTaskId,
    val jobKey: AiJobKey,
    val productId: ProductId?,
    val requesterCapability: String,
    val execution: AiExecutionSelection,
    val configurationVersion: Long,
    val promptTemplateVersion: Long,
    val requesterSessionId: ProcessSessionId?,
    val agentRole: String,
    val status: AiTaskStatus,
    val runtimeJobId: String?,
    val runtimePhase: String?,
    val runtimeAttemptCount: Int,
    val safeProgressPercent: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val safeProgress: String? = null,
    val errorCode: String? = null,
    val cancelReason: String? = null,
)
data class AiTaskResultDetails(val taskId: AiTaskId, val status: AiTaskResultStatus, val responseJson: String?, val artifacts: List<ArtifactReference>, val errorCode: String?, val safeMessage: String?, val completedAt: Instant)
data class EnvironmentKeyDetails(
    val name: String,
    val projectPrefix: String,
    val available: Boolean,
    val matchingOnlineWorkers: Int,
    val lastSeenAt: Instant,
    val knownToProduct: Boolean = false,
)
data class ProductEnvironmentKeyDetails(
    val productId: ProductId,
    val name: String,
    val projectPrefix: String,
    val active: Boolean,
    val available: Boolean,
    val matchingOnlineWorkers: Int,
    val lastSeenAt: Instant,
    val version: Long,
    val grantedAgentRoles: Set<String>,
)
data class RefreshEnvironmentCatalogCommand(val projectPrefix: String)
data class ExecutionCatalogEntry(
    val execution: AiExecutionSelection,
    val taskTypes: Set<String>,
    val available: Boolean,
    val matchingOnlineWorkers: Int,
    val lastSeenAt: Instant,
)
data class RefreshExecutionCatalogCommand(val taskType: String = "STRUCTURED_GENERATION")
data class AiTaskEventDetails(
    val sequence: Long,
    val type: String,
    val safeMessage: String?,
    val progressPercent: Int?,
    val occurredAt: Instant,
)
data class AiCostDetails(val kind: String, val status: String, val amount: String?, val currency: String?)
data class AiTaskUsageDetails(
    val taskId: AiTaskId,
    val taskType: String,
    val execution: AiExecutionSelection,
    val attemptCount: Int,
    val quality: String,
    val inputTokens: Long?,
    val cachedInputTokens: Long?,
    val outputTokens: Long?,
    val reasoningTokens: Long?,
    val costs: List<AiCostDetails>,
    val capturedAt: Instant,
)
data class SetProductEnvironmentKeyCommand(
    val productId: ProductId,
    val name: String,
    val active: Boolean,
    val expectedVersion: Long,
    val actor: ActorReference,
    val idempotencyKey: String,
)
data class SetAgentEnvironmentGrantCommand(
    val productId: ProductId,
    val name: String,
    val agentRole: String,
    val granted: Boolean,
    val actor: ActorReference,
    val idempotencyKey: String,
)
interface AiExecutionService {
    fun updateAiJobConfiguration(command: UpdateAiJobConfigurationCommand): AiJobConfigurationDetails
    fun requestAiTask(command: RequestAiTaskCommand): AiTaskId
    fun cancelAiTask(taskId: AiTaskId, reason: String)
    fun refreshEnvironmentCatalog(command: RefreshEnvironmentCatalogCommand): List<EnvironmentKeyDetails>
    fun setProductEnvironmentKey(command: SetProductEnvironmentKeyCommand): ProductEnvironmentKeyDetails
    fun setAgentEnvironmentGrant(command: SetAgentEnvironmentGrantCommand): ProductEnvironmentKeyDetails
    fun refreshExecutionCatalog(command: RefreshExecutionCatalogCommand): List<ExecutionCatalogEntry>
}
interface AiExecutionQueryService {
    fun getAiJobConfiguration(jobKey: AiJobKey): AiJobConfigurationDetails
    fun getAiJobConfigurations(): List<AiJobConfigurationDetails>
    fun getAiTask(taskId: AiTaskId): AiTaskDetails
    fun getAiTaskResult(taskId: AiTaskId): AiTaskResultDetails?
    fun downloadAiTaskArtifact(taskId: AiTaskId, artifactId: String): ByteArray
    fun findAiTasks(filter: AiTaskFilter): List<AiTaskDetails>
    fun getAiTaskEvents(taskId: AiTaskId): List<AiTaskEventDetails>
    fun getAiTaskUsage(taskId: AiTaskId): AiTaskUsageDetails?
    fun getEnvironmentCatalog(projectPrefix: String): List<EnvironmentKeyDetails>
    fun getProductEnvironmentKeys(productId: ProductId): List<ProductEnvironmentKeyDetails>
    fun getExecutionCatalog(taskType: String = "STRUCTURED_GENERATION"): List<ExecutionCatalogEntry>
}
