package media.laura.prescriptionhub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import media.laura.prescriptionhub.data.repository.PrescriptionRepository
import media.laura.prescriptionhub.data.repository.PrescriptionService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class PrescriptionRepositoryTest {

    private lateinit var database: PrescriptionDatabase
    private lateinit var repository: PrescriptionRepository
    private lateinit var service: PrescriptionService

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PrescriptionDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = PrescriptionRepository(database.prescriptionDao())
        service = repository
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAddAndRetrievePrescriptions() = runBlocking {
        val id = service.addPrescription(
            Prescription(
                name = "Omeprazole",
                color = 0xFF123456,
                dosis = "20mg",
                schedule = Schedule(
                    scheduleType = ScheduleType.DAILY,
                    timesOfDay = listOf(LocalTime.of(7, 30))
                )
            )
        )

        val retrieved = service.getPrescription(id)
        assertNotNull(retrieved)
        assertEquals("Omeprazole", retrieved?.name)

        val flowItem = service.getPrescriptionById(id).first()
        assertEquals("Omeprazole", flowItem?.name)

        val all = service.getAllPrescriptions().first()
        assertEquals(1, all.size)
    }

    @Test
    fun testUpdateAndDelete() = runBlocking {
        val id = service.addPrescription(
            Prescription(
                name = "Metformin",
                color = 0xFF654321,
                dosis = "500mg"
            )
        )

        val existing = service.getPrescription(id)!!
        service.updatePrescription(existing.copy(dosis = "850mg"))
        assertEquals("850mg", service.getPrescription(id)?.dosis)

        service.deletePrescriptionById(id)
        assertNull(service.getPrescription(id))
    }

    @Test
    fun testGetPrescriptionsForDate() = runBlocking {
        val dailyMed = Prescription(
            name = "Daily Med",
            color = 0xFF111111,
            dosis = "1 pill",
            schedule = Schedule(
                scheduleType = ScheduleType.DAILY,
                timesOfDay = listOf(LocalTime.of(8, 0))
            )
        )
        val weekdayMed = Prescription(
            name = "Mon-Wed-Fri Med",
            color = 0xFF222222,
            dosis = "10ml",
            schedule = Schedule(
                scheduleType = ScheduleType.SPECIFIC_DAYS_OF_WEEK,
                daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                timesOfDay = listOf(LocalTime.of(12, 0))
            )
        )
        val every2DaysMed = Prescription(
            name = "Every 2 Days Med",
            color = 0xFF333333,
            dosis = "2 drops",
            schedule = Schedule(
                scheduleType = ScheduleType.EVERY_X_DAYS,
                everyXDays = 2,
                startDate = LocalDate.of(2026, 8, 17), // Monday
                timesOfDay = listOf(LocalTime.of(20, 0))
            )
        )

        service.addPrescription(dailyMed)
        service.addPrescription(weekdayMed)
        service.addPrescription(every2DaysMed)

        // Monday 2026-08-17: All three are scheduled!
        val mondayList = service.getPrescriptionsForDate(LocalDate.of(2026, 8, 17)).first()
        assertEquals(3, mondayList.size)

        // Tuesday 2026-08-18: Only Daily Med is scheduled (not Mon-Wed-Fri, and every2Days is day 1 so off)
        val tuesdayList = service.getPrescriptionsForDate(LocalDate.of(2026, 8, 18)).first()
        assertEquals(1, tuesdayList.size)
        assertEquals("Daily Med", tuesdayList[0].name)

        // Wednesday 2026-08-19: All three are scheduled! (Daily, Wednesday in Mon-Wed-Fri, and every2Days day 2)
        val wednesdayList = service.getPrescriptionsForDate(LocalDate.of(2026, 8, 19)).first()
        assertEquals(3, wednesdayList.size)
    }

    @Test
    fun testGetScheduledDosesForDateSorted() = runBlocking {
        val p1 = Prescription(
            name = "Morning & Night Med",
            color = 0xFF111111,
            dosis = "1 pill",
            schedule = Schedule(
                scheduleType = ScheduleType.DAILY,
                timesOfDay = listOf(LocalTime.of(23, 0), LocalTime.of(8, 0))
            )
        )
        val p2 = Prescription(
            name = "Afternoon Med",
            color = 0xFF222222,
            dosis = "5ml",
            schedule = Schedule(
                scheduleType = ScheduleType.DAILY,
                timesOfDay = listOf(LocalTime.of(14, 0))
            )
        )

        service.addPrescription(p1)
        service.addPrescription(p2)

        val doses = service.getScheduledDosesForDate(LocalDate.now()).first()
        assertEquals(3, doses.size)
        assertEquals(LocalTime.of(8, 0), doses[0].time)
        assertEquals("Morning & Night Med", doses[0].prescription.name)

        assertEquals(LocalTime.of(14, 0), doses[1].time)
        assertEquals("Afternoon Med", doses[1].prescription.name)

        assertEquals(LocalTime.of(23, 0), doses[2].time)
        assertEquals("Morning & Night Med", doses[2].prescription.name)
    }

    @Test
    fun testDoseChecklistAndTakenTimestamp() = runBlocking {
        var now = LocalDateTime.of(2026, 8, 19, 7, 0)
        val checklistService = PrescriptionRepository(
            database.prescriptionDao(),
            nowProvider = { now }
        )

        checklistService.addPrescription(
            Prescription(
                name = "Drug A",
                color = 0xFF102030,
                dosis = "100mg",
                schedule = Schedule(
                    scheduleType = ScheduleType.DAILY,
                    timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(16, 0))
                )
            )
        )

        val date = LocalDate.of(2026, 8, 19)
        val initialChecklist = checklistService.getDoseChecklistForDate(date).first()
        assertEquals(2, initialChecklist.size)
        assertFalse(initialChecklist[0].taken)
        assertFalse(initialChecklist[1].taken)

        val firstItem = initialChecklist.first { it.scheduledTime == LocalTime.of(8, 0) }
        val takenAt = date.atTime(8, 4)
        checklistService.setDoseTaken(
            snapshotId = firstItem.snapshotId,
            scheduledDate = date,
            scheduledTime = firstItem.scheduledTime,
            taken = true,
            takenAt = takenAt
        )

        val afterTaken = checklistService.getDoseChecklistForDate(date).first()
        val takenItem = afterTaken.first { it.scheduledTime == LocalTime.of(8, 0) }
        assertTrue(takenItem.taken)
        assertEquals(takenAt, takenItem.takenAt)

        now = LocalDateTime.of(2026, 8, 19, 8, 10)
        checklistService.setDoseTaken(
            snapshotId = firstItem.snapshotId,
            scheduledDate = date,
            scheduledTime = firstItem.scheduledTime,
            taken = true,
            takenAt = date.atTime(8, 5)
        )

        val afterRetake = checklistService.getDoseChecklistForDate(date).first()
        val retakenItem = afterRetake.first { it.scheduledTime == LocalTime.of(8, 0) }
        assertEquals(2, afterRetake.size)
        assertTrue(retakenItem.taken)
        assertEquals(date.atTime(8, 5), retakenItem.takenAt)
    }

    @Test
    fun testChecklistHistoryPreservedAfterPrescriptionUpdate() = runBlocking {
        var now = LocalDateTime.of(2026, 8, 19, 7, 0)
        val historicalService = PrescriptionRepository(
            database.prescriptionDao(),
            nowProvider = { now }
        )

        val prescriptionId = historicalService.addPrescription(
            Prescription(
                name = "Drug A",
                color = 0xFFAA0000,
                dosis = "100mg",
                schedule = Schedule(
                    scheduleType = ScheduleType.DAILY,
                    timesOfDay = listOf(LocalTime.of(8, 0))
                )
            )
        )

        val day = LocalDate.of(2026, 8, 19)
        val beforeUpdate = historicalService.getDoseChecklistForDate(day).first()
        assertEquals(1, beforeUpdate.size)
        assertEquals("Drug A", beforeUpdate[0].prescriptionName)

        historicalService.setDoseTaken(
            snapshotId = beforeUpdate[0].snapshotId,
            scheduledDate = day,
            scheduledTime = LocalTime.of(8, 0),
            taken = true,
            takenAt = LocalDateTime.of(2026, 8, 19, 8, 4)
        )

        now = LocalDateTime.of(2026, 8, 19, 12, 0)
        val existing = historicalService.getPrescription(prescriptionId)!!
        historicalService.updatePrescription(
            existing.copy(
                name = "Drug A Updated",
                dosis = "150mg"
            )
        )

        val sameDay = historicalService.getDoseChecklistForDate(day).first()
        assertEquals(1, sameDay.size)
        assertEquals("Drug A", sameDay[0].prescriptionName)
        assertTrue(sameDay[0].taken)
        assertEquals(LocalDateTime.of(2026, 8, 19, 8, 4), sameDay[0].takenAt)

        val nextDay = historicalService.getDoseChecklistForDate(LocalDate.of(2026, 8, 20)).first()
        assertEquals(1, nextDay.size)
        assertEquals("Drug A Updated", nextDay[0].prescriptionName)
        assertFalse(nextDay[0].taken)
        assertNull(nextDay[0].takenAt)
    }

    @Test
    fun testChecklistHistoryPreservedAfterPrescriptionDeletion() = runBlocking {
        var now = LocalDateTime.of(2026, 8, 19, 7, 0)
        val historicalService = PrescriptionRepository(
            database.prescriptionDao(),
            nowProvider = { now }
        )

        val prescriptionId = historicalService.addPrescription(
            Prescription(
                name = "Drug B",
                color = 0xFF00AA00,
                dosis = "1 pill",
                schedule = Schedule(
                    scheduleType = ScheduleType.DAILY,
                    timesOfDay = listOf(LocalTime.of(8, 0))
                )
            )
        )

        val day = LocalDate.of(2026, 8, 19)
        val checklist = historicalService.getDoseChecklistForDate(day).first()
        historicalService.setDoseTaken(
            snapshotId = checklist[0].snapshotId,
            scheduledDate = day,
            scheduledTime = LocalTime.of(8, 0),
            taken = true,
            takenAt = LocalDateTime.of(2026, 8, 19, 8, 2)
        )

        now = LocalDateTime.of(2026, 8, 19, 18, 0)
        historicalService.deletePrescriptionById(prescriptionId)

        val sameDay = historicalService.getDoseChecklistForDate(day).first()
        assertEquals(1, sameDay.size)
        assertEquals("Drug B", sameDay[0].prescriptionName)
        assertTrue(sameDay[0].taken)

        val nextDay = historicalService.getDoseChecklistForDate(LocalDate.of(2026, 8, 20)).first()
        assertTrue(nextDay.isEmpty())
    }
}
