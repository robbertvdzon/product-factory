package nl.vdzon.productfactory.iteration

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BugStoryPriorityTest {
    @Test
    fun `important bugs block feature candidates`() {
        assertFailsWith<IllegalArgumentException> {
            validateBugStorySelection(2, listOf(42), setOf(42), emptySet())
        }
        validateBugStorySelection(2, listOf(42, 42), setOf(42), emptySet())
    }

    @Test
    fun `three story batch includes a small bug when available`() {
        assertFailsWith<IllegalArgumentException> {
            validateBugStorySelection(3, emptyList(), emptySet(), setOf(7))
        }
        validateBugStorySelection(3, listOf(7), emptySet(), setOf(7))
    }

    @Test
    fun `prompt breaks ties between multiple equally important bugs by age instead of leaving the choice open`() {
        assertTrue(
            "hetzelfde P0- of P1-niveau" in BUG_PRIORITY_GUIDANCE,
            "Promptrichtlijn moet een tie-breaker geven voor meerdere even ernstige P0- of P1-bugs",
        )
        assertTrue(
            "kies dan de oudste bug" in BUG_PRIORITY_GUIDANCE,
            "Promptrichtlijn moet de oudste bug voorschrijven, net als bij meerdere kleine bugs",
        )
    }
}
