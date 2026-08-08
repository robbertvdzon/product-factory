package nl.vdzon.productfactory.autonomy

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IterationSchedulingTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val times = listOf("03:00", "08:00", "21:00")

    private fun at(hour: Int, minute: Int) = ZonedDateTime.of(2026, 8, 8, hour, minute, 0, 0, zone)

    @Test fun `each configured time fires its own cycle on the same day`() {
        assertTrue(isIterationDue(times, at(3, 5), lastIterationToday = null))

        val afterFirstCycle = at(3, 5).toInstant()
        assertFalse(isIterationDue(times, at(3, 30), afterFirstCycle))
        assertFalse(isIterationDue(times, at(7, 59), afterFirstCycle))
        assertTrue(isIterationDue(times, at(8, 1), afterFirstCycle))

        val afterSecondCycle = at(8, 1).toInstant()
        assertFalse(isIterationDue(times, at(20, 59), afterSecondCycle))
        assertTrue(isIterationDue(times, at(21, 0), afterSecondCycle))

        val afterThirdCycle = at(21, 0).toInstant()
        assertFalse(isIterationDue(times, at(23, 59), afterThirdCycle))
    }

    @Test fun `no configured times means never due`() {
        assertFalse(isIterationDue(emptyList(), at(3, 0), null))
    }

    @Test fun `only the time of day of the last run is compared, the caller is responsible for scoping it to today`() {
        // isIterationDue zelf is dag-agnostisch: de coordinator geeft alleen een instant door wanneer die uit
        // "vandaag" komt (lastAutonomousIterationToday), dus deze functie hoeft geen datum te vergelijken.
        assertFalse(isIterationDue(listOf("03:00"), at(3, 10), lastIterationToday = at(3, 5).toInstant()))
        assertTrue(isIterationDue(listOf("08:00"), at(8, 10), lastIterationToday = at(3, 5).toInstant()))
    }
}
