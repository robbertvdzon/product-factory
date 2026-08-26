package nl.vdzon.productfactory.api.ai

import nl.vdzon.productfactory.api.shared.*
import java.time.Instant

@JvmInline value class AiJobKey(val value: String)
enum class AiProvider { CODEX, CLAUDE, MOCKED }
enum class AiTaskStatus { QUEUED, CLAIMED, RUNNING, SUSPECTED, SUCCEEDED, FAILED, CANCELLED, ABANDONED }
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
data class TestEnvironmentAccess(val baseUrl: String, val allowedRoutes: List<String>, val credentialReferences: List<String>)
data class RequestAiTaskCommand(
    val jobKey: AiJobKey,
    val productId: ProductId?,
    val requesterCapability: String,
    val requesterSessionId: ProcessSessionId?,
    val provider: AiProvider,
    val model: String,
    val configurationVersion: Long,
    val instruction: String,
    val inputJson: String,
    val responseSchema: String,
    val repository: RepositorySnapshot? = null,
    val testEnvironment: TestEnvironmentAccess? = null,
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
    val status: AiTaskStatus,
    val attemptNumber: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val safeProgress: String? = null,
    val errorCode: String? = null,
)
data class AiTaskResultDetails(val taskId: AiTaskId, val status: AiTaskResultStatus, val responseJson: String?, val artifacts: List<ArtifactReference>, val errorCode: String?, val safeMessage: String?, val completedAt: Instant)
interface AiExecutionService {
    fun updateAiJobConfiguration(command: UpdateAiJobConfigurationCommand): AiJobConfigurationDetails
    fun requestAiTask(command: RequestAiTaskCommand): AiTaskId
    fun cancelAiTask(taskId: AiTaskId, reason: String)
}
interface AiExecutionQueryService {
    fun getAiJobConfiguration(jobKey: AiJobKey): AiJobConfigurationDetails
    fun getAiJobConfigurations(): List<AiJobConfigurationDetails>
    fun getAiTask(taskId: AiTaskId): AiTaskDetails
    fun getAiTaskResult(taskId: AiTaskId): AiTaskResultDetails?
    fun findAiTasks(filter: AiTaskFilter): List<AiTaskDetails>
}
