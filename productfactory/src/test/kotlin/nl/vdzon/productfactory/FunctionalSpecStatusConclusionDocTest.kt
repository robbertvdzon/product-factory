package nl.vdzon.productfactory

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertTrue

/**
 * Bewaakt dat `docs/factory/functional-spec.md` het status/conclusion-onderscheid van een
 * productcyclus expliciet uitlegt (product-31). Geen inhoudelijke wijziging aan de applicatie;
 * puur een documentatie-check die de drie vereiste kernzinnen bevestigt.
 */
class FunctionalSpecStatusConclusionDocTest {
    private val functionalSpec = Path.of("../docs/factory/functional-spec.md")

    /** Normaliseert regeleinden binnen alinea's naar spaties, zodat de check onafhankelijk is van de markdown-regelbreedte. */
    private fun normalizedText() = functionalSpec.readText().replace(Regex("\\s+"), " ")

    @Test fun `functional spec bestaat`() {
        assertTrue(functionalSpec.exists(), "docs/factory/functional-spec.md ontbreekt")
    }

    @Test fun `status is alleen lopend of voltooid, conclusion pas geldig bij voltooid`() {
        val text = normalizedText()
        assertTrue(
            text.contains("Status is altijd óf lopend, óf voltooid"),
            "functional-spec.md mist de status-definitie (alleen 'lopend' of 'voltooid')",
        )
        assertTrue(
            text.contains("Het eindoordeel (conclusion) is pas relevant en geldig zodra de status voltooid is"),
            "functional-spec.md mist dat conclusion pas geldig is zodra status voltooid is",
        )
    }

    @Test fun `onderbroken iteratie wordt autonoom geclassificeerd`() {
        val text = normalizedText()
        assertTrue(
            text.contains(
                "Een tijdens uitvoering onderbroken iteratie wordt automatisch geclassificeerd, zonder apart menselijk besluitmoment.",
            ),
            "functional-spec.md mist de zin over autonome classificatie van een onderbroken iteratie",
        )
    }

    @Test fun `eindoordeel is onvoorwaardelijk immutabel`() {
        val text = normalizedText()
        assertTrue(
            text.contains("Het eindoordeel van een iteratie wijzigt, na vaststelling, niet meer"),
            "functional-spec.md mist de onvoorwaardelijke immutabiliteitszin",
        )
    }
}
