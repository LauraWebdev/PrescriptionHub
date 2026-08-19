package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.model.DoseChecklistItem
import java.time.LocalTime

/**
 * All doses of one day that share the same time of day.
 *
 * @param time The time of day the doses are scheduled for.
 * @param doses The doses of that slot, sorted by prescription name.
 */
data class DoseTimeGroup(
    val time: LocalTime,
    val doses: List<DoseChecklistItem>
) {
    val takenCount: Int get() = doses.count { it.taken }
    val isComplete: Boolean get() = doses.isNotEmpty() && takenCount == doses.size
    val progress: Float get() = if (doses.isEmpty()) 0f else takenCount.toFloat() / doses.size
}

/**
 * Groups a day's checklist into one [DoseTimeGroup] per time of day, earliest slot first.
 */
fun groupDosesByTime(doses: List<DoseChecklistItem>): List<DoseTimeGroup> {
    return doses
        .groupBy { it.scheduledTime }
        .map { (time, dosesOfSlot) -> DoseTimeGroup(time = time, doses = dosesOfSlot.sorted()) }
        .sortedBy { it.time }
}
