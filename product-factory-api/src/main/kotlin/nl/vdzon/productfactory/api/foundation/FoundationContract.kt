package nl.vdzon.productfactory.api.foundation

import nl.vdzon.productfactory.api.shared.ImplementationIdentity

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
}

interface DeploymentRevisionResolver {
    fun resolve(baseUrl: String, revisionEndpoint: String, revisionJsonPath: String): String
}
