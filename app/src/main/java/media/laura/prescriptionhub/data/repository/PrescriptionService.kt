package media.laura.prescriptionhub.data.repository

import kotlinx.coroutines.flow.Flow
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.ScheduledDose
import java.time.LocalDate

/**
 * Service interface exposing prescription management and schedule queries.
 */
interface PrescriptionService {

    /**
     * Observes all prescriptions in the database.
     */
    fun getAllPrescriptions(): Flow<List<Prescription>>

    /**
     * Observes a single prescription by its [id].
     */
    fun getPrescriptionById(id: Long): Flow<Prescription?>

    /**
     * Fetches a single prescription by its [id] once.
     */
    suspend fun getPrescription(id: Long): Prescription?

    /**
     * Adds a new prescription and returns its generated row id.
     */
    suspend fun addPrescription(prescription: Prescription): Long

    /**
     * Updates an existing prescription.
     */
    suspend fun updatePrescription(prescription: Prescription)

    /**
     * Deletes the given prescription.
     */
    suspend fun deletePrescription(prescription: Prescription)

    /**
     * Deletes a prescription by its [id].
     */
    suspend fun deletePrescriptionById(id: Long)

    /**
     * Deletes all prescriptions from the database.
     */
    suspend fun deleteAllPrescriptions()

    /**
     * Observes prescriptions that are scheduled to be taken on a specific [date].
     */
    fun getPrescriptionsForDate(date: LocalDate): Flow<List<Prescription>>

    /**
     * Observes all individual dose events scheduled for a specific [date], sorted chronologically by time of day.
     */
    fun getScheduledDosesForDate(date: LocalDate): Flow<List<ScheduledDose>>
}
