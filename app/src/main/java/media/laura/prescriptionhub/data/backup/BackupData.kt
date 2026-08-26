package media.laura.prescriptionhub.data.backup

import androidx.annotation.StringRes
import media.laura.prescriptionhub.data.model.DoseIntakeRecord
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.PrescriptionSnapshot
import java.time.LocalDateTime

/**
 * Data for a backup export/import.
 *
 * @param exportedAt When the export was written.
 * @param appVersion The app version that wrote the export, kept for diagnostics only.
 */
data class BackupData(
    val exportedAt: LocalDateTime,
    val appVersion: String = "",
    val prescriptions: List<Prescription> = emptyList(),
    val snapshots: List<PrescriptionSnapshot> = emptyList(),
    val doseIntakeRecords: List<DoseIntakeRecord> = emptyList()
)

/**
 * An error when importing a backup. 
 *
 * @param messageRes Message to the user
 */
class BackupFormatException(
    @StringRes val messageRes: Int,
    cause: Throwable? = null
) : Exception(cause)
