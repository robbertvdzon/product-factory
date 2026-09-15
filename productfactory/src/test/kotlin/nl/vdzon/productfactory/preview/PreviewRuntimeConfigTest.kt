package nl.vdzon.productfactory.preview

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

private const val VALID_DB_URL = "jdbc:postgresql://postgres:5432/productfactory"

class PreviewRuntimeConfigTest {
    @Test
    fun `disabled and clean configuration is valid`() {
        PreviewRuntimeConfig(enabled = false, marker = "", databaseUrl = VALID_DB_URL, previewPrNumber = "")
    }

    @Test
    fun `disabled configuration rejects a leftover marker`() {
        assertFailsWith<IllegalArgumentException> {
            PreviewRuntimeConfig(enabled = false, marker = PreviewRuntimeConfig.REQUIRED_MARKER, databaseUrl = VALID_DB_URL, previewPrNumber = "")
        }
    }

    @Test
    fun `disabled configuration rejects a leftover pr number`() {
        assertFailsWith<IllegalArgumentException> {
            PreviewRuntimeConfig(enabled = false, marker = "", databaseUrl = VALID_DB_URL, previewPrNumber = "42")
        }
    }

    @Test
    fun `enabled configuration with everything correct is valid`() {
        val config = PreviewRuntimeConfig(enabled = true, marker = PreviewRuntimeConfig.REQUIRED_MARKER, databaseUrl = VALID_DB_URL, previewPrNumber = "42")
        assert(config.prNumber == 42)
    }

    @Test
    fun `enabled configuration rejects a wrong marker`() {
        assertFailsWith<IllegalArgumentException> {
            PreviewRuntimeConfig(enabled = true, marker = "onjuist", databaseUrl = VALID_DB_URL, previewPrNumber = "42")
        }
    }

    @Test
    fun `enabled configuration rejects a production-looking database url`() {
        assertFailsWith<IllegalArgumentException> {
            PreviewRuntimeConfig(
                enabled = true,
                marker = PreviewRuntimeConfig.REQUIRED_MARKER,
                databaseUrl = "jdbc:postgresql://prod-db.internal:5432/productfactory",
                previewPrNumber = "42",
            )
        }
    }

    @Test
    fun `enabled configuration rejects a missing or invalid pr number`() {
        assertFailsWith<IllegalArgumentException> {
            PreviewRuntimeConfig(enabled = true, marker = PreviewRuntimeConfig.REQUIRED_MARKER, databaseUrl = VALID_DB_URL, previewPrNumber = "")
        }
        assertFailsWith<IllegalArgumentException> {
            PreviewRuntimeConfig(enabled = true, marker = PreviewRuntimeConfig.REQUIRED_MARKER, databaseUrl = VALID_DB_URL, previewPrNumber = "0")
        }
    }

    @Test
    fun `acceptance marker is valid without a pr number`() {
        val config = PreviewRuntimeConfig(enabled = true, marker = PreviewRuntimeConfig.ACCEPTANCE_MARKER, databaseUrl = VALID_DB_URL, previewPrNumber = "")
        assert(config.prNumber == null)
        assert(config.requireSeedingAllowed() == 0)
    }

    @Test
    fun `acceptance marker rejects a pr number`() {
        assertFailsWith<IllegalArgumentException> {
            PreviewRuntimeConfig(enabled = true, marker = PreviewRuntimeConfig.ACCEPTANCE_MARKER, databaseUrl = VALID_DB_URL, previewPrNumber = "42")
        }
    }
    private val central = "jdbc:postgresql://postgres.postgres-nonproduction.svc:5432/"
    private val tls = "?sslmode=verify-full&sslrootcert=/etc/postgres-ca/ca.crt"

    @Test
    fun `allows only the own central acceptance and PR databases`() {
        assertTrue(PreviewRuntimeConfig(true, PreviewRuntimeConfig.ACCEPTANCE_MARKER, central + "pf_acc" + tls, "").enabled)
        assertTrue(PreviewRuntimeConfig(true, PreviewRuntimeConfig.REQUIRED_MARKER, central + "pf_pr_42_abcdef12" + tls, "42").enabled)
    }

    @Test
    fun `rejects cross environment connections and JDBC overrides`() {
        val invalid = listOf(
            central + "pf_prod" + tls,
            central.replace("nonproduction", "production") + "pf_acc" + tls,
            central + "pvdd_acc" + tls,
            central + "pf_pr_43_abcdef12" + tls,
            central + "pf_acc",
            central + "pf_acc" + tls.replace("verify-full", "require"),
            central + "pf_acc" + tls + "&sslmode=disable",
            central + "pf_acc" + tls + "&host=production",
            central + "pf_acc" + tls + "&options=unsafe",
            central.replace(":5432", ":5433") + "pf_acc" + tls,
            central.replace("//", "//user@") + "pf_acc" + tls,
            central + "pf_acc" + tls + "#fragment",
            "jdbc:postgresql://database:5432/hkh?host=production",
        )
        invalid.forEach { url ->
            assertFailsWith<IllegalArgumentException> {
                PreviewRuntimeConfig(true, PreviewRuntimeConfig.ACCEPTANCE_MARKER, url, "")
            }
        }
        assertFailsWith<IllegalArgumentException> {
            PreviewRuntimeConfig(true, PreviewRuntimeConfig.REQUIRED_MARKER, central + "pf_pr_43_abcdef12" + tls, "42")
        }
    }
}
