package nl.vdzon.productfactory.workspace.api

import nl.vdzon.productfactory.contracts.WorkspacePublicationView

data class WorkspaceArtifact(
    val runId: String,
    val productSlug: String,
    val relativePath: String,
    val content: String,
)

fun interface WorkspacePublicationPort {
    fun publish(artifact: WorkspaceArtifact): WorkspacePublicationView
}
