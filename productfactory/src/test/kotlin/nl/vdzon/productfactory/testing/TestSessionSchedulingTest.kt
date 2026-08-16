package nl.vdzon.productfactory.testing

import nl.vdzon.productfactory.contracts.WeeklyScheduleView
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestSessionSchedulingTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val schedule = listOf(WeeklyScheduleView("TUESDAY", "10:00"), WeeklyScheduleView("FRIDAY", "10:00"))

    @Test
    fun `two weekly slots are each claimed once`() {
        val tuesday = ZonedDateTime.of(2026, 8, 11, 10, 1, 0, 0, zone)
        assertTrue(isTestSessionDue(schedule, tuesday, null))
        assertFalse(isTestSessionDue(schedule, tuesday, tuesday.toInstant()))
        val friday = ZonedDateTime.of(2026, 8, 14, 10, 1, 0, 0, zone)
        assertTrue(isTestSessionDue(schedule, friday, tuesday.toInstant()))
        assertFalse(isTestSessionDue(schedule, friday, friday.toInstant()))
    }

    @Test
    fun `empty schedule means manual testing only`() {
        val now = ZonedDateTime.of(2026, 8, 14, 12, 0, 0, 0, zone)
        assertFalse(isTestSessionDue(emptyList(), now, null))
    }
}
