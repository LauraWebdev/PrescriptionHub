package media.laura.prescriptionhub.reminder

import android.net.Uri
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Identifies one dose slot for reminder purposes.
 *
 * @param prescriptionId The prescription this dose belongs to.
 * @param date The day the dose is due.
 * @param time The time of day the dose is due.
 */
data class DoseReminderKey(
    val prescriptionId: Long,
    val date: LocalDate,
    val time: LocalTime
) {
    val dueAt: LocalDateTime get() = date.atTime(time)
    val insistUntil: LocalDateTime get() = dueAt.plusMinutes(INSIST_GRACE_MINUTES)

    /**
     * A stable, unique URI for this slot.
     */
    fun toUri(): Uri = Uri.parse(
        "$SCHEME://dose/$prescriptionId/${date.toEpochDay()}/${time.toSecondOfDay()}"
    )

    /** The notification tag for this slot. */
    fun toTag(): String = toUri().toString()

    companion object {
        const val INSIST_GRACE_MINUTES = 5L

        private const val SCHEME = "prescriptionhub"

        /** Rebuilds a key from [uri], or `null` when [uri] is not a dose reminder URI. */
        fun fromUri(uri: Uri?): DoseReminderKey? {
            if (uri == null || uri.scheme != SCHEME || uri.host != "dose") return null
            val segments = uri.pathSegments
            if (segments.size != 3) return null
            return try {
                DoseReminderKey(
                    prescriptionId = segments[0].toLong(),
                    date = LocalDate.ofEpochDay(segments[1].toLong()),
                    time = LocalTime.ofSecondOfDay(segments[2].toLong())
                )
            } catch (_: NumberFormatException) {
                null
            } catch (_: java.time.DateTimeException) {
                null
            }
        }
    }
}
