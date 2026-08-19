package media.laura.prescriptionhub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme

@Composable
fun PrescriptionForm(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
    }
}

@Preview(showBackground = true)
@Composable
fun PrescriptionFormPreview() {
    PrescriptionHubTheme {
        PrescriptionForm()
    }
}
