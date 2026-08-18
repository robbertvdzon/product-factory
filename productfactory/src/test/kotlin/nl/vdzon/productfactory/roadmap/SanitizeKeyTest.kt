package nl.vdzon.productfactory.roadmap

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SanitizeKeyTest {

    @Test
    fun `already valid keys pass through unchanged`() {
        assertEquals("idea-hkh-bronreis", sanitizeKey("idea-hkh-bronreis"))
        assertEquals("cap1", sanitizeKey("cap1"))
    }

    @Test
    fun `whitespace punctuation and casing are normalized to a single hyphen`() {
        assertEquals("idee-nummer-1", sanitizeKey("Idee  Nummer 1!!!"))
        assertEquals("concept-nummer-1", sanitizeKey("Concept--Nummer_1!!"))
    }

    @Test
    fun `equivalent variants converge to the same stable key`() {
        val variants = listOf("Collectief Geheugen", "collectief_geheugen", "COLLECTIEF-GEHEUGEN!!", "  collectief   geheugen  ")
        assertEquals(setOf("collectief-geheugen"), variants.map(::sanitizeKey).toSet())
    }

    @Test
    fun `result never exceeds the catalog key length limit`() {
        val long = "a".repeat(500)
        assertTrue(sanitizeKey(long).length <= 100)
    }

    @Test
    fun `a key with no valid characters falls back to a deterministic non-blank key`() {
        val first = sanitizeKey("!!! ??? ###")
        val second = sanitizeKey("!!! ??? ###")
        assertTrue(first.isNotBlank())
        assertEquals(first, second)
    }

    @Test
    fun `result never starts or ends with a hyphen`() {
        assertEquals("idee-1", sanitizeKey("-idee-1-"))
        assertEquals("idee-1", sanitizeKey("__idee_1__"))
    }
}
