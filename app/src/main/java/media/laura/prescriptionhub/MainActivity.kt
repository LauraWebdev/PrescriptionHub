package media.laura.prescriptionhub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.core.content.ContextCompat
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme

class MainActivity : ComponentActivity() {

    /**
     * Navigation Request for notifications and a counter to re-navigate.
     */
    private var navRequest by mutableStateOf<Pair<Int, AppDestinations>?>(null)
    private var navRequestCount = 0

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readNavRequest(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            PrescriptionHubTheme {
                PrescriptionHubApp(navRequest = navRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readNavRequest(intent)
    }

    private fun readNavRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_TODAY, false) == true) {
            navRequestCount++
            navRequest = navRequestCount to AppDestinations.TODAY
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_TODAY = "media.laura.prescriptionhub.extra.OPEN_TODAY"
    }
}

/**
 * The app shell.
 *
 * @param navRequest A destination requested from Notifications
 */
@PreviewScreenSizes
@Composable
fun PrescriptionHubApp(navRequest: Pair<Int, AppDestinations>? = null) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.TODAY) }

    LaunchedEffect(navRequest) {
        navRequest?.let { (_, destination) -> currentDestination = destination }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestinations.TODAY -> TodayScreen()
            AppDestinations.CALENDAR -> CalendarScreen()
            AppDestinations.PRESCRIPTION -> PrescriptionScreen()
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    TODAY("Today", Icons.Default.Today),
    CALENDAR("Calendar", Icons.Default.History),
    PRESCRIPTION("Prescription", Icons.Default.Medication),
}
