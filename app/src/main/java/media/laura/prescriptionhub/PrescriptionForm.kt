package media.laura.prescriptionhub

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

fun scheduleTypeLabel(type: ScheduleType): String = when (type) {
    ScheduleType.DAILY -> "Daily"
    ScheduleType.SPECIFIC_DAYS_OF_WEEK -> "Specific Days of Week"
    ScheduleType.EVERY_X_DAYS -> "Every X Days"
}

/** Expands a Monday-first day bitmask into the days it represents. */
fun Int.toDaysOfWeek(): List<DayOfWeek> =
    DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }

/** Collapses a set of days into a Monday-first bitmask. */
fun List<DayOfWeek>.toDaysMask(): Int =
    fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

/** Flips the bit for [day] in a day bitmask. */
fun Int.toggleDay(day: DayOfWeek): Int = this xor (1 shl (day.value - 1))

/** Whether [day] is set in a day bitmask. */
fun Int.hasDay(day: DayOfWeek): Boolean = this and (1 shl (day.value - 1)) != 0

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun formatTimeOfDay(time: LocalTime): String = time.format(timeFormatter)

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatStartDate(date: LocalDate): String = date.format(dateFormatter)

/** The reminder lead times offered in the form. `null` means no reminder. */
val reminderLeadOptions: List<Int?> = listOf(null, 5, 10, 30, 60)

/** Labels a reminder lead time of [minutes], where `null` means no reminder. */
fun formatReminderLead(minutes: Int?): String = when (minutes) {
    null -> "Never"
    60 -> "1 hour before"
    else -> "$minutes minutes before"
}

/** Saver that persists a date as an ISO string. */
private val localDateSaver = Saver<LocalDate, String>(
    save = { it.toString() },
    restore = LocalDate::parse
)

/** Saver that persists times of day as ISO strings. */
private val timesSaver = listSaver<SnapshotStateList<LocalTime>, String>(
    save = { times -> times.map(LocalTime::toString) },
    restore = { stored -> stored.map(LocalTime::parse).toMutableStateList() }
)

