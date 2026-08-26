package media.laura.prescriptionhub.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import media.laura.prescriptionhub.data.local.PrescriptionDao
import media.laura.prescriptionhub.data.local.TakenSlot
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
 *
 * @param workDispatcher Where snapshot filtering and checklist building run.
 */
class PrescriptionRepository(
    private val prescriptionDao: PrescriptionDao,
    private val nowProvider: () -> LocalDateTime,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default
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
            validFrom = historyStart(prescription.schedule.startDate, nowProvider())
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
        extendHistoryToStartDate(prescription.id, prescription.schedule.startDate, closedAt)
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
        }.flowOn(workDispatcher)
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
        }.flowOn(workDispatcher)
    }

    override fun getDoseChecklistForDate(date: LocalDate): Flow<List<DoseChecklistItem>> {
        return combine(
            prescriptionDao.getSnapshotsOverlapping(
                rangeStart = date.atStartOfDay(),
                rangeEnd = date.atTime(LocalTime.MAX)
            ),
            prescriptionDao.getDoseIntakeRecordsForDate(date)
        ) { snapshots, intakeRecords ->
            buildDoseChecklistForDate(
                date = date,
                snapshots = snapshots,
                intakeRecords = intakeRecords
            )
        }.flowOn(workDispatcher)
    }

    override suspend fun getDoseChecklistBetween(
        start: LocalDateTime,
        end: LocalDateTime
    ): List<DoseChecklistItem> = withContext(workDispatcher) {
        if (end < start) {
            return@withContext emptyList()
        }

        val startDate = start.toLocalDate()
        val endDate = end.toLocalDate()
        val snapshots = prescriptionDao.getSnapshotsOverlappingOnce(
            rangeStart = startDate.atStartOfDay(),
            rangeEnd = endDate.atTime(LocalTime.MAX)
        )
        val intakeByDate = prescriptionDao.getDoseIntakeRecordsBetween(startDate, endDate)
            .groupBy { it.scheduledDate }

        generateSequence(startDate) { previous ->
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
            prescriptionDao.getSnapshotsOverlapping(
                rangeStart = startDate.atStartOfDay(),
                rangeEnd = endDate.atTime(LocalTime.MAX)
            ),
            prescriptionDao.getTakenSlotsBetween(startDate, endDate)
        ) { snapshots, takenSlots ->
            val takenKeys = takenSlots.mapTo(mutableSetOf()) { it.toSlotKey() }

            generateSequence(startDate) { previous ->
                val next = previous.plusDays(1)
                next.takeIf { it <= endDate }
            }.mapNotNull { date ->
                buildDayProgress(
                    date = date,
                    snapshots = snapshots,
                    takenKeys = takenKeys
                )
            }.toList()
        }.flowOn(workDispatcher)
    }

    override suspend fun setDoseTaken(
        snapshotId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        taken: Boolean,
        takenAt: LocalDateTime?
    ) {
        val normalizedTakenAt = if (taken) (takenAt ?: nowProvider()) else null
        prescriptionDao.upsertDoseIntakeRecord(
            intakeRecordFor(
                snapshotId = snapshotId,
                scheduledDate = scheduledDate,
                scheduledTime = scheduledTime,
                taken = taken,
                takenAt = normalizedTakenAt
            )
        )
    }

    override suspend fun setDosesTaken(doses: List<DoseChecklistItem>, taken: Boolean) {
        if (doses.isEmpty()) {
            return
        }

        val normalizedTakenAt = if (taken) nowProvider() else null
        val records = doses.map { dose ->
            intakeRecordFor(
                snapshotId = dose.snapshotId,
                scheduledDate = dose.scheduledDate,
                scheduledTime = dose.scheduledTime,
                taken = taken,
                takenAt = normalizedTakenAt
            )
        }
        prescriptionDao.upsertDoseIntakeRecords(records)
    }

    override suspend fun backfillTakenDoses(
        prescriptionId: Long,
        until: LocalDateTime
    ): Int = withContext(workDispatcher) {
        val historyStart = prescriptionDao.getEarliestSnapshotForPrescription(prescriptionId)
            ?.validFrom
            ?: return@withContext 0
        if (until < historyStart) {
            return@withContext 0
        }

        val doses = getDoseChecklistBetween(historyStart, until)
            .filter { dose -> dose.prescriptionId == prescriptionId }
        if (doses.isEmpty()) {
            return@withContext 0
        }

        val recorded = prescriptionDao
            .getDoseIntakeRecordsBetween(historyStart.toLocalDate(), until.toLocalDate())
            .mapTo(mutableSetOf()) { record ->
                DoseSlotKey(record.snapshotId, record.scheduledDate, record.scheduledTime)
            }

        val records = doses
            .filterNot { dose ->
                DoseSlotKey(dose.snapshotId, dose.scheduledDate, dose.scheduledTime) in recorded
            }
            .map { dose ->
                DoseIntakeRecord(
                    snapshotId = dose.snapshotId,
                    scheduledDate = dose.scheduledDate,
                    scheduledTime = dose.scheduledTime,
                    taken = true,
                    takenAt = dose.scheduledDateTime
                )
            }
        prescriptionDao.upsertDoseIntakeRecords(records)
        records.size
    }

    private fun historyStart(startDate: LocalDate, now: LocalDateTime): LocalDateTime =
        if (startDate < now.toLocalDate()) startDate.atStartOfDay() else now

    private suspend fun extendHistoryToStartDate(
        prescriptionId: Long,
        startDate: LocalDate,
        now: LocalDateTime
    ) {
        val historyStart = historyStart(startDate, now)
        val earliest = prescriptionDao.getEarliestSnapshotForPrescription(prescriptionId) ?: return
        if (historyStart < earliest.validFrom) {
            prescriptionDao.backdatePrescriptionSnapshot(earliest.id, historyStart)
        }
    }

    /**
     * Builds the row to upsert for one slot, carrying over the existing row's id when there is one.
     */
    private suspend fun intakeRecordFor(
        snapshotId: Long,
        scheduledDate: LocalDate,
        scheduledTime: LocalTime,
        taken: Boolean,
        takenAt: LocalDateTime?
    ): DoseIntakeRecord {
        val existing = prescriptionDao.getDoseIntakeRecord(snapshotId, scheduledDate, scheduledTime)
        return existing?.copy(taken = taken, takenAt = takenAt)
            ?: DoseIntakeRecord(
                snapshotId = snapshotId,
                scheduledDate = scheduledDate,
                scheduledTime = scheduledTime,
                taken = taken,
                takenAt = takenAt
            )
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
        val intakeBySlot = intakeRecords.associateBy {
            DoseSlotKey(
                snapshotId = it.snapshotId,
                date = it.scheduledDate,
                time = it.scheduledTime
            )
        }

        return resolveSlotsForDate(date = date, snapshots = snapshots)
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
     * Counts [date]'s slots and how many of them were taken, or `null` when nothing is due.
     */
    private fun buildDayProgress(
        date: LocalDate,
        snapshots: List<PrescriptionSnapshot>,
        takenKeys: Set<DoseSlotKey>
    ): DoseDayProgress? {
        val slots = resolveSlotsForDate(date = date, snapshots = snapshots)
        if (slots.isEmpty()) {
            return null
        }

        return DoseDayProgress(
            date = date,
            doseCount = slots.size,
            takenCount = slots.count { candidate ->
                DoseSlotKey(
                    snapshotId = candidate.snapshot.id,
                    date = date,
                    time = candidate.time
                ) in takenKeys
            }
        )
    }

    /**
     * Works out which snapshot renders each dose slot on [date], one entry per slot.
     */
    private fun resolveSlotsForDate(
        date: LocalDate,
        snapshots: List<PrescriptionSnapshot>
    ): List<SlotCandidate> {
        val dayStart = date.atStartOfDay()
        val dayEnd = date.atTime(LocalTime.MAX)

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

    private fun TakenSlot.toSlotKey(): DoseSlotKey = DoseSlotKey(
        snapshotId = snapshotId,
        date = scheduledDate,
        time = scheduledTime
    )

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
