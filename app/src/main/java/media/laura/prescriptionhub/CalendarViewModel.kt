package media.laura.prescriptionhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import media.laura.prescriptionhub.data.model.DoseDayProgress
import media.laura.prescriptionhub.data.repository.PrescriptionService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * State of the calendar.
 *
 * @param selectedDate The day whose checklist is shown.
 * @param now The current moment.
 * @param groups The doses of [loadedDate], one group per time of day.
 * @param loadedDate The day [groups] belongs to, or `null` before the first result arrives.
 * @param progressByDate Completion per day for the day ring row.
 */
data class CalendarUiState(
    val selectedDate: LocalDate,
    val now: LocalDateTime,
    val groups: List<DoseTimeGroup> = emptyList(),
    val loadedDate: LocalDate? = null,
    val progressByDate: Map<LocalDate, DoseDayProgress> = emptyMap()
) {
    val today: LocalDate get() = now.toLocalDate()
    val isLoading: Boolean get() = loadedDate != selectedDate
}

/**
 * Holds the calendar's selected day.
 *
 * @param nowProvider Reads the current moment.
 * @param listDebounceMillis How long the dose list waits for the selection to settle.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class CalendarViewModel(
    private val prescriptionService: PrescriptionService,
    private val nowProvider: () -> LocalDateTime,
    private val listDebounceMillis: Long = LIST_DEBOUNCE_MILLIS
) : ViewModel() {

    private val clock = MutableStateFlow(nowProvider())

    private val selectedDate = MutableStateFlow(clock.value.toLocalDate())

    private val loadedDoses: Flow<LoadedDoses> = selectedDate
        .let { dates -> if (listDebounceMillis > 0) dates.debounce(listDebounceMillis) else dates }
        .flatMapLatest { date ->
            prescriptionService.getDoseChecklistForDate(date).map { doses ->
                LoadedDoses(date = date, groups = groupDosesByTime(doses))
            }
        }

    /**
     * The day the ring window is centred on.
     */
    private val ringAnchor: Flow<LocalDate> = selectedDate
        .scan(selectedDate.value) { anchor, date ->
            if (abs(ChronoUnit.DAYS.between(anchor, date)) > RING_REANCHOR_DAYS) date else anchor
        }
        .distinctUntilChanged()

    private val ringProgress: Flow<Map<LocalDate, DoseDayProgress>> = ringAnchor
        .flatMapLatest { anchor ->
            prescriptionService.getDoseProgressForDates(
                startDate = anchor.minusDays(RING_WINDOW_DAYS),
                endDate = anchor.plusDays(RING_WINDOW_DAYS)
            ).map { progress -> progress.associateBy { it.date } }
        }

    val uiState: StateFlow<CalendarUiState> = combine(
        selectedDate,
        loadedDoses,
        ringProgress,
        clock
    ) { date, loaded, progress, moment ->
        CalendarUiState(
            selectedDate = date,
            now = moment,
            groups = loaded.groups,
            loadedDate = loaded.date,
            progressByDate = progress
        )
    }.stateIn(
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

    private data class LoadedDoses(
        val date: LocalDate,
        val groups: List<DoseTimeGroup>
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        const val LIST_DEBOUNCE_MILLIS = 120L
        private const val RING_WINDOW_DAYS = 45L

        /**
         * How far the selection may drift from the anchor before the window is reloaded.
         */
        private const val RING_REANCHOR_DAYS = 20L
    }
}
