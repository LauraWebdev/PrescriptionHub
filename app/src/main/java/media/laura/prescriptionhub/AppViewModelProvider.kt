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
            PrescriptionListViewModel(prescriptionApplication().prescriptionService)
        }
        initializer {
            TodayViewModel(prescriptionApplication().prescriptionService)
        }
        initializer {
            CalendarViewModel(prescriptionApplication().prescriptionService)
        }
    }
}

fun CreationExtras.prescriptionApplication(): PrescriptionHubApplication =
    this[APPLICATION_KEY] as PrescriptionHubApplication
