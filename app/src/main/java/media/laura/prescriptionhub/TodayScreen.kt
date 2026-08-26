package media.laura.prescriptionhub

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.LocalDate
import java.time.LocalTime

/**
 * The today tab.
 *
 * @param onOpenSettings Invoked when the user opens the settings screen.
 */
@Composable
fun TodayScreen(
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var warningHidden by remember { mutableStateOf(false) }
    var canScheduleExact by remember { mutableStateOf(true) }

    // Update date on resume if the date significantly changed.
    // Also check alarms permission after coming back from settings.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshDate()
        canScheduleExact = canScheduleExactAlarms(context)
        onPauseOrDispose {}
    }

    TodayScreenContent(
        groups = uiState.groups,
        isLoading = uiState.isLoading,
        onDoseTakenChange = viewModel::setDoseTaken,
        onCheckAll = viewModel::checkAll,
        showExactAlarmWarning = !canScheduleExact && !warningHidden,
        onGrantExactAlarm = { openExactAlarmSettings(context) },
        onDismissExactAlarmWarning = { warningHidden = true },
        onOpenSettings = onOpenSettings,
        modifier = modifier
    )
}

/**
 * Today tab, stateless so it can be previewed without a database.
 *
 * @param groups The day's doses, one group per time of day.
 * @param isLoading Whether the first database result is still pending.
 * @param onDoseTakenChange Invoked with a dose and its new taken state.
 * @param onCheckAll Invoked with the group whose open doses should all be marked as taken.
 * @param showExactAlarmWarning Whether to warn that reminders cannot be delivered punctually.
 * @param onGrantExactAlarm Invoked when the user wants to grant the exact alarm permission.
 * @param onDismissExactAlarmWarning Invoked when the user hides that warning.
 * @param onOpenSettings Invoked when the user opens the settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreenContent(
    groups: List<DoseTimeGroup>,
    isLoading: Boolean = false,
    onDoseTakenChange: (DoseChecklistItem, Boolean) -> Unit = { _, _ -> },
    onCheckAll: (DoseTimeGroup) -> Unit = {},
    showExactAlarmWarning: Boolean = false,
    onGrantExactAlarm: () -> Unit = {},
    onDismissExactAlarmWarning: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allTaken = groups.isNotEmpty() && groups.all { it.isComplete }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.today_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.today_settings)
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
            AnimatedVisibility(visible = showExactAlarmWarning) {
                ExactAlarmBanner(
                    onGrant = onGrantExactAlarm,
                    onDismiss = onDismissExactAlarmWarning
                )
            }

            AnimatedVisibility(visible = allTaken) {
                TodayDoneBanner()
            }

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
                        headline = stringResource(R.string.today_empty_headline),
                        message = stringResource(R.string.today_empty_message)
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        groups.forEach { group ->
                            item(key = "header-${group.time}") {
                                DoseTimeGroupHeader(
                                    group = group,
                                    onCheckAll = { onCheckAll(group) }
                                )
                            }

                            items(
                                items = group.doses,
                                key = { "${it.snapshotId}@${it.scheduledTime}" }
                            ) { dose ->
                                DoseRow(
                                    dose = dose,
                                    onTakenChange = { taken -> onDoseTakenChange(dose, taken) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun previewDose(
    id: Long,
    name: String,
    dosis: String,
    colorIndex: Int,
    time: LocalTime,
    taken: Boolean
) = DoseChecklistItem(
    snapshotId = id,
    prescriptionId = id,
    prescriptionName = name,
    prescriptionColor = prescriptionColors[colorIndex].toStoredLong(),
    dosis = dosis,
    scheduledDate = LocalDate.of(2026, 8, 19),
    scheduledTime = time,
    taken = taken,
    takenAt = null
)

private fun previewGroups(lateGroupTaken: Boolean): List<DoseTimeGroup> {
    val morning = LocalTime.of(8, 0)
    val night = LocalTime.of(23, 0)
    return groupDosesByTime(
        listOf(
            previewDose(1, "Metformin", "850mg", 6, morning, taken = true),
            previewDose(2, "Candecor", "1 pill", 0, morning, taken = true),
            previewDose(3, "Gynokadin Estradiol", "1 pump on one arm", 8, morning, taken = true),
            previewDose(4, "Metformin", "850mg", 6, night, taken = lateGroupTaken),
            previewDose(5, "Gynokadin Estradiol", "1 pump on each arm", 8, night, taken = true),
            previewDose(6, "Progesteron", "1 pill", 2, night, taken = lateGroupTaken)
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenPreview() {
    PrescriptionHubTheme {
        TodayScreenContent(groups = previewGroups(lateGroupTaken = false))
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenAllTakenPreview() {
    PrescriptionHubTheme {
        TodayScreenContent(groups = previewGroups(lateGroupTaken = true))
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenEmptyPreview() {
    PrescriptionHubTheme {
        TodayScreenContent(groups = emptyList())
    }
}

@Preview(showBackground = true)
@Composable
private fun TodayScreenExactAlarmWarningPreview() {
    PrescriptionHubTheme {
        TodayScreenContent(
            groups = previewGroups(lateGroupTaken = false),
            showExactAlarmWarning = true
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TodayScreenDarkPreview() {
    PrescriptionHubTheme {
        TodayScreenContent(groups = previewGroups(lateGroupTaken = false))
    }
}
