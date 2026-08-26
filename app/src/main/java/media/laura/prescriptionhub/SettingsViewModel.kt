package media.laura.prescriptionhub

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import media.laura.prescriptionhub.data.backup.AppDataBackup
import media.laura.prescriptionhub.data.backup.BackupFormatException
import media.laura.prescriptionhub.data.backup.BackupSummary
import media.laura.prescriptionhub.data.repository.AppDataWiper

/**
 * @param isDeleting Whether a wipe is running.
 * @param isExporting Whether an export is running.
 * @param isImporting Whether an import is running.
 * @param message The outcome of the last export or import.
 */
data class SettingsUiState(
    val isDeleting: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
    val message: String? = null
) {
    /** Whether another data operation may start. */
    val isBusy: Boolean get() = isDeleting || isExporting || isImporting
}

/**
 * Backs the settings screen.
 *
 * @param appDataWiper Erases every trace of the user's data.
 * @param appDataBackup Moves the dataset in and out of a JSON file.
 */
class SettingsViewModel(
    private val appDataWiper: AppDataWiper,
    private val appDataBackup: AppDataBackup
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())

    /** What the screen renders. */
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Erases every prescription, dose and reminder, then invokes [onWiped].
     *
     * @param onWiped Runs once the data is gone, so the caller can restart the app.
     */
    fun deleteAllData(onWiped: () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            appDataWiper.wipe()
            onWiped()
        }
    }

    /**
     * @param target Path to where the export will be written.
     */
    fun exportData(target: Uri) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val message = runCatching { appDataBackup.exportTo(target) }
                .fold(
                    onSuccess = { summary -> "Exported ${summary.describe()}." },
                    onFailure = { error -> failureMessage("Export failed", error) }
                )
            _uiState.update { it.copy(isExporting = false, message = message) }
        }
    }

    /**
     * @param source Path of the export that will be imported.
     */
    fun importData(source: Uri) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val message = runCatching { appDataBackup.importFrom(source) }
                .fold(
                    onSuccess = { summary -> "Imported ${summary.describe()}." },
                    onFailure = { error -> failureMessage("Import failed", error) }
                )
            _uiState.update { it.copy(isImporting = false, message = message) }
        }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Turns an [error] into human readable text.
     */
    private fun failureMessage(prefix: String, error: Throwable): String {
        Log.w(TAG, prefix, error)
        return when (error) {
            is BackupFormatException -> "$prefix: ${error.message}"
            else -> "$prefix. The file could not be read or written."
        }
    }

    private fun BackupSummary.describe(): String =
        "${count(prescriptionCount, "prescription")} and ${count(doseRecordCount, "dose record")}"

    private fun count(value: Int, noun: String): String =
        if (value == 1) "$value $noun" else "$value ${noun}s"

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
