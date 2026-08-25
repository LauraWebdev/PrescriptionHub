package media.laura.prescriptionhub

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme

/**
 * A labelled group of settings rows.
 *
 * @param title Section label rendered above the group, or `null` for an unlabelled group.
 * @param content The rows, laid out vertically.
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

/** List item colours that let the [SettingsGroup] surface show through. */
@Composable
fun settingsRowColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

@Preview(showBackground = true)
@Composable
private fun SettingsGroupPreview() {
    PrescriptionHubTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsGroup(title = "Section") {
                ListItem(
                    headlineContent = { Text(text = "First row") },
                    supportingContent = { Text(text = "With supporting text") },
                    colors = settingsRowColors()
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(text = "Second row") },
                    colors = settingsRowColors()
                )
            }

            SettingsGroup {
                ListItem(
                    headlineContent = { Text(text = "Unlabelled group") },
                    colors = settingsRowColors()
                )
            }
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsGroupDarkPreview() {
    PrescriptionHubTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsGroup(title = "Section") {
                ListItem(
                    headlineContent = { Text(text = "First row") },
                    colors = settingsRowColors()
                )
            }
        }
    }
}
