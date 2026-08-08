package nl.vdzon.productfactory.preview

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Fail-closed grens voor wegwerpbare per-PR previewomgevingen en de standing acceptatieomgeving.
 *
 * Preview-modus mag alleen aan staan met een van de twee verwachte markers en een in-namespace
 * databaseverbinding. Een per ongeluk gekopieerde productie-databaseverbinding voorkomt daardoor dat
 * de applicatie opstart, in plaats van stilzwijgend tegen productie te draaien. Alleen de per-PR-marker
 * vereist een positief pull-requestnummer; de acceptatieomgeving is niet aan een PR gebonden.
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
            require(marker == REQUIRED_MARKER || marker == ACCEPTANCE_MARKER) {
                "Preview-modus vereist de verwachte preview- of acceptance-marker"
            }
            require(PREVIEW_DATABASE.matches(databaseUrl)) {
                "Preview-modus mag alleen de in-namespace previewdatabase gebruiken"
            }
            if (marker == REQUIRED_MARKER) {
                requireNotNull(prNumber) { "Preview-modus vereist een positief pull-requestnummer" }
            } else {
                require(previewPrNumber.isBlank()) { "De acceptatieomgeving is niet aan een pull-requestnummer gebonden" }
            }
        } else {
            require(marker.isBlank()) { "De preview-marker mag niet gezet zijn buiten preview-modus" }
            require(previewPrNumber.isBlank()) { "Het preview-PR-nummer mag niet gezet zijn buiten preview-modus" }
        }
    }

    fun requireSeedingAllowed(): Int {
        require(enabled) { "Previewtestdata mag alleen in een geverifieerde previewomgeving worden aangemaakt" }
        return prNumber ?: ACCEPTANCE_SEED_ID
    }

    companion object {
        const val REQUIRED_MARKER = "product-factory-pr-preview"
        const val ACCEPTANCE_MARKER = "product-factory-acceptance"
        private const val ACCEPTANCE_SEED_ID = 0

        private val PREVIEW_DATABASE =
            Regex("^jdbc:postgresql://postgres(?::5432)?/productfactory(?:\\?.*)?$")
    }
}
