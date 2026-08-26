package media.laura.prescriptionhub

import android.content.res.Configuration
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A single dose on the calendar.
 *
 * @param dose The dose to display.
 * @param now Deciding whether an open dose is still due or was missed.
 */
@Composable
fun DoseHistoryRow(
    dose: DoseChecklistItem,
    now: LocalDateTime,
    modifier: Modifier = Modifier
) {
    val status = doseStatus(dose, now)
    val label = formatDoseStatus(dose, now, LocalDateTimeFormats.current, LocalResources.current)

    ListItem(
        headlineContent = { Text(text = dose.prescriptionName) },
        supportingContent = { Text(text = dose.dosis) },
        leadingContent = { PrescriptionColorCircle(color = dose.prescriptionColor) },
        trailingContent = label?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status == DoseStatus.MISSED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier.widthIn(max = 132.dp)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    )
}

private fun previewDose(
    name: String,
    dosis: String,
    colorIndex: Int,
    taken: Boolean,
    takenAt: LocalDateTime? = null
) = DoseChecklistItem(
    snapshotId = 1,
    prescriptionId = 1,
    prescriptionName = name,
    prescriptionColor = prescriptionColors[colorIndex].toStoredLong(),
    dosis = dosis,
    scheduledDate = LocalDate.of(2026, 8, 19),
    scheduledTime = LocalTime.of(8, 0),
    taken = taken,
    takenAt = takenAt
)

@Preview(showBackground = true)
@Composable
private fun DoseHistoryRowTakenPreview() {
    PrescriptionHubTheme {
        DoseHistoryRow(
            dose = previewDose(
                name = "Metformin",
                dosis = "850mg",
                colorIndex = 6,
                taken = true,
                takenAt = LocalDateTime.of(2026, 8, 19, 8, 3)
            ),
            now = LocalDateTime.of(2026, 8, 19, 18, 0)
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DoseHistoryRowMissedPreview() {
    PrescriptionHubTheme {
        DoseHistoryRow(
            dose = previewDose(name = "Candecor", dosis = "1 pill", colorIndex = 0, taken = false),
            now = LocalDateTime.of(2026, 8, 19, 18, 0)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DoseHistoryRowUpcomingPreview() {
    PrescriptionHubTheme {
        DoseHistoryRow(
            dose = previewDose(name = "Progesteron", dosis = "1 pill", colorIndex = 2, taken = false),
            now = LocalDateTime.of(2026, 8, 19, 7, 0)
        )
    }
}
