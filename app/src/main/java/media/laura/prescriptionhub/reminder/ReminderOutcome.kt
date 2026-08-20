package media.laura.prescriptionhub.reminder

import media.laura.prescriptionhub.data.model.DoseChecklistItem
import java.time.LocalDateTime

/**
 * What should happen when a reminder event arrives.
 */
enum class ReminderOutcome {
    POST_INSISTENT,
    POST_QUIET,
    CANCEL,
    MARK_TAKEN,
    SNOOZE,
    NOTHING
}

/**
 * Defines a reminder event.
 *
 * @param action One of `ACTION_` on [DoseReminderReceiver].
 * @param key The dose slot the event is about.
 * @param dose The dose as it currently stands, or `null` when it is no longer scheduled.
 * @param now The current moment.
 */
fun reminderOutcome(
    action: String,
    key: DoseReminderKey,
    dose: DoseChecklistItem?,
    now: LocalDateTime
): ReminderOutcome {
    if (dose == null || dose.taken) {
        return ReminderOutcome.CANCEL
    }

    val insisting = now <= key.insistUntil

    return when (action) {
        DoseReminderReceiver.ACTION_FIRE -> when {
            // Reminders were switched off after this alarm was armed.
            dose.reminderLeadMinutes == null -> ReminderOutcome.CANCEL
            insisting -> ReminderOutcome.POST_INSISTENT
            else -> ReminderOutcome.POST_QUIET
        }

        DoseReminderReceiver.ACTION_TAKEN -> ReminderOutcome.MARK_TAKEN

        DoseReminderReceiver.ACTION_SNOOZE -> ReminderOutcome.SNOOZE

        DoseReminderReceiver.ACTION_DISMISSED -> when {
            insisting -> ReminderOutcome.POST_INSISTENT
            else -> ReminderOutcome.NOTHING
        }

        DoseReminderReceiver.ACTION_RELAX -> ReminderOutcome.POST_QUIET

        else -> ReminderOutcome.NOTHING
    }
}
