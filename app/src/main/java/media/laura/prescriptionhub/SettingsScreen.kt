package media.laura.prescriptionhub

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.LocalDate

private const val WEBSITE_URL = "https://laura.media"
private const val DONATION_URL = "https://ko-fi.com/laurasofiaheimann"
private const val GITHUB_URL = "https://github.com/LauraWebdev/PrescriptionHub"

private const val BACKUP_MIME_TYPE = "application/json"

/**
 * Filepicker options for backup import/export.
 */
private val IMPORT_MIME_TYPES = arrayOf(
    BACKUP_MIME_TYPE,
    "text/plain",
    "application/octet-stream"
)

/**
 * The settings screen, with the about information at the top.
 *
 * @param onBack Invoked when the user leaves the screen.
 * @param onOpenLicenses Invoked when the user wants to read the license information.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { target -> target?.let(viewModel::exportData) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source -> source?.let(viewModel::importData) }

    SettingsScreenContent(
        versionName = BuildConfig.VERSION_NAME,
        isDeleting = uiState.isDeleting,
        isExporting = uiState.isExporting,
        isImporting = uiState.isImporting,
        message = uiState.message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
        onOpenWebsite = { openUrl(context, WEBSITE_URL) },
        onOpenDonation = { openUrl(context, DONATION_URL) },
        onOpenGithub = { openUrl(context, GITHUB_URL) },
        onOpenLicenses = onOpenLicenses,
        onExportData = { exportLauncher.launch(backupFileName(LocalDate.now())) },
        onImportData = { importLauncher.launch(IMPORT_MIME_TYPES) },
        onDeleteAllData = { viewModel.deleteAllData { restartApp(context) } },
        modifier = modifier
    )
}

/** e.g. `prescription-hub-2026-08-26.json`. */
fun backupFileName(date: LocalDate): String = "prescription-hub-$date.json"

