package media.laura.prescriptionhub

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme

/**
 * Warns that reminders cannot be delivered punctually.
 *
 * @param onGrant Invoked when the user wants to fix the permission.
 * @param onDismiss Invoked when the user hides the warning for this session.
 */
@Composable
fun ExactAlarmBanner(
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.AlarmOff,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Reminders may arrive late",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Allow alarms and reminders so doses are announced on time.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onGrant) { Text(text = "Allow") }
                    TextButton(onClick = onDismiss) { Text(text = "Not now") }
                }
            }
        }
    }
}

/** Whether the app may schedule exact alarms, which reminders need to be punctual. */
fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val manager = context.getSystemService(AlarmManager::class.java) ?: return false
    return manager.canScheduleExactAlarms()
}

/** Opens the system screen where the exact alarm permission is granted. */
fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Preview(showBackground = true)
@Composable
private fun ExactAlarmBannerPreview() {
    PrescriptionHubTheme {
        ExactAlarmBanner(onGrant = {}, onDismiss = {})
    }
}
