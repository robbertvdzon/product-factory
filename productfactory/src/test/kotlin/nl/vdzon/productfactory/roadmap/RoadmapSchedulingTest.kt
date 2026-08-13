package nl.vdzon.productfactory.roadmap

import nl.vdzon.productfactory.contracts.WeeklyScheduleView
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoadmapSchedulingTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val schedule = listOf(
        WeeklyScheduleView("MONDAY", "10:00"),
        WeeklyScheduleView("THURSDAY", "12:00"),
    )

    private fun at(day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(2026, 8, day, hour, minute, 0, 0, zone)

    @Test
    fun `each configured weekday and time claims one roadmap session`() {
        val mondayAfterSlot = at(10, 10, 1)
        assertTrue(isRoadmapSessionDue(schedule, mondayAfterSlot, null))
        assertFalse(isRoadmapSessionDue(schedule, mondayAfterSlot, mondayAfterSlot.toInstant()))

        val thursdayAfterSlot = at(13, 12, 1)
        assertTrue(isRoadmapSessionDue(schedule, thursdayAfterSlot, mondayAfterSlot.toInstant()))
        assertFalse(isRoadmapSessionDue(schedule, thursdayAfterSlot, thursdayAfterSlot.toInstant()))
    }

    @Test
    fun `future slot on the current weekday does not fire early`() {
        val thursdayBeforeSlot = at(13, 11, 59)
        val mondaySession = at(10, 10, 1).toInstant()
        assertFalse(isRoadmapSessionDue(schedule, thursdayBeforeSlot, mondaySession))
    }

    @Test
    fun `empty roadmap schedule means manual sessions only`() {
        assertFalse(isRoadmapSessionDue(emptyList(), at(13, 12, 1), null))
    }
}
