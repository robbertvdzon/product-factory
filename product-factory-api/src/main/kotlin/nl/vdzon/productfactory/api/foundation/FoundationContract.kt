package nl.vdzon.productfactory.api.foundation

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
