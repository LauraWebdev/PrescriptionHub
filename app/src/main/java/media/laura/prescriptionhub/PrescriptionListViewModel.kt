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
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * State of the prescription list.
 *
 * @param prescriptions The prescriptions currently stored.
 * @param today The current date, used to seed a new prescription's start date.
 * @param isLoading Whether the first database result is still pending.
 */
data class PrescriptionListUiState(
    val prescriptions: List<Prescription> = emptyList(),
    val today: LocalDate,
    val isLoading: Boolean = true
)

/**
 * Holds the prescription list and persists edits through [PrescriptionService].
 *
 * @param nowProvider Reads the current moment.
 */
class PrescriptionListViewModel(
    private val prescriptionService: PrescriptionService,
    private val nowProvider: () -> LocalDateTime
) : ViewModel() {

    val uiState: StateFlow<PrescriptionListUiState> = prescriptionService.getAllPrescriptions()
        .map { prescriptions ->
            PrescriptionListUiState(
                prescriptions = prescriptions,
                today = nowProvider().toLocalDate(),
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = PrescriptionListUiState(today = nowProvider().toLocalDate())
        )

    /**
     * Inserts [prescription] when it is new, otherwise updates the existing row.
     *
     * @param backfillPastDoses Whether to record the doses already due as taken on the way.
     */
    fun savePrescription(prescription: Prescription, backfillPastDoses: Boolean = false) {
        viewModelScope.launch {
            val id = if (prescription.id == NEW_PRESCRIPTION_ID) {
                prescriptionService.addPrescription(prescription)
            } else {
                prescriptionService.updatePrescription(prescription)
                prescription.id
            }
            if (backfillPastDoses) {
                prescriptionService.backfillTakenDoses(id, nowProvider())
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
