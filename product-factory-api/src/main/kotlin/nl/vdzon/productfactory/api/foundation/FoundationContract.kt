package nl.vdzon.productfactory.api.foundation

import nl.vdzon.productfactory.api.shared.ImplementationIdentity
import nl.vdzon.productfactory.api.shared.CapabilityNotAvailable

enum class FoundationState { READY }

data class FoundationStatus(
    val application: String,
    val state: FoundationState,
    val message: String,
)

data class BuildIdentity(
    val applicationVersion: String,
    val apiVersion: String,
    val gitRevision: String,
    val buildTime: String,
    val environment: String,
    val backendBuildIdentity: String,
)

data class ImplementationManifest(
    val manifestVersion: String,
    val implementations: Map<String, ImplementationIdentity>,
)

interface PublicGitRevisionResolver {
    fun resolveHead(publicGitUrl: String): String

    /** Returns false only when the deployed revision is known not to contain all required commits. */
    fun containsCommits(publicGitUrl: String, deployedRevision: String, requiredCommitShas: Set<String>): Boolean {
        if (requiredCommitShas.all { it == deployedRevision }) return true
        throw CapabilityNotAvailable("Deze Git-resolver kan commit-afstamming niet controleren.")
    }
}

interface DeploymentRevisionResolver {
    fun resolve(baseUrl: String, revisionEndpoint: String, revisionJsonPath: String): String
}
