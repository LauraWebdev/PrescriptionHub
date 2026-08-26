package media.laura.prescriptionhub

import android.content.res.Resources
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
 *
 * @param formats How the device writes times.
 * @param resources Where the label text is read from, in the user's language.
 */
fun formatDoseStatus(
    dose: DoseChecklistItem,
    now: LocalDateTime,
    formats: DateTimeFormats,
    resources: Resources
): String? =
    when (doseStatus(dose, now)) {
        DoseStatus.TAKEN -> dose.takenAt
            ?.let { resources.getString(R.string.dose_status_taken_at, formats.time(it.toLocalTime())) }
            ?: resources.getString(R.string.dose_status_taken)

        DoseStatus.MISSED -> resources.getString(R.string.dose_status_missed)
        DoseStatus.UPCOMING -> null
    }
