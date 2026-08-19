package media.laura.prescriptionhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.repository.PrescriptionService

/**
 * State of the prescription list.
 *
 * @param prescriptions The prescriptions currently stored.
 * @param isLoading Whether the first database result is still pending.
 */
data class PrescriptionListUiState(
    val prescriptions: List<Prescription> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Holds the prescription list and persists edits through [PrescriptionService].
 */
class PrescriptionListViewModel(
    private val prescriptionService: PrescriptionService
) : ViewModel() {

    val uiState: StateFlow<PrescriptionListUiState> = prescriptionService.getAllPrescriptions()
        .map { prescriptions ->
            PrescriptionListUiState(prescriptions = prescriptions, isLoading = false)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PrescriptionListUiState()
        )

    /**
     * Inserts [prescription] when it is new, otherwise updates the existing row.
     */
    fun savePrescription(prescription: Prescription) {
        viewModelScope.launch {
            if (prescription.id == NEW_PRESCRIPTION_ID) {
                prescriptionService.addPrescription(prescription)
            } else {
                prescriptionService.updatePrescription(prescription)
            }
        }
    }

    fun deletePrescription(prescription: Prescription) {
        viewModelScope.launch {
            prescriptionService.deletePrescription(prescription)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val NEW_PRESCRIPTION_ID = 0L
    }
}
