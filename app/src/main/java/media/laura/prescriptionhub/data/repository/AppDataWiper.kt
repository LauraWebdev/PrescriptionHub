package media.laura.prescriptionhub.data.repository

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.reminder.DoseReminderScheduler

/**
 * Erases everything the app has stored about the user.
 */
class AppDataWiper(
    private val context: Context,
    private val database: PrescriptionDatabase,
    private val reminderScheduler: DoseReminderScheduler,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /**
     * Cancels every reminder and notification, then empties every table.
     *
     * The reminders go first because the keys to cancel are derived from the doses on record.
     */
    suspend fun wipe() = withContext(workDispatcher) {
        NotificationManagerCompat.from(context).cancelAll()
        reminderScheduler.cancelAll()
        database.clearAllTables()
    }
}