/**
 * Form for creating a prescription.
 *
 * @param today The current date.
 * @param initialPrescription Optional values to prefill the form with.
 * @param onSave Invoked with the assembled prescription when Save is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrescriptionForm(
    today: LocalDate,
    modifier: Modifier = Modifier,
    initialPrescription: Prescription? = null,
    onSave: (Prescription) -> Unit = {}
) {
    var name by rememberSaveable(initialPrescription) {
        mutableStateOf(initialPrescription?.name.orEmpty())
    }
    var dosis by rememberSaveable(initialPrescription) {
        mutableStateOf(initialPrescription?.dosis.orEmpty())
    }
    var colorArgb by rememberSaveable(initialPrescription) {
        mutableStateOf(initialPrescription?.color ?: prescriptionColors.first().toStoredLong())
    }
    var scheduleType by rememberSaveable(initialPrescription) {
        mutableStateOf(initialPrescription?.schedule?.scheduleType ?: ScheduleType.DAILY)
    }
    var daysMask by rememberSaveable(initialPrescription) {
        mutableIntStateOf(initialPrescription?.schedule?.daysOfWeek?.toDaysMask() ?: 0)
    }
    var everyXDaysText by rememberSaveable(initialPrescription) {
        mutableStateOf(initialPrescription?.schedule?.everyXDays?.toString().orEmpty())
    }
    var startDate by rememberSaveable(initialPrescription, stateSaver = localDateSaver) {
        mutableStateOf(initialPrescription?.schedule?.startDate ?: today)
    }
    val times = rememberSaveable(initialPrescription, saver = timesSaver) {
        initialPrescription?.schedule?.timesOfDay.orEmpty().toMutableStateList()
    }

    var reminderLeadMinutes by rememberSaveable(initialPrescription) {
        mutableStateOf(initialPrescription?.schedule?.reminderLeadMinutes)
    }

    var typeMenuExpanded by remember { mutableStateOf(false) }
    var reminderMenuExpanded by remember { mutableStateOf(false) }
    var showCustomColorDialog by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val everyXDays = everyXDaysText.toIntOrNull()
    val isValid = name.isNotBlank() &&
            (scheduleType != ScheduleType.SPECIFIC_DAYS_OF_WEEK || daysMask != 0) &&
            (scheduleType != ScheduleType.EVERY_X_DAYS || (everyXDays != null && everyXDays > 0))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = if (initialPrescription == null) "New Prescription" else "Edit Prescription",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        SettingsGroup(title = "General") {
            FormTextRow(
                label = "Name",
                value = name,
                onValueChange = { name = it },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next
                )
            )

            HorizontalDivider()

            FormTextRow(
                label = "Dosage",
                value = dosis,
                onValueChange = { dosis = it },
                placeholder = "e.g. 500mg",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            HorizontalDivider()

            FormControlRow(label = "Color") {
                ColorSwatchPicker(
                    selected = colorArgb.toComposeColor(),
                    onSelect = { colorArgb = it.toStoredLong() },
                    onCustomClick = { showCustomColorDialog = true }
                )
            }
        }

        SettingsGroup(title = "Schedule") {
            FormPickerRow(
                label = "Type",
                value = scheduleTypeLabel(scheduleType),
                expanded = typeMenuExpanded,
                onExpandedChange = { typeMenuExpanded = it }
            ) { dismiss ->
                ScheduleType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(text = scheduleTypeLabel(type)) },
                        onClick = {
                            scheduleType = type
                            dismiss()
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = scheduleType == ScheduleType.SPECIFIC_DAYS_OF_WEEK,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider()
                    FormControlRow(label = "Days") {
                        DayOfWeekToggles(
                            daysMask = daysMask,
                            onToggle = { daysMask = daysMask.toggleDay(it) }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = scheduleType == ScheduleType.EVERY_X_DAYS,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider()
                    FormTextRow(
                        label = "Interval",
                        value = everyXDaysText,
                        onValueChange = { input ->
                            everyXDaysText = input.filter(Char::isDigit).take(3)
                        },
                        prefix = "Every",
                        suffix = "days",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        )
                    )
                }
            }

            HorizontalDivider()

            ListItem(
                headlineContent = { Text(text = "Start date") },
                supportingContent = { Text(text = formatStartDate(startDate)) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null
                    )
                },
                colors = settingsRowColors(),
                modifier = Modifier.clickable { showDatePicker = true }
            )

            HorizontalDivider()

            FormControlRow(label = "Times") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    times.forEach { time ->
                        InputChip(
                            selected = false,
                            onClick = { times.remove(time) },
                            label = { Text(text = formatTimeOfDay(time)) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove ${formatTimeOfDay(time)}",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                    AssistChip(
                        onClick = { showTimePicker = true },
                        label = { Text(text = "Add time") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
                if (times.isEmpty()) {
                    Text(
                        text = "No times added yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsGroup(title = "Reminder") {
            FormPickerRow(
                label = "Remind me",
                value = formatReminderLead(reminderLeadMinutes),
                expanded = reminderMenuExpanded,
                onExpandedChange = { reminderMenuExpanded = it }
            ) { dismiss ->
                reminderLeadOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = formatReminderLead(option)) },
                        onClick = {
                            reminderLeadMinutes = option
                            dismiss()
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = reminderLeadMinutes != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    HorizontalDivider()
                    Text(
                        text = "The reminder stays on screen until you mark the dose as taken, " +
                                "up to five minutes after it is due.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }
        }

        Button(
            onClick = {
                onSave(
                    Prescription(
                        id = initialPrescription?.id ?: 0,
                        name = name.trim(),
                        color = colorArgb,
                        dosis = dosis.trim(),
                        schedule = Schedule(
                            scheduleType = scheduleType,
                            daysOfWeek = if (scheduleType == ScheduleType.SPECIFIC_DAYS_OF_WEEK) {
                                daysMask.toDaysOfWeek()
                            } else {
                                emptyList()
                            },
                            everyXDays = if (scheduleType == ScheduleType.EVERY_X_DAYS) {
                                everyXDays
                            } else {
                                null
                            },
                            timesOfDay = times.sorted(),
                            reminderLeadMinutes = reminderLeadMinutes,
                            startDate = startDate
                        )
                    )
                )
            },
            enabled = isValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Save",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showCustomColorDialog) {
        CustomColorDialog(
            initial = colorArgb.toComposeColor(),
            onConfirm = { picked ->
                colorArgb = picked.toStoredLong()
                showCustomColorDialog = false
            },
            onDismiss = { showCustomColorDialog = false }
        )
    }

    if (showDatePicker) {
        DayPickerDialog(
            initialDate = startDate,
            onConfirm = { picked ->
                startDate = picked
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (showTimePicker) {
        TimeOfDayPickerDialog(
            onConfirm = { picked ->
                if (times.none { it == picked }) {
                    times.add(picked)
                    times.sort()
                }
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

/**
 * Seven connected toggles, one per weekday, starting on Monday.
 *
 * @param daysMask Monday-first bitmask of the currently selected days.
 * @param onToggle Invoked with the day whose selection was flipped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayOfWeekToggles(
    daysMask: Int,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier
) {
    MultiChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEachIndexed { index, day ->
            val fullName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            SegmentedButton(
                checked = daysMask.hasDay(day),
                onCheckedChange = { onToggle(day) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = DayOfWeek.entries.size
                ),
                icon = {},
                label = {
                    Text(text = fullName.take(1))
                },
                modifier = Modifier.semantics { contentDescription = fullName }
            )
        }
    }
}

/**
 * Time picker for adding a time of day.
 *
 * @param onConfirm Invoked with the chosen time when the user confirms.
 * @param onDismiss Invoked when the dialog is canceled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeOfDayPickerDialog(
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Add time") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(text = "OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

/**
 * A text field that sits inside a [SettingsGroup].
 *
 * @param label Floating label naming the field.
 * @param value Current text.
 * @param onValueChange Invoked with the edited text.
 * @param keyboardOptions Keyboard type and IME action for the field.
 * @param placeholder Hint shown while the field is empty.
 * @param prefix Text shown before the value.
 * @param suffix Text shown after the value.
 */
