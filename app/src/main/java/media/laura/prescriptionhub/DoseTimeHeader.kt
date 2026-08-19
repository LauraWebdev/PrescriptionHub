package media.laura.prescriptionhub

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.LocalTime

/**
 * Header of one time slot on the calendar.
 */
@Composable
fun DoseTimeHeader(
    time: LocalTime,
    modifier: Modifier = Modifier
) {
    Text(
        text = formatTimeOfDay(time),
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun DoseTimeHeaderPreview() {
    PrescriptionHubTheme {
        DoseTimeHeader(time = LocalTime.of(8, 0))
    }
}
