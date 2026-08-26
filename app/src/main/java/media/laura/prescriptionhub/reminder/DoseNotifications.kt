package media.laura.prescriptionhub.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import media.laura.prescriptionhub.MainActivity
import media.laura.prescriptionhub.R
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.DateTimeFormats
import java.time.Duration
import java.time.LocalDateTime

/**
 * Builds and posts dose reminder notifications.
 * CHANNEL_INSISTENT is annoying and reposts, CHANNEL_QUIET is for after the schedule time and can be dismissed.
 */
object DoseNotifications {

    const val CHANNEL_INSISTENT = "dose_reminders"

    const val CHANNEL_QUIET = "dose_reminders_quiet"

    private const val GROUP_KEY = "media.laura.prescriptionhub.DOSE_REMINDERS"

    /**
     * Same NOTIFICATION_ID for every reminder is okay, because they are grouped by key.
     */
    private const val NOTIFICATION_ID = 1

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_INSISTENT,
                context.getString(R.string.notification_channel_insistent_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_insistent_description)
                setShowBadge(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_QUIET,
                context.getString(R.string.notification_channel_quiet_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_quiet_description)
                setShowBadge(true)
            }
        )
    }

    /**
     * Posts the reminder for [dose].
     *
     * Before [DoseReminderKey.insistUntil] the notification is ongoing and interrupting, and
     * dismissing it re-posts it. After that it is a quiet, dismissable note.
     *
     * @param insistent Whether the reminder is still inside its insistence window.
     */
    fun post(
        context: Context,
        key: DoseReminderKey,
        dose: DoseChecklistItem,
        now: LocalDateTime,
        insistent: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = build(context, key, dose, now, insistent)
        NotificationManagerCompat.from(context).notify(key.toTag(), NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context, key: DoseReminderKey) {
        NotificationManagerCompat.from(context).cancel(key.toTag(), NOTIFICATION_ID)
    }

    private fun build(
        context: Context,
        key: DoseReminderKey,
        dose: DoseChecklistItem,
        now: LocalDateTime,
        insistent: Boolean
    ): Notification {
        val title = listOf(dose.prescriptionName, dose.dosis)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val builder = NotificationCompat.Builder(
            context,
            if (insistent) CHANNEL_INSISTENT else CHANNEL_QUIET
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(dose.prescriptionColor.toInt())
            .setContentTitle(title)
            .setContentText(describe(context, key, now, insistent, DateTimeFormats.from(context)))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent(context))
            .setDeleteIntent(broadcast(context, key, DoseReminderReceiver.ACTION_DISMISSED))
            .setGroup(GROUP_KEY)
            .setAutoCancel(false)
            .setOngoing(insistent)
            .setOnlyAlertOnce(!insistent)
            .setPriority(
                if (insistent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_taken),
                broadcast(context, key, DoseReminderReceiver.ACTION_TAKEN)
            )

        if (insistent) {
            builder.addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_snooze),
                broadcast(context, key, DoseReminderReceiver.ACTION_SNOOZE)
            )
        }

        return builder.build()
    }

    private fun describe(
        context: Context,
        key: DoseReminderKey,
        now: LocalDateTime,
        insistent: Boolean,
        formats: DateTimeFormats
    ): String {
        val at = formats.time(key.time)
        if (!insistent) {
            return context.getString(R.string.notification_overdue_quiet, at)
        }

        val minutes = Duration.between(now, key.dueAt).toMinutes()
        return when {
            minutes >= 1L -> context.resources.getQuantityString(
                R.plurals.notification_due_in_minutes,
                minutes.toInt(),
                at,
                minutes.toInt()
            )

            minutes == 0L -> context.getString(R.string.notification_due_now, at)
            else -> context.getString(R.string.notification_due_overdue, at)
        }
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * A pending broadcast for [action] on [key].
     */
    private fun broadcast(
        context: Context,
        key: DoseReminderKey,
        action: String
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        DoseReminderReceiver.intent(context, key, action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
