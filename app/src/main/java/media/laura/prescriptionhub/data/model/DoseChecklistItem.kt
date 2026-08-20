package media.laura.prescriptionhub.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A single checklist row for a specific prescription dose at a specific date/time slot.
 *
 * @param reminderLeadMinutes How many minutes before this dose to remind, or `null` for no reminder.
 */
data class DoseChecklistItem(
    val snapshotId: Long,
    val prescriptionId: Long,
    val prescriptionName: String,
    val prescriptionColor: Long,
    val dosis: String,
    val scheduledDate: LocalDate,
    val scheduledTime: LocalTime,
    val taken: Boolean,
    val takenAt: LocalDateTime?,
    val reminderLeadMinutes: Int? = null
) : Comparable<DoseChecklistItem> {
    /** The wall-clock moment this dose is due. */
    val scheduledDateTime: LocalDateTime get() = scheduledDate.atTime(scheduledTime)

    override fun compareTo(other: DoseChecklistItem): Int {
        val timeComparison = scheduledTime.compareTo(other.scheduledTime)
        return if (timeComparison != 0) {
            timeComparison
        } else {
            prescriptionName.compareTo(other.prescriptionName)
        }
    }
}
