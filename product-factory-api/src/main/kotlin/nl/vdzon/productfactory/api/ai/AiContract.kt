package nl.vdzon.productfactory.api.ai

import nl.vdzon.productfactory.api.shared.*
import java.time.Duration
import java.time.Instant

@JvmInline value class AiJobKey(val value: String)
enum class AiProvider { CODEX, CLAUDE, MOCKED }
enum class AiTaskStatus { PENDING_SUBMISSION, QUEUED, WAITING_FOR_WORKER, RUNNING, SUCCEEDED, FAILED, CANCELLED }
enum class AiTaskResultStatus { SUCCEEDED, FAILED }
data class AiJobConfigurationDetails(
    val jobKey: AiJobKey,
    val displayName: String,
    val provider: AiProvider,
    val model: String,
    val enabled: Boolean,
    val version: Long,
    val updatedAt: Instant,
    val updatedBy: ActorReference,
)
data class UpdateAiJobConfigurationCommand(
    val jobKey: AiJobKey,
    val provider: AiProvider,
    val model: String,
    val enabled: Boolean,
    val expectedVersion: Long,
    val actor: ActorReference,
    val idempotencyKey: String,
)
data class RepositorySnapshot(val publicGitUrl: String, val commitSha: String)
data class AiInputAttachment(val filename: String, val mediaType: String, val content: ByteArray) {
    override fun equals(other: Any?) = other is AiInputAttachment && filename == other.filename && mediaType == other.mediaType && content.contentEquals(other.content)
    override fun hashCode() = 31 * (31 * filename.hashCode() + mediaType.hashCode()) + content.contentHashCode()
}
data class RequestAiTaskCommand(
    val jobKey: AiJobKey,
    val productId: ProductId?,
    val requesterCapability: String,
    val requesterSessionId: ProcessSessionId?,
    val agentRole: String,
    val provider: AiProvider,
    val model: String,
    val configurationVersion: Long,
    val promptTemplateVersion: Long,
    val prompt: String,
    val responseSchema: String? = null,
    val repository: RepositorySnapshot? = null,
    val attachments: List<AiInputAttachment> = emptyList(),
    val executionTimeout: Duration,
    val idempotencyKey: String,
)
data class AiTaskFilter(val productId: ProductId? = null, val statuses: Set<AiTaskStatus> = emptySet(), val jobKey: AiJobKey? = null, val timeRange: TimeRange = TimeRange())
data class AiTaskDetails(
    val id: AiTaskId,
    val jobKey: AiJobKey,
    val productId: ProductId?,
    val requesterCapability: String,
    val provider: AiProvider,
    val model: String,
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
}
interface AiExecutionQueryService {
    fun getAiJobConfiguration(jobKey: AiJobKey): AiJobConfigurationDetails
    fun getAiJobConfigurations(): List<AiJobConfigurationDetails>
    fun getAiTask(taskId: AiTaskId): AiTaskDetails
    fun getAiTaskResult(taskId: AiTaskId): AiTaskResultDetails?
    fun findAiTasks(filter: AiTaskFilter): List<AiTaskDetails>
    fun getEnvironmentCatalog(projectPrefix: String): List<EnvironmentKeyDetails>
    fun getProductEnvironmentKeys(productId: ProductId): List<ProductEnvironmentKeyDetails>
}
