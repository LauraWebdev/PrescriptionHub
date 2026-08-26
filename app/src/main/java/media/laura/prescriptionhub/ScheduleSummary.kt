package media.laura.prescriptionhub

import android.content.res.Resources
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import java.time.DayOfWeek

/**
 * One-line description of when a prescription is due.
 *
 * @param formats How the device writes times and weekday names.
 * @param resources Where the summary text is read from, in the user's language.
 */
fun formatScheduleSummary(
    schedule: Schedule,
    formats: DateTimeFormats,
    resources: Resources
): String {
    val frequency = formatFrequency(schedule, formats, resources)
    val times = schedule.timesOfDay.sorted().map(formats::time)
    return (listOf(frequency) + times).joinToString(resources.separator())
}

private fun formatFrequency(
    schedule: Schedule,
    formats: DateTimeFormats,
    resources: Resources
): String = when (schedule.scheduleType) {
    ScheduleType.DAILY -> resources.dailyLabel()

    ScheduleType.SPECIFIC_DAYS_OF_WEEK -> {
        val days = schedule.daysOfWeek.distinct().sorted()
        when {
            days.isEmpty() -> resources.dailyLabel()
            days.size == DayOfWeek.entries.size -> resources.dailyLabel()
            else -> days.joinToString(resources.separator(), transform = formats::weekdayShort)
        }
    }

    ScheduleType.EVERY_X_DAYS -> {
        val interval = schedule.everyXDays
        when {
            interval == null || interval <= 0 -> resources.dailyLabel()
            interval == 1 -> resources.getString(R.string.schedule_summary_every_day)
            else -> resources.getString(R.string.schedule_summary_every_x_days, interval)
        }
    }
}

private fun Resources.dailyLabel(): String = getString(R.string.schedule_summary_daily)

private fun Resources.separator(): String = getString(R.string.list_separator)
