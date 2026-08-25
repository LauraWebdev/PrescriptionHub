package media.laura.prescriptionhub

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme

private const val WEBSITE_URL = "https://laura.media"
private const val DONATION_URL = "https://ko-fi.com/laurasofiaheimann"
private const val GITHUB_URL = "https://github.com/LauraWebdev/PrescriptionHub"

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
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    val context = LocalContext.current

    SettingsScreenContent(
        versionName = BuildConfig.VERSION_NAME,
        isDeleting = isDeleting,
        onBack = onBack,
        onOpenWebsite = { openUrl(context, WEBSITE_URL) },
        onOpenDonation = { openUrl(context, DONATION_URL) },
        onOpenGithub = { openUrl(context, GITHUB_URL) },
        onOpenLicenses = onOpenLicenses,
        onDeleteAllData = { viewModel.deleteAllData { restartApp(context) } },
        modifier = modifier
    )
}

/**
 * Settings.
 *
 * @param versionName The version to show below the app name.
 * @param isDeleting Whether a data wipe is running.
 * @param onBack Invoked when the user leaves the screen.
 * @param onOpenWebsite Invoked when the user taps the website row.
 * @param onOpenDonation Invoked when the user taps the donation row.
 * @param onOpenGithub Invoked when the user taps the source repository row.
 * @param onOpenLicenses Invoked when the user taps the licenses row.
 * @param onDeleteAllData Invoked once the user has confirmed the wipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    versionName: String,
    isDeleting: Boolean = false,
    onBack: () -> Unit = {},
    onOpenWebsite: () -> Unit = {},
    onOpenDonation: () -> Unit = {},
    onOpenGithub: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    onDeleteAllData: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                    headlineContent = { Text(text = "Developed by") },
                    supportingContent = { Text(text = "Laura Sofia Heimann") },
                    leadingContent = {
                        Icon(imageVector = Icons.Outlined.Person, contentDescription = null)
                    },
                    colors = settingsRowColors()
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text(text = "Website") },
                    supportingContent = { Text(text = "laura.media") },
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
                    headlineContent = { Text(text = "Donate on Ko-Fi") },
                    supportingContent = { Text(text = "Support open-source development") },
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

            SettingsGroup(title = "Project") {
                ListItem(
                    headlineContent = { Text(text = "GitHub") },
                    supportingContent = { Text(text = "PrescriptionHub") },
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
                    headlineContent = { Text(text = "Licenses") },
                    supportingContent = { Text(text = "Open-source licenses") },
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

            SettingsGroup(title = "Data") {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Delete all data",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    supportingContent = {
                        Text(text = "Removes every prescription and dose record")
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
                    modifier = Modifier.clickable(enabled = !isDeleting) {
                        showDeleteConfirmation = true
                    }
                )
            }

            Text(
                text = "Prescription Hub is free software, licensed under the GNU General " +
                        "Public License v3.0.",
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
            title = { Text(text = "Delete all data?") },
            text = {
                Text(
                    text = "This permanently removes every prescription and every dose you " +
                            "have recorded, cancels all reminders, and restarts Prescription " +
                            "Hub. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteAllData()
                    }
                ) {
                    Text(
                        text = "Delete everything",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(text = "Cancel")
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
            text = "Prescription Hub",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = "Version $versionName",
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

@Preview(showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenDarkPreview() {
    PrescriptionHubTheme {
        SettingsScreenContent(versionName = "1.0")
    }
}
