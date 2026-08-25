package media.laura.prescriptionhub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.core.content.ContextCompat
import androidx.window.core.layout.WindowSizeClass
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
@Composable
fun PrescriptionHubApp(navRequest: Pair<Int, AppDestinations>? = null) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.TODAY) }
    var overlay by rememberSaveable { mutableStateOf<AppOverlay?>(null) }

    LaunchedEffect(navRequest) {
        navRequest?.let { (_, destination) ->
            currentDestination = destination
            overlay = null
        }
    }

    BackHandler(enabled = overlay != null) {
        overlay = if (overlay == AppOverlay.LICENSES) AppOverlay.SETTINGS else null
    }

    when (overlay) {
        AppOverlay.SETTINGS -> SettingsScreen(
            onBack = { overlay = null },
            onOpenLicenses = { overlay = AppOverlay.LICENSES }
        )

        AppOverlay.LICENSES -> LicensesScreen(
            onBack = { overlay = AppOverlay.SETTINGS }
        )

        null -> {
            val windowAdaptiveInfo = currentWindowAdaptiveInfo()
            /*
             * Small screens receive the narrow icon-only NavigationRail. Bigger screens like
             * tablets use a sidebar with expanded labels.
             */
            val navigationLayoutType =
                if (windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
                        WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
                    ) &&
                    windowAdaptiveInfo.windowSizeClass.isHeightAtLeastBreakpoint(
                        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND
                    )
                ) {
                    NavigationSuiteType.WideNavigationRailExpanded
                } else {
                    NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(windowAdaptiveInfo)
                }

            NavigationSuiteScaffold(
                layoutType = navigationLayoutType,
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
                    AppDestinations.TODAY -> TodayScreen(
                        onOpenSettings = { overlay = AppOverlay.SETTINGS }
                    )

                    AppDestinations.CALENDAR -> CalendarScreen()
                    AppDestinations.PRESCRIPTION -> PrescriptionScreen()
                }
            }
        }
    }
}

/**
 * A screen shown on top of the tabs, without the navigation bar.
 */
enum class AppOverlay {
    SETTINGS,
    LICENSES,
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    TODAY("Today", Icons.Default.Today),
    CALENDAR("Calendar", Icons.Default.History),
    PRESCRIPTION("Prescription", Icons.Default.Medication),
}

@PreviewScreenSizes
@Composable
private fun PrescriptionHubAppPreview() {
    PrescriptionHubTheme {
        PrescriptionHubApp()
    }
}
