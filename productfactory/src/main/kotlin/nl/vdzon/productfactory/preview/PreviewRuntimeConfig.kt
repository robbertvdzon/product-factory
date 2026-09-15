package nl.vdzon.productfactory.preview

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
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
            require(isIsolatedDatabase(databaseUrl)) {
                "Preview-modus vereist een eigen geverifieerde non-productiondatabase"
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

    private fun isIsolatedDatabase(jdbcUrl: String): Boolean = runCatching {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) return false
        val uri = URI(jdbcUrl.removePrefix("jdbc:"))
        if (uri.rawUserInfo != null || uri.rawFragment != null || uri.port !in listOf(-1, 5432)) return false
        val parameters = uri.rawQuery?.split("&")?.map { part ->
            val pair = part.split("=", limit = 2)
            if (pair.size != 2) return false
            URLDecoder.decode(pair[0], StandardCharsets.UTF_8) to URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
        } ?: emptyList()
        if (parameters.map { it.first }.distinct().size != parameters.size) return false
        if (parameters.any { it.first !in setOf("sslmode", "sslrootcert") }) return false
        if (uri.host == "postgres" && uri.rawPath == "/productfactory") return true
        if (uri.host !in setOf("postgres.postgres-nonproduction.svc", "postgres.postgres-nonproduction.svc.cluster.local")) return false
        val expected = if (marker == ACCEPTANCE_MARKER) Regex("/pf_acc")
            else Regex("/pf_pr_${prNumber}_[a-f0-9]{8}")
        val query = parameters.toMap()
        expected.matches(uri.rawPath) && query["sslmode"] == "verify-full" &&
            query["sslrootcert"] == "/etc/postgres-ca/ca.crt"
    }.getOrDefault(false)

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
