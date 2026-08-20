package media.laura.prescriptionhub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import media.laura.prescriptionhub.data.local.PrescriptionDao
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.data.model.DoseDayProgress
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
    private val nowProvider: () -> LocalDateTime
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

    override suspend fun getDoseChecklistBetween(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<DoseChecklistItem> {
        if (end < start) {
            return emptyList()
        }

        val startDate = start.toLocalDate()
        val endDate = end.toLocalDate()
        val snapshots = prescriptionDao.getAllPrescriptionSnapshots().first()
        val intakeByDate = prescriptionDao.getDoseIntakeRecordsBetween(startDate, endDate)
            .first()
            .groupBy { it.scheduledDate }

        return generateSequence(startDate) { previous ->
            previous.plusDays(1).takeIf { it <= endDate }
        }.flatMap { date ->
            buildDoseChecklistForDate(
                date = date,
                snapshots = snapshots,
                intakeRecords = intakeByDate[date].orEmpty()
            ).asSequence()
        }.filter { dose ->
            dose.scheduledDateTime >= start && dose.scheduledDateTime <= end
        }.toList()
    }

    override fun getDoseProgressForDates(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DoseDayProgress>> {
        if (endDate < startDate) {
            return flowOf(emptyList())
        }

        return combine(
            prescriptionDao.getAllPrescriptionSnapshots(),
            prescriptionDao.getDoseIntakeRecordsBetween(startDate, endDate)
        ) { snapshots, intakeRecords ->
            val intakeByDate = intakeRecords.groupBy { it.scheduledDate }

            generateSequence(startDate) { previous ->
                val next = previous.plusDays(1)
                next.takeIf { it <= endDate }
            }.mapNotNull { date ->
                val doses = buildDoseChecklistForDate(
                    date = date,
                    snapshots = snapshots,
                    intakeRecords = intakeByDate[date].orEmpty()
                )
                if (doses.isEmpty()) {
                    null
                } else {
                    DoseDayProgress(
                        date = date,
                        doseCount = doses.size,
                        takenCount = doses.count { it.taken }
                    )
                }
            }.toList()
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
            .filter { snapshot ->
                snapshot.validFrom <= dayEnd && (snapshot.validTo == null || snapshot.validTo >= dayStart)
            }
            .filter { snapshot ->
                snapshot.schedule.isScheduledOn(date)
            }
            .groupBy { snapshot -> snapshot.prescriptionId }
            .values
            .flatMap { snapshotsOfPrescription ->
                // A prescription that still exists covers the whole day, including the slots before
                // it was added or edited. One that was deleted during the day only covers the slots
                // it was actually there for, so it does not come back for the morning.
                val coversWholeDay = snapshotsOfPrescription.any { it.validTo == null }

                snapshotsOfPrescription
                    .flatMap { snapshot ->
                        snapshot.schedule.timesOfDay.mapNotNull { time ->
                            slotCandidate(
                                snapshot = snapshot,
                                date = date,
                                time = time,
                                coversWholeDay = coversWholeDay
                            )
                        }
                    }
                    // One row per slot, even when an edit split the day between two snapshots.
                    .groupBy { candidate -> candidate.time }
                    .values
                    .map { candidates -> candidates.maxWith(slotPrecedence) }
            }
            .map { candidate ->
                val snapshot = candidate.snapshot
                val intake = intakeBySlot[
                    DoseSlotKey(
                        snapshotId = snapshot.id,
                        date = date,
                        time = candidate.time
                    )
                ]
                DoseChecklistItem(
                    snapshotId = snapshot.id,
                    prescriptionId = snapshot.prescriptionId,
                    prescriptionName = snapshot.name,
                    prescriptionColor = snapshot.color,
                    dosis = snapshot.dosis,
                    scheduledDate = date,
                    scheduledTime = candidate.time,
                    taken = intake?.taken ?: false,
                    takenAt = intake?.takenAt,
                    reminderLeadMinutes = snapshot.schedule.reminderLeadMinutes
                )
            }
            .sorted()
    }

    /**
     * Decides whether [snapshot] renders the slot at [time] on [date], or `null` when it does not.
     */
    private fun slotCandidate(
        snapshot: PrescriptionSnapshot,
        date: LocalDate,
        time: LocalTime,
        coversWholeDay: Boolean
    ): SlotCandidate? {
        val scheduledDateTime = date.atTime(time)
        val isInEffect = scheduledDateTime >= snapshot.validFrom &&
            (snapshot.validTo == null || scheduledDateTime <= snapshot.validTo)
        val startsLaterThatDay = coversWholeDay &&
            scheduledDateTime < snapshot.validFrom &&
            snapshot.validFrom.toLocalDate() == date

        return when {
            isInEffect -> SlotCandidate(snapshot = snapshot, time = time, isInEffect = true)
            startsLaterThatDay -> SlotCandidate(snapshot = snapshot, time = time, isInEffect = false)
            else -> null
        }
    }

    private data class SlotCandidate(
        val snapshot: PrescriptionSnapshot,
        val time: LocalTime,
        val isInEffect: Boolean
    )

    private data class DoseSlotKey(
        val snapshotId: Long,
        val date: LocalDate,
        val time: LocalTime
    )

    private companion object {
        /**
         * Picks the snapshot that renders a slot, highest first.
         */
        val slotPrecedence: Comparator<SlotCandidate> = Comparator { first, second ->
            val byEffect = first.isInEffect.compareTo(second.isInEffect)
            when {
                byEffect != 0 -> byEffect
                first.isInEffect -> first.snapshot.validFrom.compareTo(second.snapshot.validFrom)
                else -> second.snapshot.validFrom.compareTo(first.snapshot.validFrom)
            }
        }
    }
}
