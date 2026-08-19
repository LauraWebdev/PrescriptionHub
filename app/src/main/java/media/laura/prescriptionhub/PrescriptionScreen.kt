package media.laura.prescriptionhub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrescriptionScreen(
    modifier: Modifier = Modifier,
    onAddPrescription: () -> Unit = {}
) {
    var showPrescriptionForm by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Prescription") },
                actions = {
                    IconButton(
                        onClick = {
                            showPrescriptionForm = true
                            onAddPrescription()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Prescription"
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Prescription",
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    if (showPrescriptionForm) {
        ModalBottomSheet(
            onDismissRequest = {
                showPrescriptionForm = false
            }
        ) {
            PrescriptionForm(
                modifier = Modifier.padding(bottom = 24.dp),
                // TODO: persist via PrescriptionHubApplication.prescriptionService.addPrescription
                onSave = { showPrescriptionForm = false }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PrescriptionScreenPreview() {
    PrescriptionHubTheme {
        PrescriptionScreen()
    }
}
