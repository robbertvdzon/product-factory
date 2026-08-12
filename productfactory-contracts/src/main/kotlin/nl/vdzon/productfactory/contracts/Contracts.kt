package nl.vdzon.productfactory.contracts

import java.time.Instant

data class AgentTask(
    val runId: String,
    val productSlug: String,
    val taskType: String,
    val prompt: String,
    val timeoutSeconds: Long = 900,
    val model: String? = null,
    val responseSchema: String? = null,
    val provider: String? = null,
)

data class AgentResult(
    val runId: String,
    val status: String,
    val summary: String,
    val artifacts: List<AgentArtifact> = emptyList(),
    val completedAt: Instant = Instant.now(),
)

data class AgentArtifact(val relativePath: String, val mediaType: String, val content: String)

data class AgentWorkerHello(
    val type: String = "hello",
    val token: String,
    val workerId: String,
    val workerVersion: String,
    val capabilities: List<String> = listOf("codex"),
)

data class AgentWorkerTaskFrame(val type: String = "task", val task: AgentTask)
data class AgentWorkerResultFrame(val type: String = "result", val result: AgentResult)

enum class AgentTaskState { RUNNING, COMPLETED, FAILED, DISCONNECTED }

data class AgentTaskStatus(
    val runId: String,
    val state: AgentTaskState,
    val result: AgentResult? = null,
    val updatedAt: Instant = Instant.now(),
)

data class AgentRunView(
    val runId: String,
    val productSlug: String,
    val taskType: String,
    val status: String,
    val resultReference: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
)

data class AgentWorkerStatus(
    val connected: Boolean,
    val workerId: String? = null,
    val workerVersion: String? = null,
    val capabilities: List<String> = emptyList(),
    val connectedSince: Instant? = null,
    val activeRuns: Int = 0,
)

data class ProductView(
    val id: String,
    val slug: String,
    val name: String,
    val mission: String,
    val description: String,
    val guardrails: String,
    val softwareFactoryProjectKey: String,
    val targetRepositoryName: String,
    val workspaceDirectory: String,
    val allowedWritePaths: List<String>,
    val workspaceOwnership: String,
    val liveUrl: String?,
    val previewUrlPattern: String?,
    val acceptanceUrl: String?,
    val adminUrl: String?,
    val status: String,
    val developmentMode: String,
    val iterationTimes: List<String>,
    val timezone: String,
    val maxStoriesPerCycle: Int,
    val wipLimit: Int,
    val aiProvider: String,
    val aiModel: String,
    val dailyBudgetCents: Int,
    val monthlyBudgetCents: Int,
    val escalationPolicy: String,
    val privacyRules: String,
    val accessibilityRules: String,
    val qualityRules: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val meetingRequestedAt: Instant? = null,
    val meetingRequestedTopics: List<String> = emptyList(),
)
data class StoryCandidateView(
    val id: Long,
    val productSlug: String,
    val title: String,
    val description: String,
    val status: String,
    val createdAt: Instant,
    val iterationSequenceNumber: Int? = null,
    val acceptanceCriteria: String? = null,
    val criticReason: String? = null,
    val blocked: Boolean = false,
    val blockedReason: String? = null,
    val themeId: String? = null,
)
data class ProductRecordView(
    val id: Long,
    val productSlug: String,
    val title: String,
    val content: String,
    val sourceUrl: String? = null,
    val createdAt: Instant,
)

data class ShadowIterationView(
    val id: String,
    val productSlug: String,
    val sequenceNumber: Int,
    val focus: String,
    val mode: String,
    val status: String,
    val currentRole: String?,
    val criticVerdict: String?,
    val candidateCount: Int,
    val workspaceRunId: String?,
    val workspacePullRequestUrl: String?,
    val workspaceCommitSha: String?,
    val errorMessage: String?,
    val summary: String?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val acceptedCandidateCount: Int = 0,
    val revisionRounds: Int = 0,
    val outcomeReason: String? = null,
    val resumedFromIterationId: String? = null,
)

data class ShadowIterationStepView(
    val role: String,
    val attempt: Int,
    val runId: String,
    val status: String,
    val errorMessage: String?,
    val startedAt: Instant,
    val completedAt: Instant?,
)
data class WorkspacePublicationView(
    val runId: String,
    val productSlug: String,
    val artifactPath: String,
    val contentHash: String,
    val status: String,
    val pullRequestUrl: String?,
    val commitSha: String?,
)

data class MeetingView(
    val id: String,
    val productSlug: String,
    val sequenceNumber: Int,
    val initiator: String,
    val status: String,
    val requestedTopics: List<String>,
    val outcomeSummary: String?,
    val createdAt: Instant,
    val closedAt: Instant?,
    val workspaceRunId: String? = null,
    val workspacePullRequestUrl: String? = null,
    val workspaceCommitSha: String? = null,
)

data class MeetingMessageView(
    val id: Long,
    val meetingId: String,
    val sender: String,
    val content: String,
    val createdAt: Instant,
)

data class RoadmapThemeView(
    val id: String,
    val productSlug: String,
    val sequenceNumber: Int,
    val title: String,
    val description: String,
    val priority: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val closedAt: Instant?,
)

data class RoadmapSettledQuestionView(
    val id: Long,
    val productSlug: String,
    val content: String,
    val createdAt: Instant,
)

data class RoadmapSessionView(
    val id: String,
    val productSlug: String,
    val sequenceNumber: Int,
    val status: String,
    val summary: String?,
    val errorMessage: String?,
    val createdAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val workspaceRunId: String? = null,
    val workspacePullRequestUrl: String? = null,
    val workspaceCommitSha: String? = null,
)

data class DeliveryVerificationView(
    val id: String,
    val productSlug: String,
    val themeId: String,
    val candidateId: Long,
    val candidateTitle: String,
    val status: String,
    val verdict: String?,
    val report: String?,
    val createdAt: Instant,
    val completedAt: Instant?,
)
