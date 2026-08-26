package media.laura.prescriptionhub.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import media.laura.prescriptionhub.R
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.repository.AppDataWiper
import media.laura.prescriptionhub.reminder.DoseReminderScheduler
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime

/**
 * How much data one export or import moved, for the message shown afterwards.
 */
data class BackupSummary(
    val prescriptionCount: Int,
    val doseRecordCount: Int
)

/**
 * Writes the whole dataset out to a JSON file.
 *
 * @param context Used to reach the content resolver for the file the user picked.
 * @param database The database to read from and write to.
 * @param appDataWiper Clears the way for an import.
 * @param reminderScheduler Re-armed against the imported doses.
 * @param appVersion Recorded in the export for diagnostics.
 * @param nowProvider Supplies the export timestamp.
 * @param workDispatcher Where the file and database work runs.
 */
class AppDataBackup(
    private val context: Context,
    private val database: PrescriptionDatabase,
    private val appDataWiper: AppDataWiper,
    private val reminderScheduler: DoseReminderScheduler,
    private val appVersion: String = "",
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() },
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val dao = database.prescriptionDao()

    /** Reads everything currently stored. */
    suspend fun export(): BackupData = withContext(workDispatcher) {
        BackupData(
            exportedAt = nowProvider(),
            appVersion = appVersion,
            prescriptions = dao.getAllPrescriptionsOnce(),
            snapshots = dao.getAllSnapshotsOnce(),
            doseIntakeRecords = dao.getAllDoseIntakeRecordsOnce()
        )
    }

    /**
     * Replaces everything stored with [data], then re-arms the reminders.
     */
    suspend fun restore(data: BackupData): BackupSummary = withContext(workDispatcher) {
        val snapshotIds = data.snapshots.mapTo(mutableSetOf()) { it.id }
        val doseRecords = data.doseIntakeRecords.filter { it.snapshotId in snapshotIds }

        appDataWiper.wipe()
        database.withTransaction {
            dao.insertPrescriptions(data.prescriptions)
            dao.insertPrescriptionSnapshots(data.snapshots)
            dao.upsertDoseIntakeRecords(doseRecords)
        }
        reminderScheduler.rearmWindow()

        BackupSummary(
            prescriptionCount = data.prescriptions.size,
            doseRecordCount = doseRecords.size
        )
    }

    /**
     * Writes the whole dataset as JSON to the document at [uri].
     *
     * @throws IOException When the document cannot be written.
     */
    suspend fun exportTo(uri: Uri): BackupSummary = withContext(workDispatcher) {
        val data = export()
        val text = BackupJson.encode(data)
        val resolver = context.contentResolver

        val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri)
            ?: throw IOException("The selected file could not be opened for writing.")
        stream.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        }

        BackupSummary(
            prescriptionCount = data.prescriptions.size,
            doseRecordCount = data.doseIntakeRecords.size
        )
    }

    /**
     * Replaces the dataset with the backup stored at [uri].
     *
     * @throws BackupFormatException When the document is not a readable backup.
     * @throws IOException When the document cannot be read.
     */
    suspend fun importFrom(uri: Uri): BackupSummary = withContext(workDispatcher) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readCapped(MAX_IMPORT_BYTES)
        } ?: throw IOException("The selected file could not be opened.")

        restore(BackupJson.decode(text))
    }

    /**
     * Reads [this] as UTF-8.
     *
     * @throws BackupFormatException When the document is longer than [maxBytes].
     */
    private fun InputStream.readCapped(maxBytes: Int): String {
        val collected = ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(chunk)
            if (read == -1) break
            if (collected.size() + read > maxBytes) {
                throw BackupFormatException(R.string.backup_error_too_large)
            }
            collected.write(chunk, 0, read)
        }
        return collected.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 16 * 1024 * 1024
    }
}
