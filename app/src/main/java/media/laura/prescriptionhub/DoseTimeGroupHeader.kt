package media.laura.prescriptionhub

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.LocalDate
import java.time.LocalTime

/**
 * Header of one time slot in the "Today" checklist
 *
 * @param group The time slot to describe.
 * @param onCheckAll Invoked when the user wants every open dose of the slot marked as taken.
 */
@Composable
fun DoseTimeGroupHeader(
    group: DoseTimeGroup,
    onCheckAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = formatTimeOfDay(group.time),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )

        if (!group.isComplete) {
            AssistChip(
                onClick = onCheckAll,
                label = { Text(text = "Check all") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                border = null
            )
        }

        DoseProgressRing(group = group)
    }
}

/** The slot's completion as a ring. */
@Composable
private fun DoseProgressRing(
    group: DoseTimeGroup,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(targetValue = group.progress, label = "doseProgress")

    CircularProgressIndicator(
        progress = { progress },
        strokeWidth = 3.dp,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .size(24.dp)
            .clearAndSetSemantics {
                contentDescription = "${group.takenCount} of ${group.doses.size} taken"
            }
    )
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

@Preview(showBackground = true)
@Composable
private fun DoseTimeGroupHeaderPartialPreview() {
    PrescriptionHubTheme {
        DoseTimeGroupHeader(
            group = DoseTimeGroup(
                time = LocalTime.of(23, 0),
                doses = listOf(
                    previewDose(1, "Metformin", "850mg", 6, LocalTime.of(23, 0), taken = true),
                    previewDose(2, "Progesteron", "1 pill", 0, LocalTime.of(23, 0), taken = false),
                    previewDose(3, "Gynokadin", "1 pump", 8, LocalTime.of(23, 0), taken = false)
                )
            ),
            onCheckAll = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DoseTimeGroupHeaderCompletePreview() {
    PrescriptionHubTheme {
        DoseTimeGroupHeader(
            group = DoseTimeGroup(
                time = LocalTime.of(8, 0),
                doses = listOf(
                    previewDose(1, "Metformin", "850mg", 6, LocalTime.of(8, 0), taken = true),
                    previewDose(2, "Candecor", "1 pill", 0, LocalTime.of(8, 0), taken = true)
                )
            ),
            onCheckAll = {}
        )
    }
}
