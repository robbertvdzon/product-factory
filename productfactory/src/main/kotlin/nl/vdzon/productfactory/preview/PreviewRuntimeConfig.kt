package nl.vdzon.productfactory.preview

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Fail-closed grens voor wegwerpbare per-PR previewomgevingen.
 *
 * Preview-modus mag alleen aan staan met de verwachte marker en een in-namespace databaseverbinding.
 * Een per ongeluk gekopieerde productie-databaseverbinding voorkomt daardoor dat de applicatie
 * opstart, in plaats van stilzwijgend tegen productie te draaien.
 */
@Component
class PreviewRuntimeConfig(
    @param:Value("\${PF_PREVIEW_ENABLED:false}") val enabled: Boolean,
    @param:Value("\${PF_PREVIEW_MARKER:}") val marker: String,
    @param:Value("\${PF_DB_URL:}") databaseUrl: String,
    @param:Value("\${PF_PREVIEW_PR_NUMBER:}") previewPrNumber: String,
) {
    val prNumber: Int? = previewPrNumber.toIntOrNull()?.takeIf { it > 0 }

    init {
        if (enabled) {
            require(marker == REQUIRED_MARKER) { "Preview-modus vereist de verwachte preview-marker" }
            require(PREVIEW_DATABASE.matches(databaseUrl)) {
                "Preview-modus mag alleen de in-namespace previewdatabase gebruiken"
            }
            requireNotNull(prNumber) { "Preview-modus vereist een positief pull-requestnummer" }
        } else {
            require(marker.isBlank()) { "De preview-marker mag niet gezet zijn buiten preview-modus" }
            require(previewPrNumber.isBlank()) { "Het preview-PR-nummer mag niet gezet zijn buiten preview-modus" }
        }
    }

    fun requireSeedingAllowed(): Int = requireNotNull(prNumber) {
        "Previewtestdata mag alleen in een geverifieerde previewomgeving worden aangemaakt"
    }

    companion object {
        const val REQUIRED_MARKER = "product-factory-pr-preview"

        private val PREVIEW_DATABASE =
            Regex("^jdbc:postgresql://postgres(?::5432)?/productfactory(?:\\?.*)?$")
    }
}
