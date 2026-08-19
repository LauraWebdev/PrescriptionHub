package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * One-line description of when a prescription is due.
 */
fun formatScheduleSummary(schedule: Schedule): String {
    val frequency = formatFrequency(schedule)
    val times = schedule.timesOfDay.sorted().map(::formatTimeOfDay)
    return (listOf(frequency) + times).joinToString(", ")
}

private fun formatFrequency(schedule: Schedule): String = when (schedule.scheduleType) {
    ScheduleType.DAILY -> DAILY_LABEL

    ScheduleType.SPECIFIC_DAYS_OF_WEEK -> {
        val days = schedule.daysOfWeek.distinct().sorted()
        when {
            days.isEmpty() -> DAILY_LABEL
            days.size == DayOfWeek.entries.size -> DAILY_LABEL
            else -> days.joinToString(", ") {
                it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
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
