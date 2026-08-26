package media.laura.prescriptionhub

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.data.model.DoseDayProgress
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/**
 * A day the calendar is showing, with its year left off while it matches [today].
 *
 * @param formats How the device writes dates.
 */
fun formatCalendarDate(
    date: LocalDate,
    today: LocalDate,
    formats: DateTimeFormats
): String = if (date.year == today.year) {
    formats.dayAndMonth(date)
} else {
    formats.dayMonthAndYear(date)
}

/** The calendar's app bar title format. */
fun formatCalendarTitle(
    date: LocalDate,
    today: LocalDate,
    formats: DateTimeFormats
): String {
    val formatted = formatCalendarDate(date = date, today = today, formats = formats)
    return if (date == today) "Today, $formatted" else formatted
}

/**
 * The calendar tab
 */
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Re-read the clock on resume, so the day and the missed/upcoming split do not go stale.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    CalendarScreenContent(
        selectedDate = uiState.selectedDate,
        now = uiState.now,
        groups = uiState.groups,
        progressByDate = uiState.progressByDate,
        isLoading = uiState.isLoading,
        onDateSelected = viewModel::selectDate,
        onPreviousDay = viewModel::showPreviousDay,
        onNextDay = viewModel::showNextDay,
        modifier = modifier
    )
}

/**
 * Calendar summary
 *
 * @param selectedDate The day being shown.
 * @param now The current moment.
 * @param groups The selected day's doses, one group per time of day.
 * @param progressByDate Completion per day for the ring row.
 * @param isLoading Whether the first database result is still pending.
 * @param onDateSelected Invoked with a day picked from the ring row.
 * @param onPreviousDay Invoked by the app bar's back arrow.
 * @param onNextDay Invoked by the app bar's forward arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreenContent(
    selectedDate: LocalDate,
    now: LocalDateTime,
    groups: List<DoseTimeGroup>,
    progressByDate: Map<LocalDate, DoseDayProgress> = emptyMap(),
    isLoading: Boolean = false,
    onDateSelected: (LocalDate) -> Unit = {},
    onPreviousDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val today = now.toLocalDate()
    var showDayPicker by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = formatCalendarTitle(
                            date = selectedDate,
                            today = today,
                            formats = LocalDateTimeFormats.current
                        ),
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = "Jump to a day") { showDayPicker = true }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onPreviousDay) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Day"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNextDay) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Day"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            DayProgressRow(
                selectedDate = selectedDate,
                today = today,
                progressByDate = progressByDate,
                onDateSelected = onDateSelected
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    isLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )

                    groups.isEmpty() -> DoseEmptyState(
                        headline = "Nothing scheduled",
                        message = "No dose was due on this day."
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        groups.forEach { group ->
                            item(key = "header-${group.time}") {
                                DoseTimeHeader(time = group.time)
                            }

                            items(
                                items = group.doses,
                                key = { "${it.snapshotId}@${it.scheduledTime}" }
                            ) { dose ->
                                DoseHistoryRow(dose = dose, now = now)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDayPicker) {
        DayPickerDialog(
            initialDate = selectedDate,
            onConfirm = { picked ->
                onDateSelected(picked)
                showDayPicker = false
            },
            onDismiss = { showDayPicker = false }
        )
    }
}

private fun previewDose(
    id: Long,
    name: String,
    dosis: String,
    colorIndex: Int,
    date: LocalDate,
    time: LocalTime,
    taken: Boolean,
    takenAt: LocalDateTime? = null
) = DoseChecklistItem(
    snapshotId = id,
    prescriptionId = id,
    prescriptionName = name,
    prescriptionColor = prescriptionColors[colorIndex].toStoredLong(),
    dosis = dosis,
    scheduledDate = date,
    scheduledTime = time,
    taken = taken,
    takenAt = takenAt
)

private val previewToday = LocalDate.of(2026, 8, 19)
private val previewDay = previewToday.minusDays(1)

private fun previewGroups(): List<DoseTimeGroup> {
    val morning = LocalTime.of(8, 0)
    val night = LocalTime.of(23, 0)
    return groupDosesByTime(
        listOf(
            previewDose(1, "Metformin", "850mg", 6, previewDay, morning, true, previewDay.atTime(8, 3)),
            previewDose(2, "Candecor", "1 pill", 0, previewDay, morning, true, previewDay.atTime(8, 3)),
            previewDose(3, "Gynokadin Estradiol", "1 pump on one arm", 8, previewDay, morning, true, previewDay.atTime(8, 3)),
            previewDose(4, "Metformin", "850mg", 6, previewDay, night, true, previewDay.atTime(23, 5)),
            previewDose(5, "Gynokadin Estradiol", "1 pump on each arm", 8, previewDay, night, false),
            previewDose(6, "Progesteron", "1 pill", 2, previewDay, night, true, previewDay.atTime(23, 5))
        )
    )
}

private fun previewProgress(): Map<LocalDate, DoseDayProgress> = listOf(
    DoseDayProgress(previewToday.minusDays(2), doseCount = 4, takenCount = 3),
    DoseDayProgress(previewToday.minusDays(1), doseCount = 6, takenCount = 5),
    DoseDayProgress(previewToday, doseCount = 6, takenCount = 2),
    DoseDayProgress(previewToday.plusDays(1), doseCount = 6, takenCount = 0),
    DoseDayProgress(previewToday.plusDays(2), doseCount = 6, takenCount = 0)
).associateBy { it.date }

@Preview(showBackground = true)
@Composable
private fun CalendarScreenPreview() {
    PrescriptionHubTheme {
        CalendarScreenContent(
            selectedDate = previewDay,
            groups = previewGroups(),
            progressByDate = previewProgress(),
            now = previewToday.atTime(18, 0)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CalendarScreenEmptyPreview() {
    PrescriptionHubTheme {
        CalendarScreenContent(
            selectedDate = previewToday.plusDays(3),
            groups = emptyList(),
            progressByDate = previewProgress(),
            now = previewToday.atTime(18, 0)
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CalendarScreenDarkPreview() {
    PrescriptionHubTheme {
        CalendarScreenContent(
            selectedDate = previewDay,
            groups = previewGroups(),
            progressByDate = previewProgress(),
            now = previewToday.atTime(18, 0)
        )
    }
}
