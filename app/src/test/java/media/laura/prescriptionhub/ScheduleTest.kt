package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ScheduleTest {
    private val anyStartDate = LocalDate.of(2026, 8, 19)

    @Test
    fun testDailySchedule() {
        val schedule = Schedule(
            scheduleType = ScheduleType.DAILY,
            timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(23, 0)),
            startDate = anyStartDate
        )

        // Should be active on any date
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 8, 19)))
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 8, 20)))
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun testSpecificDaysOfWeekSchedule() {
        val schedule = Schedule(
            scheduleType = ScheduleType.SPECIFIC_DAYS_OF_WEEK,
            daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            timesOfDay = listOf(LocalTime.of(13, 0), LocalTime.MIDNIGHT),
            startDate = anyStartDate
        )

        // 2026-08-17 is Monday
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 8, 17)))
        // 2026-08-18 is Tuesday
        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 8, 18)))
        // 2026-08-19 is Wednesday
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 8, 19)))
        // 2026-08-20 is Thursday
        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 8, 20)))
        // 2026-08-21 is Friday
        assertTrue(schedule.isScheduledOn(LocalDate.of(2026, 8, 21)))
        // 2026-08-22 is Saturday
        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 8, 22)))
        // 2026-08-23 is Sunday
        assertFalse(schedule.isScheduledOn(LocalDate.of(2026, 8, 23)))
    }

    @Test
    fun testEveryXDaysSchedule() {
        val startDate = LocalDate.of(2026, 8, 1)
        val scheduleEvery2Days = Schedule(
            scheduleType = ScheduleType.EVERY_X_DAYS,
            everyXDays = 2,
            startDate = startDate,
            timesOfDay = listOf(LocalTime.of(14, 0))
        )

        // Day 0: 2026-08-01 (should be active)
        assertTrue(scheduleEvery2Days.isScheduledOn(LocalDate.of(2026, 8, 1)))
        // Day 1: 2026-08-02 (not active)
        assertFalse(scheduleEvery2Days.isScheduledOn(LocalDate.of(2026, 8, 2)))
        // Day 2: 2026-08-03 (active)
        assertTrue(scheduleEvery2Days.isScheduledOn(LocalDate.of(2026, 8, 3)))
        // Day 3: 2026-08-04 (not active)
        assertFalse(scheduleEvery2Days.isScheduledOn(LocalDate.of(2026, 8, 4)))
        // Before start date (not active)
        assertFalse(scheduleEvery2Days.isScheduledOn(LocalDate.of(2026, 7, 31)))

        val scheduleEvery7Days = Schedule(
            scheduleType = ScheduleType.EVERY_X_DAYS,
            everyXDays = 7,
            startDate = startDate
        )
        assertTrue(scheduleEvery7Days.isScheduledOn(LocalDate.of(2026, 8, 1)))
        assertTrue(scheduleEvery7Days.isScheduledOn(LocalDate.of(2026, 8, 8)))
        assertTrue(scheduleEvery7Days.isScheduledOn(LocalDate.of(2026, 8, 15)))
        assertFalse(scheduleEvery7Days.isScheduledOn(LocalDate.of(2026, 8, 2)))
    }

    @Test
    fun testInvalidEveryXDaysSchedule() {
        val scheduleNoInterval = Schedule(
            scheduleType = ScheduleType.EVERY_X_DAYS,
            everyXDays = null,
            startDate = anyStartDate
        )
        assertFalse(scheduleNoInterval.isScheduledOn(anyStartDate))

        val scheduleZeroInterval = Schedule(
            scheduleType = ScheduleType.EVERY_X_DAYS,
            everyXDays = 0,
            startDate = anyStartDate
        )
        assertFalse(scheduleZeroInterval.isScheduledOn(anyStartDate))
    }
}
