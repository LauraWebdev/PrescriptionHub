package media.laura.prescriptionhub

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.repository.PrescriptionRepository
import media.laura.prescriptionhub.data.repository.PrescriptionService
import media.laura.prescriptionhub.reminder.DoseNotifications
import media.laura.prescriptionhub.reminder.DoseReminderScheduler
import media.laura.prescriptionhub.reminder.ReminderSchedulingPrescriptionService
import java.time.LocalDateTime

class PrescriptionHubApplication : Application() {

    val database: PrescriptionDatabase by lazy {
        PrescriptionDatabase.getDatabase(this)
    }

    val nowProvider: () -> LocalDateTime = { LocalDateTime.now() }

    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    private val repository: PrescriptionService by lazy {
        PrescriptionRepository(database.prescriptionDao())
    }

    val reminderScheduler: DoseReminderScheduler by lazy {
        DoseReminderScheduler(
            context = this,
            serviceProvider = { repository },
            nowProvider = nowProvider
        )
    }

    val prescriptionService: PrescriptionService by lazy {
        ReminderSchedulingPrescriptionService(repository, reminderScheduler)
    }

    override fun onCreate() {
        super.onCreate()
        DoseNotifications.createChannels(this)
        applicationScope.launch {
            reminderScheduler.rearmWindow()
        }
    }
}
