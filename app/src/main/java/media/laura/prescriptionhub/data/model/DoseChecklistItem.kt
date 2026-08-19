package media.laura.prescriptionhub.data.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A single checklist row for a specific prescription dose at a specific date/time slot.
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
    val takenAt: LocalDateTime?
) : Comparable<DoseChecklistItem> {
    override fun compareTo(other: DoseChecklistItem): Int {
        val timeComparison = scheduledTime.compareTo(other.scheduledTime)
        return if (timeComparison != 0) {
            timeComparison
        } else {
            prescriptionName.compareTo(other.prescriptionName)
        }
    }
}
