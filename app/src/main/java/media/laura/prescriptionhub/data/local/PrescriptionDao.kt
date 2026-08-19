package media.laura.prescriptionhub.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import media.laura.prescriptionhub.data.model.DoseIntakeRecord
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.PrescriptionSnapshot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Data Access Object for performing database operations on the prescriptions table.
 */
@Dao
interface PrescriptionDao {

    @Query("SELECT * FROM prescriptions ORDER BY id ASC")
    fun getAllPrescriptions(): Flow<List<Prescription>>

    @Query("SELECT * FROM prescriptions WHERE id = :id")
    fun getPrescriptionById(id: Long): Flow<Prescription?>

    @Query("SELECT * FROM prescriptions WHERE id = :id")
    suspend fun getPrescriptionByIdOnce(id: Long): Prescription?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescription(prescription: Prescription): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescriptions(prescriptions: List<Prescription>): List<Long>

    @Update
    suspend fun updatePrescription(prescription: Prescription)

    @Delete
    suspend fun deletePrescription(prescription: Prescription)

    @Query("DELETE FROM prescriptions WHERE id = :id")
    suspend fun deletePrescriptionById(id: Long)

    @Query("DELETE FROM prescriptions")
    suspend fun deleteAllPrescriptions()

    @Query("SELECT * FROM prescription_snapshots ORDER BY id ASC")
    fun getAllPrescriptionSnapshots(): Flow<List<PrescriptionSnapshot>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescriptionSnapshot(snapshot: PrescriptionSnapshot): Long

    @Update
    suspend fun updatePrescriptionSnapshot(snapshot: PrescriptionSnapshot)

    @Query("SELECT * FROM prescription_snapshots WHERE prescriptionId = :prescriptionId AND validTo IS NULL ORDER BY validFrom DESC LIMIT 1")
    suspend fun getOpenSnapshotForPrescription(prescriptionId: Long): PrescriptionSnapshot?

    @Query("SELECT * FROM prescription_snapshots WHERE validTo IS NULL")
    suspend fun getOpenSnapshots(): List<PrescriptionSnapshot>

    @Query("UPDATE prescription_snapshots SET validTo = :validTo WHERE id = :snapshotId")
    suspend fun closePrescriptionSnapshot(snapshotId: Long, validTo: LocalDateTime)

    @Query("SELECT * FROM dose_intake_records WHERE scheduledDate = :date")
    fun getDoseIntakeRecordsForDate(date: LocalDate): Flow<List<DoseIntakeRecord>>

    @Query("SELECT * FROM dose_intake_records WHERE snapshotId = :snapshotId AND scheduledDate = :date AND scheduledTime = :time")
    suspend fun getDoseIntakeRecord(snapshotId: Long, date: LocalDate, time: LocalTime): DoseIntakeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDoseIntakeRecord(record: DoseIntakeRecord)
}
