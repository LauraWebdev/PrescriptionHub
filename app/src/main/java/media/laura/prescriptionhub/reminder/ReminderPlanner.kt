package media.laura.prescriptionhub.reminder

import media.laura.prescriptionhub.data.model.DoseChecklistItem
import java.time.LocalDateTime

/**
 * A reminder that should be armed for one dose slot.
 *
 * @param key Identifies the dose slot.
 * @param triggerAt When the reminder should fire.
 * @param dose The dose the reminder is about.
 */
data class PlannedReminder(
    val key: DoseReminderKey,
    val triggerAt: LocalDateTime,
    val dose: DoseChecklistItem
)

/**
 * Works out which of [doses] still need a reminder armed.
 *
 * @param doses The dose slots to consider.
 * @param now The current moment.
 * @param windowEnd The end of the scheduling horizon.
 */
fun planReminders(
    doses: List<DoseChecklistItem>,
    now: LocalDateTime,
    windowEnd: LocalDateTime
): List<PlannedReminder> = doses.mapNotNull { dose ->
    if (dose.taken) return@mapNotNull null
    val lead = dose.reminderLeadMinutes ?: return@mapNotNull null
    if (lead < 0) return@mapNotNull null

    val triggerAt = dose.scheduledDateTime.minusMinutes(lead.toLong())
    if (triggerAt < now || triggerAt > windowEnd) return@mapNotNull null

    PlannedReminder(
        key = DoseReminderKey(
            prescriptionId = dose.prescriptionId,
            date = dose.scheduledDate,
            time = dose.scheduledTime
        ),
        triggerAt = triggerAt,
        dose = dose
    )
}.sortedBy { it.triggerAt }
