package media.laura.prescriptionhub.data.backup

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

class BackupFormatException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
