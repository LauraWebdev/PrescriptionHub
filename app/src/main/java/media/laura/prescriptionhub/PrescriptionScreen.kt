package media.laura.prescriptionhub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun PrescriptionScreen(
    modifier: Modifier = Modifier,
    viewModel: PrescriptionListViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PrescriptionScreenContent(
        prescriptions = uiState.prescriptions,
        today = uiState.today,
        isLoading = uiState.isLoading,
        onSavePrescription = viewModel::savePrescription,
        onDeletePrescription = viewModel::deletePrescription,
        modifier = modifier
    )
}

/**
 * Stateless prescription tab, so it can be previewed without a database.
 *
 * @param prescriptions The prescriptions to list.
 * @param today The current date.
 * @param isLoading Whether the prescriptions are still being loaded.
 * @param onSavePrescription Invoked with a new or edited prescription that should be persisted.
 * @param onDeletePrescription Invoked once the user confirmed deleting a prescription.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionScreenContent(
    prescriptions: List<Prescription>,
    today: LocalDate,
    isLoading: Boolean = false,
    onSavePrescription: (Prescription, Boolean) -> Unit = { _, _ -> },
    onDeletePrescription: (Prescription) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showPrescriptionForm by rememberSaveable { mutableStateOf(false) }
    var editingPrescriptionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDeletion by remember { mutableStateOf<Prescription?>(null) }

    val editingPrescription = editingPrescriptionId?.let { id ->
        prescriptions.firstOrNull { it.id == id }
    }

    fun openAddForm() {
        editingPrescriptionId = null
        showPrescriptionForm = true
    }

    fun closeForm() {
        showPrescriptionForm = false
        editingPrescriptionId = null
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.prescription_title)) },
                actions = {
                    IconButton(onClick = { openAddForm() }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.prescription_add)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )

                prescriptions.isEmpty() -> PrescriptionEmptyState(
                    onAddPrescription = { openAddForm() }
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(items = prescriptions, key = { it.id }) { prescription ->
                        PrescriptionRow(
                            prescription = prescription,
                            onEdit = {
                                editingPrescriptionId = it.id
                                showPrescriptionForm = true
                            },
                            onDeleteRequest = { pendingDeletion = it }
                        )
                    }
                }
            }
        }
    }

    if (showPrescriptionForm) {
        ModalBottomSheet(onDismissRequest = { closeForm() }) {
            PrescriptionForm(
                today = today,
                modifier = Modifier.padding(bottom = 24.dp),
                initialPrescription = editingPrescription,
                onSave = { prescription, backfillPastDoses ->
                    onSavePrescription(prescription, backfillPastDoses)
                    closeForm()
                }
            )
        }
    }

    pendingDeletion?.let { prescription ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = {
                Text(text = stringResource(R.string.prescription_delete_title, prescription.name))
            },
            text = {
                Text(
                    text = stringResource(R.string.prescription_delete_message)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePrescription(prescription)
                        pendingDeletion = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

private val previewToday = LocalDate.of(2026, 8, 19)

@Preview(showBackground = true)
@Composable
private fun PrescriptionScreenEmptyPreview() {
    PrescriptionHubTheme {
        PrescriptionScreenContent(prescriptions = emptyList(), today = previewToday)
    }
}

@Preview(showBackground = true)
@Composable
private fun PrescriptionScreenPreview() {
    PrescriptionHubTheme {
        PrescriptionScreenContent(
            prescriptions = listOf(
                Prescription(
                    id = 1,
                    name = "Metformin",
                    color = prescriptionColors[6].toStoredLong(),
                    dosis = "850mg",
                    schedule = Schedule(
                        scheduleType = ScheduleType.DAILY,
                        timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(23, 0)),
                        startDate = previewToday
                    )
                ),
                Prescription(
                    id = 2,
                    name = "Candecor",
                    color = prescriptionColors[0].toStoredLong(),
                    dosis = "1 pill",
                    schedule = Schedule(
                        scheduleType = ScheduleType.EVERY_X_DAYS,
                        everyXDays = 3,
                        timesOfDay = listOf(LocalTime.of(8, 0)),
                        startDate = previewToday
                    )
                ),
                Prescription(
                    id = 3,
                    name = "Gynokadin Estradiol",
                    color = prescriptionColors[8].toStoredLong(),
                    dosis = "1 pump on one arm",
                    schedule = Schedule(
                        scheduleType = ScheduleType.SPECIFIC_DAYS_OF_WEEK,
                        daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                        timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(23, 0)),
                        startDate = previewToday
                    )
                )
            ),
            today = previewToday
        )
    }
}
