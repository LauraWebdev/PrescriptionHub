package media.laura.prescriptionhub.data.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Schedule settings for a prescription.
 *
 * @param scheduleType The recurrence type: DAILY, SPECIFIC_DAYS_OF_WEEK, or EVERY_X_DAYS.
 * @param daysOfWeek Specific days of the week when scheduleType is SPECIFIC_DAYS_OF_WEEK.
 * @param everyXDays Interval in days when scheduleType is EVERY_X_DAYS.
 * @param timesOfDay List of times during the day when the prescription should be taken (e.g., 08:00, 14:00, 23:00).
 * @param startDate Reference/start date used to compute intervals for EVERY_X_DAYS. Defaults to current date.
 * @param reminderLeadMinutes How many minutes before a dose to remind, or `null` for no reminder.
 */
data class Schedule(
    val scheduleType: ScheduleType = ScheduleType.DAILY,
    val daysOfWeek: List<DayOfWeek> = emptyList(),
    val everyXDays: Int? = null,
    val timesOfDay: List<LocalTime> = emptyList(),
    val startDate: LocalDate = LocalDate.now(),
    val reminderLeadMinutes: Int? = null
) {
    /**
     * Determines whether this schedule is active/due on the given [date].
     */
    fun isScheduledOn(date: LocalDate): Boolean {
        return when (scheduleType) {
            ScheduleType.DAILY -> true
            ScheduleType.SPECIFIC_DAYS_OF_WEEK -> daysOfWeek.contains(date.dayOfWeek)
            ScheduleType.EVERY_X_DAYS -> {
                val interval = everyXDays ?: return false
                if (interval <= 0) return false
                val daysBetween = ChronoUnit.DAYS.between(startDate, date)
                daysBetween >= 0 && (daysBetween % interval == 0L)
            }
        }
    }
}
