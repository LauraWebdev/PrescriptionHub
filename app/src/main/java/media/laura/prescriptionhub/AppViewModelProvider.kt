package media.laura.prescriptionhub

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * Single factory for every view model in the app.
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val application = prescriptionApplication()
            PrescriptionListViewModel(application.prescriptionService, application.nowProvider)
        }
        initializer {
            val application = prescriptionApplication()
            TodayViewModel(application.prescriptionService, application.nowProvider)
        }
        initializer {
            val application = prescriptionApplication()
            CalendarViewModel(application.prescriptionService, application.nowProvider)
        }
        initializer {
            val application = prescriptionApplication()
            SettingsViewModel(
                application.appDataWiper,
                application.appDataBackup,
                application.resources
            )
        }
    }
}

fun CreationExtras.prescriptionApplication(): PrescriptionHubApplication =
    this[APPLICATION_KEY] as PrescriptionHubApplication
