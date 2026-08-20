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
import java.time.LocalDateTime

/**
 * State of the calendar.
 *
 * @param selectedDate The day whose checklist is shown.
 * @param now The current moment.
 * @param groups The selected day's doses, one group per time of day.
 * @param progressByDate Completion per day for the day ring row. Days without a scheduled dose are
 *   absent, as are days outside the loaded window.
 * @param isLoading Whether the first database result is still pending.
 */
data class CalendarUiState(
    val selectedDate: LocalDate,
    val now: LocalDateTime,
    val groups: List<DoseTimeGroup> = emptyList(),
    val progressByDate: Map<LocalDate, DoseDayProgress> = emptyMap(),
    val isLoading: Boolean = true
) {
    val today: LocalDate get() = now.toLocalDate()
}

/**
 * Holds the calendar's selected day.
 *
 * @param nowProvider Reads the current moment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val prescriptionService: PrescriptionService,
    private val nowProvider: () -> LocalDateTime
) : ViewModel() {

    private val clock = MutableStateFlow(nowProvider())

    private val selectedDate = MutableStateFlow(clock.value.toLocalDate())

    val uiState: StateFlow<CalendarUiState> = selectedDate
        .flatMapLatest { date ->
            combine(
                prescriptionService.getDoseChecklistForDate(date),
                prescriptionService.getDoseProgressForDates(
                    startDate = date.minusDays(RING_WINDOW_DAYS),
                    endDate = date.plusDays(RING_WINDOW_DAYS)
                )
            ) { doses, progress ->
                CalendarUiState(
                    selectedDate = date,
                    now = clock.value,
                    groups = groupDosesByTime(doses),
                    progressByDate = progress.associateBy { it.date },
                    isLoading = false
                )
            }
        }
        .combine(clock) { state, moment -> state.copy(now = moment) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = CalendarUiState(
                selectedDate = selectedDate.value,
                now = clock.value
            )
        )

    fun refresh() {
        clock.value = nowProvider()
    }

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
