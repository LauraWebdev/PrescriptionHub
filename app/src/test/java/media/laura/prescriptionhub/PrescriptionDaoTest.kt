package media.laura.prescriptionhub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import media.laura.prescriptionhub.data.local.PrescriptionDao
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.model.DoseIntakeRecord
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.PrescriptionSnapshot
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class PrescriptionDaoTest {
    private val anyStartDate = LocalDate.of(2026, 8, 19)

    private lateinit var database: PrescriptionDatabase
    private lateinit var dao: PrescriptionDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PrescriptionDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.prescriptionDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun testInsertAndGetPrescriptionDaily() = runBlocking {
        val prescription = Prescription(
            name = "Aspirin",
            color = 0xFFFF0000,
            dosis = "100mg",
            schedule = Schedule(
                scheduleType = ScheduleType.DAILY,
                timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(23, 0)),
                startDate = anyStartDate
            )
        )

        val id = dao.insertPrescription(prescription)
        assertTrue(id > 0)

        val retrieved = dao.getPrescriptionByIdOnce(id)
        assertNotNull(retrieved)
        assertEquals("Aspirin", retrieved?.name)
        assertEquals(0xFFFF0000, retrieved?.color)
        assertEquals("100mg", retrieved?.dosis)
        assertEquals(ScheduleType.DAILY, retrieved?.schedule?.scheduleType)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(23, 0)), retrieved?.schedule?.timesOfDay)
    }

    @Test
    fun testInsertAndGetPrescriptionSpecificDays() = runBlocking {
        val prescription = Prescription(
            name = "Amoxicillin",
            color = 0xFF00FF00,
            dosis = "500mg",
            schedule = Schedule(
                scheduleType = ScheduleType.SPECIFIC_DAYS_OF_WEEK,
                daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                timesOfDay = listOf(LocalTime.of(13, 0), LocalTime.MIDNIGHT),
                startDate = anyStartDate
            )
        )

        val id = dao.insertPrescription(prescription)
        val fromFlow = dao.getPrescriptionById(id).first()
        assertNotNull(fromFlow)
        assertEquals("Amoxicillin", fromFlow?.name)
        assertEquals(ScheduleType.SPECIFIC_DAYS_OF_WEEK, fromFlow?.schedule?.scheduleType)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), fromFlow?.schedule?.daysOfWeek)
        assertEquals(listOf(LocalTime.of(13, 0), LocalTime.MIDNIGHT), fromFlow?.schedule?.timesOfDay)
    }

    @Test
    fun testInsertAndGetPrescriptionEveryXDays() = runBlocking {
        val startDate = LocalDate.of(2026, 8, 1)
        val prescription = Prescription(
            name = "Vitamin D",
            color = 0xFFFFFF00,
            dosis = "1000 IU",
            schedule = Schedule(
                scheduleType = ScheduleType.EVERY_X_DAYS,
                everyXDays = 2,
                timesOfDay = listOf(LocalTime.of(14, 0)),
                startDate = startDate
            )
        )

        val id = dao.insertPrescription(prescription)
        val retrieved = dao.getPrescriptionByIdOnce(id)
        assertNotNull(retrieved)
        assertEquals(ScheduleType.EVERY_X_DAYS, retrieved?.schedule?.scheduleType)
        assertEquals(2, retrieved?.schedule?.everyXDays)
        assertEquals(startDate, retrieved?.schedule?.startDate)
        assertEquals(listOf(LocalTime.of(14, 0)), retrieved?.schedule?.timesOfDay)
    }

    @Test
    fun testUpdatePrescription() = runBlocking {
        val original = Prescription(
            name = "Ibuprofen",
            color = 0xFF0000FF,
            dosis = "200mg",
            schedule = Schedule(scheduleType = ScheduleType.DAILY, startDate = anyStartDate)
        )
        val id = dao.insertPrescription(original)

        val updated = original.copy(
            id = id,
            name = "Ibuprofen Extra",
            dosis = "400mg"
        )
        dao.updatePrescription(updated)

        val retrieved = dao.getPrescriptionByIdOnce(id)
        assertEquals("Ibuprofen Extra", retrieved?.name)
        assertEquals("400mg", retrieved?.dosis)
    }

    @Test
    fun testDeletePrescription() = runBlocking {
        val prescription = Prescription(
            name = "Paracetamol",
            color = 0xFFFFFFFF,
            dosis = "500mg",
            schedule = Schedule(scheduleType = ScheduleType.DAILY, startDate = anyStartDate)
        )
        val id = dao.insertPrescription(prescription)
        val saved = dao.getPrescriptionByIdOnce(id)!!

        dao.deletePrescription(saved)
        assertNull(dao.getPrescriptionByIdOnce(id))
    }

    @Test
    fun testDeletePrescriptionById() = runBlocking {
        val prescription = Prescription(
            name = "Paracetamol",
            color = 0xFFFFFFFF,
            dosis = "500mg",
            schedule = Schedule(scheduleType = ScheduleType.DAILY, startDate = anyStartDate)
        )
        val id = dao.insertPrescription(prescription)
        dao.deletePrescriptionById(id)
        assertNull(dao.getPrescriptionByIdOnce(id))
    }

    @Test
    fun overlappingSnapshotsCoverOpenAndClosedValidityWindows() = runBlocking {
        val day = LocalDate.of(2026, 8, 19)

        // Ended the day before the one we ask about.
        val expired = insertSnapshot(
            name = "Expired",
            validFrom = LocalDateTime.of(2026, 8, 10, 9, 0),
            validTo = LocalDateTime.of(2026, 8, 18, 9, 0)
        )
        // Ended during the day.
        val closedThatDay = insertSnapshot(
            name = "Closed that day",
            validFrom = LocalDateTime.of(2026, 8, 10, 9, 0),
            validTo = LocalDateTime.of(2026, 8, 19, 12, 0)
        )
        // Still open.
        val open = insertSnapshot(
            name = "Open",
            validFrom = LocalDateTime.of(2026, 8, 10, 9, 0),
            validTo = null
        )
        // Opens only the day after.
        val future = insertSnapshot(
            name = "Future",
            validFrom = LocalDateTime.of(2026, 8, 20, 9, 0),
            validTo = null
        )

        val found = dao.getSnapshotsOverlapping(
            rangeStart = day.atStartOfDay(),
            rangeEnd = day.atTime(LocalTime.MAX)
        ).first()

        assertEquals(listOf(closedThatDay, open), found.map { it.id })
        assertTrue(expired !in found.map { it.id })
        assertTrue(future !in found.map { it.id })
    }

    @Test
    fun overlappingSnapshotsMatchATimestampStoredWithoutSeconds() = runBlocking {
        val day = LocalDate.of(2026, 8, 19)
        val id = insertSnapshot(
            name = "Zero seconds",
            validFrom = LocalDateTime.of(2026, 8, 19, 8, 0),
            validTo = null
        )

        val found = dao.getSnapshotsOverlapping(
            rangeStart = day.atStartOfDay(),
            rangeEnd = day.atTime(LocalTime.MAX)
        ).first()
        assertEquals(listOf(id), found.map { it.id })

        // And it must not leak into the day before it began.
        val dayBefore = dao.getSnapshotsOverlapping(
            rangeStart = day.minusDays(1).atStartOfDay(),
            rangeEnd = day.minusDays(1).atTime(LocalTime.MAX)
        ).first()
        assertTrue(dayBefore.isEmpty())
    }

    @Test
    fun overlappingSnapshotsReadOnceMatchTheObservedQuery() = runBlocking {
        val day = LocalDate.of(2026, 8, 19)
        insertSnapshot(
            name = "Open",
            validFrom = LocalDateTime.of(2026, 8, 19, 8, 0),
            validTo = null
        )

        val observed = dao.getSnapshotsOverlapping(day.atStartOfDay(), day.atTime(LocalTime.MAX)).first()
        val once = dao.getSnapshotsOverlappingOnce(day.atStartOfDay(), day.atTime(LocalTime.MAX))

        assertEquals(observed, once)
    }

    @Test
    fun takenSlotsBetweenReportsOnlyTickedRowsInRange() = runBlocking {
        val snapshotId = insertSnapshot(
            name = "Metformin",
            validFrom = LocalDateTime.of(2026, 8, 1, 8, 0),
            validTo = null
        )
        val inRange = LocalDate.of(2026, 8, 19)

        dao.upsertDoseIntakeRecord(
            DoseIntakeRecord(
                snapshotId = snapshotId,
                scheduledDate = inRange,
                scheduledTime = LocalTime.of(8, 0),
                taken = true,
                takenAt = inRange.atTime(8, 3)
            )
        )
        // Un-ticking leaves the row behind with taken = 0; it must not be counted.
        dao.upsertDoseIntakeRecord(
            DoseIntakeRecord(
                snapshotId = snapshotId,
                scheduledDate = inRange,
                scheduledTime = LocalTime.of(20, 0),
                taken = false,
                takenAt = null
            )
        )
        // Outside the range.
        dao.upsertDoseIntakeRecord(
            DoseIntakeRecord(
                snapshotId = snapshotId,
                scheduledDate = LocalDate.of(2026, 8, 25),
                scheduledTime = LocalTime.of(8, 0),
                taken = true,
                takenAt = LocalDate.of(2026, 8, 25).atTime(8, 1)
            )
        )

        val slots = dao.getTakenSlotsBetween(
            startDate = LocalDate.of(2026, 8, 18),
            endDate = LocalDate.of(2026, 8, 20)
        ).first()

        assertEquals(1, slots.size)
        assertEquals(snapshotId, slots.single().snapshotId)
        assertEquals(inRange, slots.single().scheduledDate)
        assertEquals(LocalTime.of(8, 0), slots.single().scheduledTime)
    }

    private suspend fun insertSnapshot(
        name: String,
        validFrom: LocalDateTime,
        validTo: LocalDateTime?
    ): Long = dao.insertPrescriptionSnapshot(
        PrescriptionSnapshot(
            prescriptionId = 1L,
            name = name,
            color = 0xFF102030,
            dosis = "1 pill",
            schedule = Schedule(
                scheduleType = ScheduleType.DAILY,
                timesOfDay = listOf(LocalTime.of(8, 0)),
                startDate = LocalDate.of(2026, 8, 1)
            ),
            validFrom = validFrom,
            validTo = validTo
        )
    )

    @Test
    fun testGetAllPrescriptions() = runBlocking {
        val p1 = Prescription(
            name = "Med 1",
            color = 0x1,
            dosis = "10mg",
            schedule = Schedule(startDate = anyStartDate)
        )
        val p2 = Prescription(
            name = "Med 2",
            color = 0x2,
            dosis = "20mg",
            schedule = Schedule(startDate = anyStartDate)
        )
        dao.insertPrescriptions(listOf(p1, p2))

        val list = dao.getAllPrescriptions().first()
        assertEquals(2, list.size)
        assertEquals("Med 1", list[0].name)
        assertEquals("Med 2", list[1].name)

        dao.deleteAllPrescriptions()
        val emptyList = dao.getAllPrescriptions().first()
        assertTrue(emptyList.isEmpty())
    }
}
