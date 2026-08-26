package media.laura.prescriptionhub.data.repository

import kotlinx.coroutines.flow.Flow
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.data.model.DoseDayProgress
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.ScheduledDose
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

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

    /**
     * Observes checklist rows for [date] including taken state and exact taken timestamp.
     */
    fun getDoseChecklistForDate(date: LocalDate): Flow<List<DoseChecklistItem>>

    /**
     * Fetches every dose slot due between [start] and [end] once, both bounds inclusive.
     */
    suspend fun getDoseChecklistBetween(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<DoseChecklistItem>

    /**
     * Observes how much of each day between [startDate] and [endDate] has been taken.
     *
     * Days without any scheduled dose are left out.
     */
    fun getDoseProgressForDates(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DoseDayProgress>>

    /**
     * Stores the taken state for one prescription dose slot.
     *
     * @param takenAt When the dose was taken.
     */
    suspend fun setDoseTaken(
        snapshotId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        taken: Boolean,
        takenAt: LocalDateTime? = null
    )

    suspend fun setDosesTaken(doses: List<DoseChecklistItem>, taken: Boolean)

    /**
     * Records every dose of [prescriptionId] that was already due at [until] as taken at the time
     * it was scheduled for.
     *
     * @return How many doses were newly recorded.
     */
    suspend fun backfillTakenDoses(prescriptionId: Long, until: LocalDateTime): Int
}
