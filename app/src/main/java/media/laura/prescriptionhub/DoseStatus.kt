package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.model.DoseChecklistItem
import java.time.LocalDateTime

enum class DoseStatus {
    TAKEN,
    MISSED,
    UPCOMING
}

/**
 * Returns [dose] status as of [now].
 */
fun doseStatus(dose: DoseChecklistItem, now: LocalDateTime): DoseStatus = when {
    dose.taken -> DoseStatus.TAKEN
    dose.scheduledDate.atTime(dose.scheduledTime) > now -> DoseStatus.UPCOMING
    else -> DoseStatus.MISSED
}

/**
 * The calendar's label for [dose], or `null` when the dose is still due.
 */
fun formatDoseStatus(dose: DoseChecklistItem, now: LocalDateTime): String? =
    when (doseStatus(dose, now)) {
        DoseStatus.TAKEN -> dose.takenAt?.let { "Taken ${formatTimeOfDay(it.toLocalTime())}" } ?: "Taken"
        DoseStatus.MISSED -> "Not taken"
        DoseStatus.UPCOMING -> null
    }
