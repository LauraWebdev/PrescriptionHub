package media.laura.prescriptionhub.data.model

import java.time.LocalDate

/**
 * How much of one day's doses have been taken, for the calendar's day ring.
 *
 * @param date The day being summarized.
 * @param doseCount How many doses were scheduled that day.
 * @param takenCount How many of them have been recorded as taken.
 */
data class DoseDayProgress(
    val date: LocalDate,
    val doseCount: Int,
    val takenCount: Int
) {
    /** Share of taken doses in `0f..1f`. */
    val progress: Float get() = if (doseCount == 0) 0f else takenCount.toFloat() / doseCount

    /** Whether every dose of the day has been taken. */
    val isComplete: Boolean get() = doseCount > 0 && takenCount == doseCount
}