@Composable
private fun FormTextRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    suffix: String? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        placeholder = placeholder?.let { { Text(text = it) } },
        prefix = prefix?.let { { Text(text = it) } },
        suffix = suffix?.let { { Text(text = it) } },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * A labelled row for a control that does not fit a [ListItem].
 *
 * @param label Row label, styled like a [ListItem] headline.
 * @param content The control, laid out below the label.
 */
@Composable
private fun FormControlRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
    }
}

/**
 * A [ListItem] row that shows the current selection and opens a dropdown when tapped.
 *
 * @param label Row label.
 * @param value The current selection, shown as supporting text.
 * @param expanded Whether the dropdown is open.
 * @param onExpandedChange Invoked when the dropdown should open or close.
 * @param menuItems The dropdown entries; call `dismiss` to close the menu after picking.
 */
@Composable
private fun FormPickerRow(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menuItems: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    Box(modifier = modifier) {
        ListItem(
            headlineContent = { Text(text = label) },
            supportingContent = { Text(text = value) },
            trailingContent = {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null
                )
            },
            colors = settingsRowColors(),
            modifier = Modifier.clickable { onExpandedChange(true) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            menuItems { onExpandedChange(false) }
        }
    }
}

private val previewToday = LocalDate.of(2026, 8, 19)

@Preview(showBackground = true)
@Composable
private fun PrescriptionFormPreview() {
    PrescriptionHubTheme {
        PrescriptionForm(today = previewToday)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PrescriptionFormSpecificDaysPreview() {
    PrescriptionHubTheme {
        PrescriptionForm(
            today = previewToday,
            initialPrescription = Prescription(
                name = "Ibuprofen",
                color = prescriptionColors[8].toStoredLong(),
                dosis = "400mg",
                schedule = Schedule(
                    scheduleType = ScheduleType.SPECIFIC_DAYS_OF_WEEK,
                    daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                    startDate = previewToday
                )
            )
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PrescriptionFormEveryXDaysPreview() {
    PrescriptionHubTheme {
        PrescriptionForm(
            today = previewToday,
            initialPrescription = Prescription(
                name = "Vitamin D",
                color = prescriptionColors[3].toStoredLong(),
                dosis = "1 pill",
                schedule = Schedule(
                    scheduleType = ScheduleType.EVERY_X_DAYS,
                    everyXDays = 2,
                    timesOfDay = listOf(LocalTime.of(9, 30)),
                    startDate = previewToday
                )
            )
        )
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun PrescriptionFormEditPreview() {
    PrescriptionHubTheme {
        PrescriptionForm(
            today = previewToday,
            initialPrescription = Prescription(
                id = 7,
                name = "Metformin",
                color = prescriptionColors[6].toStoredLong(),
                dosis = "850mg",
                schedule = Schedule(
                    scheduleType = ScheduleType.DAILY,
                    timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(23, 0)),
                    startDate = LocalDate.of(2026, 1, 15)
                )
            )
        )
    }
}

@Preview(showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrescriptionFormDarkPreview() {
    PrescriptionHubTheme {
        PrescriptionForm(
            today = previewToday,
            initialPrescription = Prescription(
                name = "Metformin",
                color = prescriptionColors[6].toStoredLong(),
                dosis = "850mg",
                schedule = Schedule(
                    scheduleType = ScheduleType.DAILY,
                    timesOfDay = listOf(LocalTime.of(7, 0), LocalTime.of(13, 0), LocalTime.of(19, 0)),
                    startDate = previewToday
                )
            )
        )
    }
}
