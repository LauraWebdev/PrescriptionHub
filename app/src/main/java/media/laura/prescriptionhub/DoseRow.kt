package media.laura.prescriptionhub

import android.content.res.Configuration
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * A single dose in the "Today" checklist.
 *
 * @param dose The dose to display.
 * @param onTakenChange Invoked when the row is tapped.
 */
@Composable
fun DoseRow(
    dose: DoseChecklistItem,
    onTakenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(text = dose.prescriptionName) },
        supportingContent = { Text(text = dose.dosis) },
        leadingContent = { PrescriptionColorCircle(color = dose.prescriptionColor) },
        trailingContent = {
            // The row itself carries the toggle, so the checkbox is only the visual state.
            Checkbox(checked = dose.taken, onCheckedChange = null)
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.toggleable(
            value = dose.taken,
            role = Role.Checkbox,
            onValueChange = onTakenChange
        )
    )
}

@Preview(showBackground = true)
@Composable
fun DoseRowOpenPreview() {
    PrescriptionHubTheme {
        DoseRow(
            dose = DoseChecklistItem(
                snapshotId = 1,
                prescriptionId = 1,
                prescriptionName = "Metformin",
                prescriptionColor = prescriptionColors[6].toStoredLong(),
                dosis = "850mg",
                scheduledDate = LocalDate.of(2026, 8, 19),
                scheduledTime = LocalTime.of(8, 0),
                taken = false,
                takenAt = null
            ),
            onTakenChange = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DoseRowTakenPreview() {
    PrescriptionHubTheme {
        DoseRow(
            dose = DoseChecklistItem(
                snapshotId = 2,
                prescriptionId = 2,
                prescriptionName = "Gynokadin Estradiol",
                prescriptionColor = prescriptionColors[8].toStoredLong(),
                dosis = "1 pump on one arm",
                scheduledDate = LocalDate.of(2026, 8, 19),
                scheduledTime = LocalTime.of(8, 0),
                taken = true,
                takenAt = LocalDateTime.of(2026, 8, 19, 8, 12)
            ),
            onTakenChange = {}
        )
    }
}
