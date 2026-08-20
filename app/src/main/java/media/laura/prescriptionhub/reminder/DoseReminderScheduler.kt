package media.laura.prescriptionhub.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import media.laura.prescriptionhub.data.repository.PrescriptionService
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Arms and cancels the [AlarmManager] alarms behind dose reminders.
 *
 * @param context Application context.
 * @param serviceProvider Supplies the data service.
 * @param zoneId The zone used to turn wall-clock dose times into alarm instants.
 * @param nowProvider Supplies the current moment; overridable in tests.
 */
class DoseReminderScheduler(
    private val context: Context,
    private val serviceProvider: () -> PrescriptionService,
    private val nowProvider: () -> LocalDateTime,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    private var armedKeys: Set<DoseReminderKey> = emptySet()

    suspend fun rearmWindow() {
        val now = nowProvider()
        val windowEnd = now.plusHours(WINDOW_HOURS)

        val doses = runCatching {
            serviceProvider().getDoseChecklistBetween(now, windowEnd.plusHours(MAX_LEAD_HOURS))
        }.getOrElse { error ->
            Log.w(TAG, "Could not load doses for reminder scheduling", error)
            return
        }

        val planned = planReminders(doses = doses, now = now, windowEnd = windowEnd)
        val plannedKeys = planned.map { it.key }.toSet()

        (armedKeys - plannedKeys).forEach(::cancel)
        planned.forEach(::arm)
        armedKeys = plannedKeys

        armHorizonRefresh(now)
    }

    fun arm(planned: PlannedReminder) {
        schedule(
            triggerAt = planned.triggerAt,
            pendingIntent = pendingIntent(planned.key, DoseReminderReceiver.ACTION_FIRE)
        )
    }

    /** Cancels any pending reminder for [key], including the follow-up. */
    fun cancel(key: DoseReminderKey) {
        val actions = listOf(DoseReminderReceiver.ACTION_FIRE, DoseReminderReceiver.ACTION_RELAX)
        actions.forEach { action ->
            existingPendingIntent(key, action)?.let { pending ->
                alarmManager?.cancel(pending)
                pending.cancel()
            }
        }
    }

    fun armSnooze(key: DoseReminderKey, minutes: Long = SNOOZE_MINUTES) {
        schedule(
            triggerAt = nowProvider().plusMinutes(minutes),
            pendingIntent = pendingIntent(key, DoseReminderReceiver.ACTION_FIRE)
        )
    }

    fun armRelax(key: DoseReminderKey): Boolean {
        if (key.insistUntil <= nowProvider()) {
            return false
        }
        schedule(
            triggerAt = key.insistUntil,
            pendingIntent = pendingIntent(key, DoseReminderReceiver.ACTION_RELAX)
        )
        return true
    }

    private fun armHorizonRefresh(now: LocalDateTime) {
        val nextRefresh = now.toLocalDate()
            .plusDays(1)
            .atTime(LocalTime.of(HORIZON_REFRESH_HOUR, 0))
        schedule(
            triggerAt = nextRefresh,
            pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                DoseReminderReceiver.horizonRefreshIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    /**
     * Schedules [pendingIntent] for [triggerAt].
     */
    private fun schedule(triggerAt: LocalDateTime, pendingIntent: PendingIntent) {
        val manager = alarmManager ?: return
        val triggerAtMillis = triggerAt.atZone(zoneId).toInstant().toEpochMilli()

        try {
            if (canScheduleExact(manager)) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                manager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    INEXACT_WINDOW_MILLIS,
                    pendingIntent
                )
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Falling back to an inexact alarm", error)
            manager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                INEXACT_WINDOW_MILLIS,
                pendingIntent
            )
        }
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun pendingIntent(key: DoseReminderKey, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            DoseReminderReceiver.intent(context, key, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun existingPendingIntent(key: DoseReminderKey, action: String): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            0,
            DoseReminderReceiver.intent(context, key, action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

    private companion object {
        const val TAG = "DoseReminderScheduler"

        const val WINDOW_HOURS = 48L

        const val MAX_LEAD_HOURS = 1L

        const val SNOOZE_MINUTES = 5L
        const val HORIZON_REFRESH_HOUR = 3
        const val INEXACT_WINDOW_MILLIS = 5L * 60L * 1000L
    }
}
