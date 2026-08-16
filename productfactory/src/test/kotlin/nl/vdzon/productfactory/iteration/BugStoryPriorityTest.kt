package nl.vdzon.productfactory.iteration

import kotlin.test.Test
import kotlin.test.assertFailsWith

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
}
