package nl.vdzon.productfactory.preview

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

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
        assertEquals(SyntheticDataset.PR_PREVIEW, config.dataset)
        assertEquals(42, config.requirePreviewSeedingAllowed())
        assertFailsWith<IllegalArgumentException> { config.requireAcceptanceSeedingAllowed() }
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
        assertEquals(SyntheticDataset.ACCEPTANCE, config.dataset)
        config.requireAcceptanceSeedingAllowed()
        assertFailsWith<IllegalArgumentException> { config.requirePreviewSeedingAllowed() }
    }

    @Test
    fun `acceptance marker rejects a pr number`() {
        assertFailsWith<IllegalArgumentException> {
            PreviewRuntimeConfig(enabled = true, marker = PreviewRuntimeConfig.ACCEPTANCE_MARKER, databaseUrl = VALID_DB_URL, previewPrNumber = "42")
        }
    }
}
