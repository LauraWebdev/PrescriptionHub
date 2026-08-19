package media.laura.prescriptionhub.data.model

import java.time.LocalTime

/**
 * Represents a single scheduled dosage event for a prescription at a specific time of day.
 */
data class ScheduledDose(
    val prescription: Prescription,
    val time: LocalTime
) : Comparable<ScheduledDose> {
    override fun compareTo(other: ScheduledDose): Int {
        val timeComparison = this.time.compareTo(other.time)
        return if (timeComparison != 0) {
            timeComparison
        } else {
            this.prescription.name.compareTo(other.prescription.name)
        }
    }
}
