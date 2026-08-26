package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import java.time.DayOfWeek

/**
 * One-line description of when a prescription is due.
 *
 * @param formats How the device writes times and weekday names.
 */
fun formatScheduleSummary(schedule: Schedule, formats: DateTimeFormats): String {
    val frequency = formatFrequency(schedule, formats)
    val times = schedule.timesOfDay.sorted().map(formats::time)
    return (listOf(frequency) + times).joinToString(", ")
}

private fun formatFrequency(
    schedule: Schedule,
    formats: DateTimeFormats
): String = when (schedule.scheduleType) {
    ScheduleType.DAILY -> DAILY_LABEL

    ScheduleType.SPECIFIC_DAYS_OF_WEEK -> {
        val days = schedule.daysOfWeek.distinct().sorted()
        when {
            days.isEmpty() -> DAILY_LABEL
            days.size == DayOfWeek.entries.size -> DAILY_LABEL
            else -> days.joinToString(", ", transform = formats::weekdayShort)
        }
    }

    ScheduleType.EVERY_X_DAYS -> {
        val interval = schedule.everyXDays
        when {
            interval == null || interval <= 0 -> DAILY_LABEL
            interval == 1 -> "Every day"
            else -> "Every $interval days"
        }
    }
}

private const val DAILY_LABEL = "Daily"
