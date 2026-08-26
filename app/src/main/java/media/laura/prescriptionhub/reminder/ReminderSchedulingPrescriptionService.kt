package media.laura.prescriptionhub.reminder

import kotlinx.coroutines.flow.Flow
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.data.model.DoseDayProgress
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.ScheduledDose
import media.laura.prescriptionhub.data.repository.PrescriptionService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Keeps dose reminders by re-arming them after every write.
 *
 * @param delegate The data service.
 * @param scheduler The scheduler to re-arm.
 */
class ReminderSchedulingPrescriptionService(
    private val delegate: PrescriptionService,
    private val scheduler: DoseReminderScheduler
) : PrescriptionService {

    override fun getAllPrescriptions(): Flow<List<Prescription>> =
        delegate.getAllPrescriptions()

    override fun getPrescriptionById(id: Long): Flow<Prescription?> =
        delegate.getPrescriptionById(id)

    override suspend fun getPrescription(id: Long): Prescription? =
        delegate.getPrescription(id)

    override suspend fun addPrescription(prescription: Prescription): Long =
        delegate.addPrescription(prescription).also { scheduler.rearmWindow() }

    override suspend fun updatePrescription(prescription: Prescription) {
        delegate.updatePrescription(prescription)
        scheduler.rearmWindow()
    }

    override suspend fun deletePrescription(prescription: Prescription) {
        delegate.deletePrescription(prescription)
        scheduler.rearmWindow()
    }

    override suspend fun deletePrescriptionById(id: Long) {
        delegate.deletePrescriptionById(id)
        scheduler.rearmWindow()
    }

    override suspend fun deleteAllPrescriptions() {
        delegate.deleteAllPrescriptions()
        scheduler.rearmWindow()
    }

    override fun getPrescriptionsForDate(date: LocalDate): Flow<List<Prescription>> =
        delegate.getPrescriptionsForDate(date)

    override fun getScheduledDosesForDate(date: LocalDate): Flow<List<ScheduledDose>> =
        delegate.getScheduledDosesForDate(date)

    override fun getDoseChecklistForDate(date: LocalDate): Flow<List<DoseChecklistItem>> =
        delegate.getDoseChecklistForDate(date)

    override suspend fun getDoseChecklistBetween(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<DoseChecklistItem> = delegate.getDoseChecklistBetween(start, end)

    override fun getDoseProgressForDates(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DoseDayProgress>> = delegate.getDoseProgressForDates(startDate, endDate)

    /**
     * Ticking a dose off cancels its reminder. Un-ticking brings the reminder back.
     */
    override suspend fun setDoseTaken(
        snapshotId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        taken: Boolean,
        takenAt: LocalDateTime?
    ) {
        delegate.setDoseTaken(snapshotId, scheduledDate, scheduledTime, taken, takenAt)
        scheduler.rearmWindow()
    }

    override suspend fun setDosesTaken(doses: List<DoseChecklistItem>, taken: Boolean) {
        delegate.setDosesTaken(doses, taken)
        scheduler.rearmWindow()
    }

    override suspend fun backfillTakenDoses(prescriptionId: Long, until: LocalDateTime): Int =
        delegate.backfillTakenDoses(prescriptionId, until).also { scheduler.rearmWindow() }
}
