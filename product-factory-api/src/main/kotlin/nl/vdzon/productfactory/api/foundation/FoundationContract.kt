package nl.vdzon.productfactory.api.foundation

enum class FoundationState { READY }

data class FoundationStatus(
    val application: String,
    val state: FoundationState,
    val message: String,
)