/**
 * Settings.
 *
 * @param versionName The version to show below the app name.
 * @param isDeleting Whether a data wipe is running.
 * @param isExporting Whether an export is running.
 * @param isImporting Whether an import is running.
 * @param message The outcome of the last export or import.
 * @param onMessageShown Invoked once [message] has been shown.
 * @param onBack Invoked when the user leaves the screen.
 * @param onOpenWebsite Invoked when the user taps the website row.
 * @param onOpenDonation Invoked when the user taps the donation row.
 * @param onOpenGithub Invoked when the user taps the source repository row.
 * @param onOpenLicenses Invoked when the user taps the licenses row.
 * @param onExportData Invoked when the user wants to write a backup.
 * @param onImportData Invoked once the user has confirmed reading a backup.
 * @param onDeleteAllData Invoked once the user has confirmed the wipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    versionName: String,
    isDeleting: Boolean = false,
    isExporting: Boolean = false,
    isImporting: Boolean = false,
    message: String? = null,
    onMessageShown: () -> Unit = {},
    onBack: () -> Unit = {},
    onOpenWebsite: () -> Unit = {},
    onOpenDonation: () -> Unit = {},
    onOpenGithub: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    onExportData: () -> Unit = {},
    onImportData: () -> Unit = {},
    onDeleteAllData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    var showImportConfirmation by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val isBusy = isDeleting || isExporting || isImporting

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            AppIdentityHeader(versionName = versionName)

            SettingsGroup {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.settings_developed_by)) },
                    supportingContent = { Text(text = stringResource(R.string.developer_name)) },
                    leadingContent = {
                        Icon(imageVector = Icons.Outlined.Person, contentDescription = null)
                    },
                    colors = settingsRowColors()
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.settings_website)) },
                    supportingContent = { Text(text = stringResource(R.string.website_host)) },
                    leadingContent = {
                        Icon(imageVector = Icons.Outlined.Language, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null
                        )
                    },
                    colors = settingsRowColors(),
                    modifier = Modifier.clickable(onClick = onOpenWebsite)
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.settings_donate)) },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_donate_description))
                    },
                    leadingContent = {
                        Icon(imageVector = Icons.Outlined.FavoriteBorder, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null
                        )
                    },
                    colors = settingsRowColors(),
                    modifier = Modifier.clickable(onClick = onOpenDonation)
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_section_project)) {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.settings_github)) },
                    supportingContent = { Text(text = stringResource(R.string.github_repository)) },
                    leadingContent = {
                        Icon(imageVector = Icons.Outlined.Code, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null
                        )
                    },
                    colors = settingsRowColors(),
                    modifier = Modifier.clickable(onClick = onOpenGithub)
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.settings_licenses)) },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_licenses_description))
                    },
                    leadingContent = {
                        Icon(imageVector = Icons.Outlined.Description, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    colors = settingsRowColors(),
                    modifier = Modifier.clickable(onClick = onOpenLicenses)
                )
            }

            SettingsGroup(title = stringResource(R.string.settings_section_data)) {
                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.settings_export)) },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_export_description))
                    },
                    leadingContent = {
                        Icon(imageVector = Icons.Outlined.Upload, contentDescription = null)
                    },
                    trailingContent = if (isExporting) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                    } else {
                        null
                    },
                    colors = settingsRowColors(),
                    modifier = Modifier.clickable(enabled = !isBusy, onClick = onExportData)
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text(text = stringResource(R.string.settings_import)) },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_import_description))
                    },
                    leadingContent = {
                        Icon(imageVector = Icons.Outlined.Download, contentDescription = null)
                    },
                    trailingContent = if (isImporting) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                    } else {
                        null
                    },
                    colors = settingsRowColors(),
                    modifier = Modifier.clickable(enabled = !isBusy) {
                        showImportConfirmation = true
                    }
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.settings_delete_all),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = {
                        Text(text = stringResource(R.string.settings_delete_all_description))
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    trailingContent = if (isDeleting) {
                        { CircularProgressIndicator(modifier = Modifier.size(20.dp)) }
                    } else {
                        null
                    },
                    colors = settingsRowColors(),
                    modifier = Modifier.clickable(enabled = !isBusy) {
                        showDeleteConfirmation = true
                    }
                )
            }

            Text(
                text = stringResource(R.string.settings_license_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(text = stringResource(R.string.settings_delete_dialog_title)) },
            text = {
                Text(text = stringResource(R.string.settings_delete_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteAllData()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.settings_delete_dialog_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            title = { Text(text = stringResource(R.string.settings_import_dialog_title)) },
            text = {
                Text(text = stringResource(R.string.settings_import_dialog_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmation = false
                        onImportData()
                    }
                ) {
                    Text(text = stringResource(R.string.settings_import_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmation = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/**
 * App icon, name and version.
 *
 * @param versionName The version to show below the app name.
 */
@Composable
private fun AppIdentityHeader(
    versionName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 32.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon()

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = stringResource(R.string.settings_version, versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The launcher icon.
 */
@Composable
private fun AppIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clip(MaterialTheme.shapes.extraLarge)
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_background),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.5f)
        )
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.5f)
        )
    }
}

/** Opens [url] in whichever browser the user has. */
fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * Relaunches the app in a fresh process.
 */
fun restartApp(context: Context) {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SettingsScreenPreview() {
    PrescriptionHubTheme {
        SettingsScreenContent(versionName = "1.0")
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SettingsScreenDeletingPreview() {
    PrescriptionHubTheme {
        SettingsScreenContent(versionName = "1.0", isDeleting = true)
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun SettingsScreenExportingPreview() {
    PrescriptionHubTheme {
        SettingsScreenContent(versionName = "1.0", isExporting = true)
    }
}

@Preview(showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenDarkPreview() {
    PrescriptionHubTheme {
        SettingsScreenContent(versionName = "1.0")
    }
}
