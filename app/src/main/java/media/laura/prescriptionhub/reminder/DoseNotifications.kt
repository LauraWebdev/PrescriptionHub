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
import media.laura.prescriptionhub.formatTimeOfDay
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
                "Dose reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminds you shortly before a dose is due"
                setShowBadge(true)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_QUIET,
                "Missed doses",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quietly reminds you about a dose that is still not taken"
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
            .setContentText(describe(key, now, insistent))
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
                "Taken",
                broadcast(context, key, DoseReminderReceiver.ACTION_TAKEN)
            )

        if (insistent) {
            builder.addAction(
                R.drawable.ic_notification,
                "Snooze 5 min",
                broadcast(context, key, DoseReminderReceiver.ACTION_SNOOZE)
            )
        }

        return builder.build()
    }

    private fun describe(key: DoseReminderKey, now: LocalDateTime, insistent: Boolean): String {
        val at = formatTimeOfDay(key.time)
        if (!insistent) {
            return "Was due at $at · not taken yet"
        }

        val minutes = Duration.between(now, key.dueAt).toMinutes()
        return when {
            minutes > 1L -> "Due at $at · in $minutes minutes"
            minutes == 1L -> "Due at $at · in 1 minute"
            minutes == 0L -> "Due at $at · now"
            else -> "Due at $at · overdue"
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
