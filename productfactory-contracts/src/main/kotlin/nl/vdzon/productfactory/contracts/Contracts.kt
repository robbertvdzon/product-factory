package nl.vdzon.productfactory.contracts

import java.time.Instant

data class AgentTask(
    val runId: String,
    val productSlug: String,
    val taskType: String,
    val prompt: String,
    val timeoutSeconds: Long = 900,
)

data class AgentResult(
    val runId: String,
    val status: String,
    val summary: String,
    val artifacts: List<AgentArtifact> = emptyList(),
    val completedAt: Instant = Instant.now(),
)

data class AgentArtifact(val relativePath: String, val mediaType: String, val content: String)

data class ProductView(val slug: String, val name: String, val mission: String, val guardrails: String, val createdAt: Instant)
data class StoryCandidateView(val id: Long, val productSlug: String, val title: String, val description: String, val status: String, val createdAt: Instant)
data class WorkspacePublicationView(
    val runId: String,
    val productSlug: String,
    val artifactPath: String,
    val contentHash: String,
    val status: String,
    val pullRequestUrl: String?,
    val commitSha: String?,
)
