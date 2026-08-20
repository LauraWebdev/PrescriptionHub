package media.laura.prescriptionhub.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import media.laura.prescriptionhub.PrescriptionHubApplication
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import java.time.LocalDateTime

/**
 * Handles a dose reminder firing and the actions on its notification.
 */
class DoseReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val application = context.applicationContext as? PrescriptionHubApplication ?: return
        val action = intent.action ?: return
        val pending = goAsync()

        application.applicationScope.launch {
            try {
                if (action == ACTION_REFRESH_HORIZON) {
                    application.reminderScheduler.rearmWindow()
                } else {
                    DoseReminderKey.fromUri(intent.data)?.let { key ->
                        handle(application, action, key)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(
        application: PrescriptionHubApplication,
        action: String,
        key: DoseReminderKey
    ) {
        val context = application.applicationContext
        val scheduler = application.reminderScheduler
        val now = application.nowProvider()
        val dose = findDose(application, key)

        val outcome = reminderOutcome(action = action, key = key, dose = dose, now = now)

        if (dose == null) {
            DoseNotifications.cancel(context, key)
        } else {
            when (outcome) {
                ReminderOutcome.POST_INSISTENT -> {
                    DoseNotifications.post(context, key, dose, now, insistent = true)
                    if (!scheduler.armRelax(key)) {
                        DoseNotifications.post(context, key, dose, now, insistent = false)
                    }
                }

                ReminderOutcome.POST_QUIET ->
                    DoseNotifications.post(context, key, dose, now, insistent = false)

                ReminderOutcome.CANCEL -> DoseNotifications.cancel(context, key)

                ReminderOutcome.MARK_TAKEN -> {
                    application.prescriptionService.setDoseTaken(
                        snapshotId = dose.snapshotId,
                        scheduledDate = key.date,
                        scheduledTime = key.time,
                        taken = true
                    )
                    DoseNotifications.cancel(context, key)
                }

                ReminderOutcome.SNOOZE -> {
                    DoseNotifications.cancel(context, key)
                    scheduler.armSnooze(key)
                }

                ReminderOutcome.NOTHING -> Unit
            }
        }

        if (action == ACTION_FIRE) {
            scheduler.rearmWindow()
        }
    }

    /** Looks the dose slot up again, or `null` when it is no longer scheduled. */
    private suspend fun findDose(
        application: PrescriptionHubApplication,
        key: DoseReminderKey
    ): DoseChecklistItem? {
        val dueAt = key.dueAt
        return application.prescriptionService
            .getDoseChecklistBetween(dueAt, dueAt)
            .firstOrNull { dose ->
                dose.prescriptionId == key.prescriptionId && dose.scheduledTime == key.time
            }
    }

    companion object {
        const val ACTION_FIRE = "media.laura.prescriptionhub.action.FIRE_REMINDER"
        const val ACTION_TAKEN = "media.laura.prescriptionhub.action.DOSE_TAKEN"
        const val ACTION_SNOOZE = "media.laura.prescriptionhub.action.SNOOZE_REMINDER"
        const val ACTION_DISMISSED = "media.laura.prescriptionhub.action.REMINDER_DISMISSED"
        const val ACTION_RELAX = "media.laura.prescriptionhub.action.RELAX_REMINDER"
        const val ACTION_REFRESH_HORIZON = "media.laura.prescriptionhub.action.REFRESH_HORIZON"

        fun intent(context: Context, key: DoseReminderKey, action: String): Intent =
            Intent(context, DoseReminderReceiver::class.java)
                .setAction(action)
                .setData(key.toUri())

        fun horizonRefreshIntent(context: Context): Intent =
            Intent(context, DoseReminderReceiver::class.java)
                .setAction(ACTION_REFRESH_HORIZON)
    }
}
