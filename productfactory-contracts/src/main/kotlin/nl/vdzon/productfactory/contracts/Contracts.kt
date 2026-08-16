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

data class WeeklyScheduleView(
    val dayOfWeek: String,
    val time: String,
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
    val roadmapSchedule: List<WeeklyScheduleView>,
    val testSchedule: List<WeeklyScheduleView>,
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
    val bugId: Long? = null,
)
data class ProductRecordView(
    val id: Long,
    val productSlug: String,
    val title: String,
    val content: String,
    val sourceUrl: String? = null,
    val createdAt: Instant,
    val supersedesId: Long? = null,
    val changeReason: String? = null,
    val createdBy: String = "system",
)

data class MemoryChangeView(
    val action: String,
    val productSlug: String,
    val memoryId: Long,
    val title: String,
    val reason: String,
)

/** Eén onveranderlijke versie uit de volledige geheugenlijn van een product. */
data class MemoryVersionView(
    val id: Long,
    val productSlug: String,
    val rootMemoryId: Long,
    val versionNumber: Int,
    val title: String,
    val content: String,
    val status: String,
    val createdAt: Instant,
    val effectiveUntil: Instant? = null,
    val supersedesId: Long? = null,
    val supersededById: Long? = null,
    val changeReason: String? = null,
    val createdBy: String = "system",
    val retirementReason: String? = null,
    val retiredBy: String? = null,
)

data class ShadowIterationDecisionView(
    val iterationId: String,
    val actorType: String,
    val mechanism: String,
    val reasonCode: String,
    val decidedAt: Instant,
)

enum class ManualStartOrigin {
    AUTONOMOUS_DEFAULT,
    OWNER_INPUT,
}

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
    val decision: ShadowIterationDecisionView? = null,
    val manualStartOrigin: ManualStartOrigin? = null,
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
    val consultedSources: List<String> = emptyList(),
    val memoryChanges: List<MemoryChangeView> = emptyList(),
    val images: List<ProductMediaView> = emptyList(),
)

data class ProductMediaView(
    val id: String,
    val productSlug: String,
    val filename: String,
    val mediaType: String,
    val sizeBytes: Long,
    val altText: String?,
    val source: String,
    val sourceReference: String?,
    val createdAt: Instant,
)

data class RoadmapEpicView(
    val id: String,
    val productSlug: String,
    val sequenceNumber: Int,
    val title: String,
    val description: String,
    /** Tijdelijke compatibiliteit voor oudere dashboardimages; nieuwe UI toont dit veld niet. */
    val priority: String,
    val status: String,
    val customerRank: Int,
    val processRank: Int,
    val priorityScore: Int,
    val roadmapRank: Int,
    val dependencyIds: List<String>,
    val blockedByIds: List<String>,
    val blocksIds: List<String>,
    val horizon: String = "UNPLACED",
    val kind: String = "DELIVERY",
    val capabilityKey: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val closedAt: Instant?,
)

data class RoadmapFutureVisionView(
    val id: String,
    val productSlug: String,
    val version: Int,
    val content: Map<String, Any?>,
    val changeSummary: String,
    val createdBySessionId: String,
    val createdAt: Instant,
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

data class BugView(
    val id: Long,
    val productSlug: String,
    val title: String,
    val description: String,
    val reproductionSteps: String,
    val expectedResult: String,
    val actualResult: String,
    val priority: String,
    val status: String,
    val sourceType: String,
    val sourceId: String?,
    val occurrenceCount: Int,
    val linkedCandidateId: Long?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastVerifiedAt: Instant?,
    val resolvedAt: Instant?,
)

data class TestSessionView(
    val id: String,
    val productSlug: String,
    val sequenceNumber: Int,
    val status: String,
    val summary: String?,
    val errorMessage: String?,
    val testedAreas: Int,
    val bugsCreated: Int,
    val bugsUpdated: Int,
    val bugsResolved: Int,
    val createdAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val workspaceRunId: String? = null,
    val workspacePullRequestUrl: String? = null,
    val workspaceCommitSha: String? = null,
)
