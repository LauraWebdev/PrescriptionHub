package media.laura.prescriptionhub

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * A single prescription in the list.
 *
 * @param prescription The prescription to display.
 * @param onEdit Invoked when the row is tapped.
 * @param onDeleteRequest Invoked when the row has been swiped far enough to delete.
 */
@Composable
fun PrescriptionRow(
    prescription: Prescription,
    onEdit: (Prescription) -> Unit,
    onDeleteRequest: (Prescription) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnDeleteRequest by rememberUpdatedState(onDeleteRequest)
    val currentPrescription by rememberUpdatedState(prescription)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDeleteRequest(currentPrescription)
            }
            // Keep the row until the deletion is confirmed.
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = { DeleteSwipeBackground() },
        modifier = modifier
    ) {
        ListItem(
            headlineContent = { Text(text = prescription.name) },
            supportingContent = { Text(text = prescription.dosis) },
            leadingContent = { PrescriptionColorCircle(color = prescription.color) },
            trailingContent = {
                Text(
                    text = formatScheduleSummary(prescription.schedule),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier.widthIn(max = 132.dp)
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.clickable { onEdit(prescription) }
        )
    }
}

/** The prescription's user-chosen color. */
@Composable
fun PrescriptionColorCircle(
    color: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color.toComposeColor())
    )
}

@Composable
private fun DeleteSwipeBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private val previewToday = LocalDate.of(2026, 8, 19)

@Preview(showBackground = true)
@Composable
private fun PrescriptionRowPreview() {
    PrescriptionHubTheme {
        PrescriptionRow(
            prescription = Prescription(
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
            onEdit = {},
            onDeleteRequest = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrescriptionRowSpecificDaysPreview() {
    PrescriptionHubTheme {
        PrescriptionRow(
            prescription = Prescription(
                id = 2,
                name = "Gynokadin Estradiol",
                color = prescriptionColors[8].toStoredLong(),
                dosis = "1 pump on one arm",
                schedule = Schedule(
                    scheduleType = ScheduleType.SPECIFIC_DAYS_OF_WEEK,
                    daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                    timesOfDay = listOf(LocalTime.of(8, 0)),
                    startDate = previewToday
                )
            ),
            onEdit = {},
            onDeleteRequest = {}
        )
    }
}
