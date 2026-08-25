package media.laura.prescriptionhub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import media.laura.prescriptionhub.data.repository.AppDataWiper

/**
 * Backs the settings screen.
 *
 * @param appDataWiper Erases every trace of the user's data.
 */
class SettingsViewModel(private val appDataWiper: AppDataWiper) : ViewModel() {

    private val _isDeleting = MutableStateFlow(false)

    /** Whether a wipe is currently running. */
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    /**
     * Erases every prescription, dose and reminder, then invokes [onWiped].
     *
     * @param onWiped Runs once the data is gone, so the caller can restart the app.
     */
    fun deleteAllData(onWiped: () -> Unit) {
        if (_isDeleting.value) return
        _isDeleting.value = true
        viewModelScope.launch {
            appDataWiper.wipe()
            onWiped()
        }
    }
}
