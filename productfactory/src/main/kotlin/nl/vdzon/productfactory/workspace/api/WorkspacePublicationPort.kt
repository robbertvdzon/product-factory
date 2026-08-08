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

/** Leest de door de eigenaar geschreven productvisie uit de workspace, zodat cycli op dezelfde tekst redeneren als wat de app toont. */
fun interface WorkspaceVisionPort {
    fun readVision(productSlug: String): String?
}
