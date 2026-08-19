package media.laura.prescriptionhub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import media.laura.prescriptionhub.data.local.PrescriptionDao
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.data.model.DoseIntakeRecord
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.PrescriptionSnapshot
import media.laura.prescriptionhub.data.model.ScheduledDose
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Concrete implementation of [PrescriptionService] backed by Room [PrescriptionDao].
 */
class PrescriptionRepository(
    private val prescriptionDao: PrescriptionDao,
    private val nowProvider: () -> LocalDateTime = { LocalDateTime.now() }
) : PrescriptionService {

    override fun getAllPrescriptions(): Flow<List<Prescription>> {
        return prescriptionDao.getAllPrescriptions()
    }

    override fun getPrescriptionById(id: Long): Flow<Prescription?> {
        return prescriptionDao.getPrescriptionById(id)
    }

    override suspend fun getPrescription(id: Long): Prescription? {
        return prescriptionDao.getPrescriptionByIdOnce(id)
    }

    override suspend fun addPrescription(prescription: Prescription): Long {
        val id = prescriptionDao.insertPrescription(prescription)
        val snapshot = PrescriptionSnapshot.fromPrescription(
            prescription = prescription.copy(id = id),
            validFrom = nowProvider()
        )
        prescriptionDao.insertPrescriptionSnapshot(snapshot)
        return id
    }

    override suspend fun updatePrescription(prescription: Prescription) {
        val closedAt = nowProvider()
        closeOpenSnapshotForPrescription(prescription.id, closedAt)
        prescriptionDao.updatePrescription(prescription)
        val snapshot = PrescriptionSnapshot.fromPrescription(
            prescription = prescription,
            validFrom = closedAt
        )
        prescriptionDao.insertPrescriptionSnapshot(snapshot)
    }

    override suspend fun deletePrescription(prescription: Prescription) {
        closeOpenSnapshotForPrescription(prescription.id, nowProvider())
        prescriptionDao.deletePrescription(prescription)
    }

    override suspend fun deletePrescriptionById(id: Long) {
        closeOpenSnapshotForPrescription(id, nowProvider())
        prescriptionDao.deletePrescriptionById(id)
    }

    override suspend fun deleteAllPrescriptions() {
        val closedAt = nowProvider()
        prescriptionDao.getOpenSnapshots().forEach { snapshot ->
            prescriptionDao.closePrescriptionSnapshot(snapshot.id, closedAt)
        }
        prescriptionDao.deleteAllPrescriptions()
    }

    override fun getPrescriptionsForDate(date: LocalDate): Flow<List<Prescription>> {
        return prescriptionDao.getAllPrescriptions().map { list ->
            list.filter { prescription ->
                prescription.schedule.isScheduledOn(date)
            }
        }
    }

    override fun getScheduledDosesForDate(date: LocalDate): Flow<List<ScheduledDose>> {
        return prescriptionDao.getAllPrescriptions().map { list ->
            list.filter { prescription ->
                prescription.schedule.isScheduledOn(date)
            }.flatMap { prescription ->
                prescription.schedule.timesOfDay.map { time ->
                    ScheduledDose(prescription = prescription, time = time)
                }
            }.sorted()
        }
    }

    override fun getDoseChecklistForDate(date: LocalDate): Flow<List<DoseChecklistItem>> {
        return combine(
            prescriptionDao.getAllPrescriptionSnapshots(),
            prescriptionDao.getDoseIntakeRecordsForDate(date)
        ) { snapshots, intakeRecords ->
            buildDoseChecklistForDate(
                date = date,
                snapshots = snapshots,
                intakeRecords = intakeRecords
            )
        }
    }

    override suspend fun setDoseTaken(
        snapshotId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        taken: Boolean,
        takenAt: LocalDateTime?
    ) {
        val normalizedTakenAt = if (taken) (takenAt ?: nowProvider()) else null
        val existing = prescriptionDao.getDoseIntakeRecord(snapshotId, scheduledDate, scheduledTime)
        val record = if (existing == null) {
            DoseIntakeRecord(
                snapshotId = snapshotId,
                scheduledDate = scheduledDate,
                scheduledTime = scheduledTime,
                taken = taken,
                takenAt = normalizedTakenAt
            )
        } else {
            existing.copy(
                taken = taken,
                takenAt = normalizedTakenAt
            )
        }
        prescriptionDao.upsertDoseIntakeRecord(record)
    }

    private suspend fun closeOpenSnapshotForPrescription(
        prescriptionId: Long,
        closedAt: LocalDateTime
    ) {
        val openSnapshot = prescriptionDao.getOpenSnapshotForPrescription(prescriptionId)
        if (openSnapshot != null) {
            prescriptionDao.closePrescriptionSnapshot(openSnapshot.id, closedAt)
        }
    }

    private fun buildDoseChecklistForDate(
        date: LocalDate,
        snapshots: List<PrescriptionSnapshot>,
        intakeRecords: List<DoseIntakeRecord>
    ): List<DoseChecklistItem> {
        val dayStart = date.atStartOfDay()
        val dayEnd = date.atTime(LocalTime.MAX)
        val intakeBySlot = intakeRecords.associateBy {
            DoseSlotKey(
                snapshotId = it.snapshotId,
                date = it.scheduledDate,
                time = it.scheduledTime
            )
        }

        return snapshots
            .asSequence()
            .filter { snapshot ->
                snapshot.validFrom <= dayEnd && (snapshot.validTo == null || snapshot.validTo >= dayStart)
            }
            .filter { snapshot ->
                snapshot.schedule.isScheduledOn(date)
            }
            .flatMap { snapshot ->
                snapshot.schedule.timesOfDay.asSequence().mapNotNull { time ->
                    val scheduledDateTime = date.atTime(time)
                    val isActiveAtTime =
                        scheduledDateTime >= snapshot.validFrom &&
                            (snapshot.validTo == null || scheduledDateTime <= snapshot.validTo)

                    if (!isActiveAtTime) {
                        null
                    } else {
                        val intake = intakeBySlot[
                            DoseSlotKey(
                                snapshotId = snapshot.id,
                                date = date,
                                time = time
                            )
                        ]
                        DoseChecklistItem(
                            snapshotId = snapshot.id,
                            prescriptionId = snapshot.prescriptionId,
                            prescriptionName = snapshot.name,
                            prescriptionColor = snapshot.color,
                            dosis = snapshot.dosis,
                            scheduledDate = date,
                            scheduledTime = time,
                            taken = intake?.taken ?: false,
                            takenAt = intake?.takenAt
                        )
                    }
                }
            }
            .sorted()
            .toList()
    }

    private data class DoseSlotKey(
        val snapshotId: Long,
        val date: LocalDate,
        val time: LocalTime
    )
}
