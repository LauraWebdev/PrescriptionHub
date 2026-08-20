package media.laura.prescriptionhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.data.repository.PrescriptionService
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * State of the today checklist.
 *
 * @param date The day the checklist belongs to.
 * @param groups The day's doses, one group per time of day.
 * @param isLoading Whether the first database result is still pending.
 */
data class TodayUiState(
    val date: LocalDate,
    val groups: List<DoseTimeGroup> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Holds today's dose checklist.
 *
 * @param nowProvider Reads the current moment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val prescriptionService: PrescriptionService,
    private val nowProvider: () -> LocalDateTime
) : ViewModel() {

    private val date = MutableStateFlow(nowProvider().toLocalDate())

    val uiState: StateFlow<TodayUiState> = date
        .flatMapLatest { day ->
            prescriptionService.getDoseChecklistForDate(day).map { doses ->
                TodayUiState(
                    date = day,
                    groups = groupDosesByTime(doses),
                    isLoading = false
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = TodayUiState(date = date.value)
        )

    fun refreshDate() {
        date.value = nowProvider().toLocalDate()
    }

    /**
     * Records [dose] as taken or open.
     */
    fun setDoseTaken(dose: DoseChecklistItem, taken: Boolean) {
        viewModelScope.launch {
            prescriptionService.setDoseTaken(
                snapshotId = dose.snapshotId,
                scheduledDate = dose.scheduledDate,
                scheduledTime = dose.scheduledTime,
                taken = taken
            )
        }
    }

    /**
     * Records every open dose of [group] as taken. Doses that are already taken are skipped.
     */
    fun checkAll(group: DoseTimeGroup) {
        viewModelScope.launch {
            group.doses.filterNot { it.taken }.forEach { dose ->
                prescriptionService.setDoseTaken(
                    snapshotId = dose.snapshotId,
                    scheduledDate = dose.scheduledDate,
                    scheduledTime = dose.scheduledTime,
                    taken = true
                )
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
