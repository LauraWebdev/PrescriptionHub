package media.laura.prescriptionhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import media.laura.prescriptionhub.data.model.DoseDayProgress
import media.laura.prescriptionhub.data.repository.PrescriptionService
import java.time.LocalDate

/**
 * State of the calendar.
 *
 * @param selectedDate The day whose checklist is shown.
 * @param today The current date, used to mark the selected day and to judge missed doses.
 * @param groups The selected day's doses, one group per time of day.
 * @param progressByDate Completion per day for the day ring row. Days without a scheduled dose are
 *   absent, as are days outside the loaded window.
 * @param isLoading Whether the first database result is still pending.
 */
data class CalendarUiState(
    val selectedDate: LocalDate,
    val today: LocalDate,
    val groups: List<DoseTimeGroup> = emptyList(),
    val progressByDate: Map<LocalDate, DoseDayProgress> = emptyMap(),
    val isLoading: Boolean = true
)

/**
 * Holds the calendar's selected day.
 *
 * @param todayProvider Reads the current date. Injected so tests can pin the day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val prescriptionService: PrescriptionService,
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) : ViewModel() {

    private val selectedDate = MutableStateFlow(todayProvider())

    val uiState: StateFlow<CalendarUiState> = selectedDate
        .flatMapLatest { date ->
            val today = todayProvider()
            combine(
                prescriptionService.getDoseChecklistForDate(date),
                prescriptionService.getDoseProgressForDates(
                    startDate = date.minusDays(RING_WINDOW_DAYS),
                    endDate = date.plusDays(RING_WINDOW_DAYS)
                )
            ) { doses, progress ->
                CalendarUiState(
                    selectedDate = date,
                    today = today,
                    groups = groupDosesByTime(doses),
                    progressByDate = progress.associateBy { it.date },
                    isLoading = false
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CalendarUiState(
                selectedDate = selectedDate.value,
                today = selectedDate.value
            )
        )

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun showPreviousDay() {
        selectedDate.value = selectedDate.value.minusDays(1)
    }

    fun showNextDay() {
        selectedDate.value = selectedDate.value.plusDays(1)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * How many days on either side of the selected day are summarized for the ring row. Wide
         * enough that the rings around the selected day are always filled in while scrolling.
         */
        const val RING_WINDOW_DAYS = 45L
    }
}
