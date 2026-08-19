package media.laura.prescriptionhub.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import media.laura.prescriptionhub.data.model.Prescription

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
}
