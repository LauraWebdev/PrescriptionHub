package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ScheduleSummaryTest {
    private val anyStartDate = LocalDate.of(2026, 8, 19)
    private val formats = testFormats()

    @Test
    fun dailyScheduleListsFrequencyThenTimes() {
        val summary = formatScheduleSummary(
            Schedule(
                scheduleType = ScheduleType.DAILY,
                timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(23, 0)),
                startDate = anyStartDate
            ),
            formats
        )

        assertEquals("Daily, 08:00, 23:00", summary)
    }

    @Test
    fun timesAreSortedRegardlessOfInputOrder() {
        val summary = formatScheduleSummary(
            Schedule(
                scheduleType = ScheduleType.DAILY,
                timesOfDay = listOf(LocalTime.of(23, 0), LocalTime.of(8, 0)),
                startDate = anyStartDate
            ),
            formats
        )

        assertEquals("Daily, 08:00, 23:00", summary)
    }

    @Test
    fun scheduleWithoutTimesShowsOnlyFrequency() {
        val summary = formatScheduleSummary(
            Schedule(scheduleType = ScheduleType.DAILY, timesOfDay = emptyList(), startDate = anyStartDate),
            formats
        )

        assertEquals("Daily", summary)
    }

    @Test
    fun specificDaysUseShortDayNamesInWeekOrder() {
        val summary = formatScheduleSummary(
            Schedule(
                scheduleType = ScheduleType.SPECIFIC_DAYS_OF_WEEK,
                daysOfWeek = listOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                timesOfDay = listOf(LocalTime.of(8, 0)),
                startDate = anyStartDate
            ),
            formats
        )

        val expectedDays = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            .joinToString(", ") {
                formats.weekdayShort(it)
            }
        assertEquals("$expectedDays, 08:00", summary)
    }

    @Test
    fun allSevenDaysCollapseToDaily() {
        val summary = formatScheduleSummary(
            Schedule(
                scheduleType = ScheduleType.SPECIFIC_DAYS_OF_WEEK,
                daysOfWeek = DayOfWeek.entries.toList(),
                timesOfDay = listOf(LocalTime.of(8, 0)),
                startDate = anyStartDate
            ),
            formats
        )

        assertEquals("Daily, 08:00", summary)
    }

    @Test
    fun specificDaysWithoutSelectionFallsBackToDaily() {
        val summary = formatScheduleSummary(
            Schedule(
                scheduleType = ScheduleType.SPECIFIC_DAYS_OF_WEEK,
                daysOfWeek = emptyList(),
                timesOfDay = listOf(LocalTime.of(8, 0)),
                startDate = anyStartDate
            ),
            formats
        )

        assertEquals("Daily, 08:00", summary)
    }

    @Test
    fun intervalScheduleIsPluralized() {
        val summary = formatScheduleSummary(
            Schedule(
                scheduleType = ScheduleType.EVERY_X_DAYS,
                everyXDays = 3,
                timesOfDay = listOf(LocalTime.of(9, 30)),
                startDate = anyStartDate
            ),
            formats
        )

        assertEquals("Every 3 days, 09:30", summary)
    }

    @Test
    fun intervalOfOneReadsAsEveryDay() {
        val summary = formatScheduleSummary(
            Schedule(
                scheduleType = ScheduleType.EVERY_X_DAYS,
                everyXDays = 1,
                timesOfDay = listOf(LocalTime.of(9, 30)),
                startDate = anyStartDate
            ),
            formats
        )

        assertEquals("Every day, 09:30", summary)
    }

    @Test
    fun missingIntervalFallsBackToDaily() {
        val summary = formatScheduleSummary(
            Schedule(
                scheduleType = ScheduleType.EVERY_X_DAYS,
                everyXDays = null,
                timesOfDay = listOf(LocalTime.of(9, 30)),
                startDate = anyStartDate
            ),
            formats
        )

        assertEquals("Daily, 09:30", summary)
    }
}
