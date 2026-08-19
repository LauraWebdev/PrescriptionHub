package media.laura.prescriptionhub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import media.laura.prescriptionhub.data.local.PrescriptionDao
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.model.Prescription
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
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class PrescriptionDaoTest {

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
                timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(23, 0))
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
                timesOfDay = listOf(LocalTime.of(13, 0), LocalTime.MIDNIGHT)
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
            schedule = Schedule(scheduleType = ScheduleType.DAILY)
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
            schedule = Schedule(scheduleType = ScheduleType.DAILY)
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
            schedule = Schedule(scheduleType = ScheduleType.DAILY)
        )
        val id = dao.insertPrescription(prescription)
        dao.deletePrescriptionById(id)
        assertNull(dao.getPrescriptionByIdOnce(id))
    }

    @Test
    fun testGetAllPrescriptions() = runBlocking {
        val p1 = Prescription(name = "Med 1", color = 0x1, dosis = "10mg")
        val p2 = Prescription(name = "Med 2", color = 0x2, dosis = "20mg")
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
