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
 * One dose slot that was recorded as taken.
 */
data class TakenSlot(
    val snapshotId: Long,
    val scheduledDate: LocalDate,
    val scheduledTime: LocalTime
)

/**
 * Data Access Object for performing database operations on the prescriptions table.
 */
@Dao
interface PrescriptionDao {

    @Query("SELECT * FROM prescriptions ORDER BY id ASC")
    fun getAllPrescriptions(): Flow<List<Prescription>>

    @Query("SELECT * FROM prescriptions ORDER BY id ASC")
    suspend fun getAllPrescriptionsOnce(): List<Prescription>

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

    /**
     * Observes the snapshots whose validity overlaps [rangeStart]..[rangeEnd].
     */
    @Query(
        """
        SELECT * FROM prescription_snapshots
        WHERE validFrom <= :rangeEnd AND (validTo IS NULL OR validTo >= :rangeStart)
        ORDER BY id ASC
        """
    )
    fun getSnapshotsOverlapping(
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime
    ): Flow<List<PrescriptionSnapshot>>

    /** Reads the snapshots overlapping [rangeStart]..[rangeEnd] once. */
    @Query(
        """
        SELECT * FROM prescription_snapshots
        WHERE validFrom <= :rangeEnd AND (validTo IS NULL OR validTo >= :rangeStart)
        ORDER BY id ASC
        """
    )
    suspend fun getSnapshotsOverlappingOnce(
        rangeStart: LocalDateTime,
        rangeEnd: LocalDateTime
    ): List<PrescriptionSnapshot>

    @Query("SELECT * FROM prescription_snapshots ORDER BY id ASC")
    suspend fun getAllSnapshotsOnce(): List<PrescriptionSnapshot>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescriptionSnapshot(snapshot: PrescriptionSnapshot): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrescriptionSnapshots(snapshots: List<PrescriptionSnapshot>): List<Long>

    @Update
    suspend fun updatePrescriptionSnapshot(snapshot: PrescriptionSnapshot)

    @Query("SELECT * FROM prescription_snapshots WHERE prescriptionId = :prescriptionId AND validTo IS NULL ORDER BY validFrom DESC LIMIT 1")
    suspend fun getOpenSnapshotForPrescription(prescriptionId: Long): PrescriptionSnapshot?

    @Query("SELECT * FROM prescription_snapshots WHERE validTo IS NULL")
    suspend fun getOpenSnapshots(): List<PrescriptionSnapshot>

    @Query(
        """
        SELECT * FROM prescription_snapshots WHERE prescriptionId = :prescriptionId
        ORDER BY validFrom ASC, id ASC LIMIT 1
        """
    )
    suspend fun getEarliestSnapshotForPrescription(prescriptionId: Long): PrescriptionSnapshot?

    @Query("UPDATE prescription_snapshots SET validTo = :validTo WHERE id = :snapshotId")
    suspend fun closePrescriptionSnapshot(snapshotId: Long, validTo: LocalDateTime)

    @Query("UPDATE prescription_snapshots SET validFrom = :validFrom WHERE id = :snapshotId")
    suspend fun backdatePrescriptionSnapshot(snapshotId: Long, validFrom: LocalDateTime)

    @Query("SELECT * FROM dose_intake_records ORDER BY id ASC")
    suspend fun getAllDoseIntakeRecordsOnce(): List<DoseIntakeRecord>

    @Query("SELECT * FROM dose_intake_records WHERE scheduledDate = :date")
    fun getDoseIntakeRecordsForDate(date: LocalDate): Flow<List<DoseIntakeRecord>>

    @Query("SELECT * FROM dose_intake_records WHERE scheduledDate BETWEEN :startDate AND :endDate")
    suspend fun getDoseIntakeRecordsBetween(startDate: LocalDate, endDate: LocalDate): List<DoseIntakeRecord>

    /**
     * Observes just the slots recorded as taken between [startDate] and [endDate].
     */
    @Query(
        """
        SELECT snapshotId, scheduledDate, scheduledTime FROM dose_intake_records
        WHERE taken = 1 AND scheduledDate BETWEEN :startDate AND :endDate
        """
    )
    fun getTakenSlotsBetween(startDate: LocalDate, endDate: LocalDate): Flow<List<TakenSlot>>

    @Query("SELECT * FROM dose_intake_records WHERE snapshotId = :snapshotId AND scheduledDate = :date AND scheduledTime = :time")
    suspend fun getDoseIntakeRecord(snapshotId: Long, date: LocalDate, time: LocalTime): DoseIntakeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDoseIntakeRecord(record: DoseIntakeRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDoseIntakeRecords(records: List<DoseIntakeRecord>)
}
