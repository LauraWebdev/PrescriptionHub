package media.laura.prescriptionhub

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import media.laura.prescriptionhub.data.repository.PrescriptionRepository
import media.laura.prescriptionhub.reminder.DoseReminderScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class DoseReminderSchedulerTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val date = LocalDate.of(2026, 8, 19)
    private var now = LocalDateTime.of(2026, 8, 19, 7, 0)

    private lateinit var context: Context
    private lateinit var database: PrescriptionDatabase
    private lateinit var repository: PrescriptionRepository
    private lateinit var scheduler: DoseReminderScheduler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PrescriptionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PrescriptionRepository(database.prescriptionDao(), nowProvider = { now })
        scheduler = DoseReminderScheduler(
            context = context,
            serviceProvider = { repository },
            zoneId = zone,
            nowProvider = { now }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aDoseWithAReminderGetsAnAlarmAtItsLeadTime() = runBlocking {
        addPrescription("Metformin", LocalTime.of(8, 0), lead = 10)

        scheduler.rearmWindow()

        assertNotNull(alarmAt(LocalDateTime.of(2026, 8, 19, 7, 50)))
    }

    @Test
    fun aDoseWithoutAReminderGetsNoAlarm() = runBlocking {
        addPrescription("Metformin", LocalTime.of(8, 0), lead = null)

        scheduler.rearmWindow()

        assertTrue(doseAlarmTriggerTimes().isEmpty())
    }

    @Test
    fun everyTimeOfDayInTheWindowGetsItsOwnAlarm() = runBlocking {
        addPrescription("Metformin", LocalTime.of(8, 0), LocalTime.of(20, 0), lead = 5)

        scheduler.rearmWindow()

        val triggers = doseAlarmTriggerTimes()
        assertTrue(triggers.contains(LocalDateTime.of(2026, 8, 19, 7, 55)))
        assertTrue(triggers.contains(LocalDateTime.of(2026, 8, 19, 19, 55)))
        assertTrue(triggers.contains(LocalDateTime.of(2026, 8, 20, 7, 55)))
    }

    @Test
    fun rearmingTwiceDoesNotDuplicateAnAlarm() = runBlocking {
        addPrescription("Metformin", LocalTime.of(8, 0), lead = 10)

        scheduler.rearmWindow()
        val afterFirst = doseAlarmTriggerTimes()
        scheduler.rearmWindow()
        val afterSecond = doseAlarmTriggerTimes()

        assertEquals(afterFirst, afterSecond)
    }

    @Test
    fun aTakenDoseLosesItsAlarm() = runBlocking {
        val id = addPrescription("Metformin", LocalTime.of(8, 0), lead = 10)
        scheduler.rearmWindow()
        assertNotNull(alarmAt(LocalDateTime.of(2026, 8, 19, 7, 50)))

        val snapshot = database.prescriptionDao().getOpenSnapshotForPrescription(id)!!
        repository.setDoseTaken(snapshot.id, date, LocalTime.of(8, 0), taken = true)
        scheduler.rearmWindow()

        assertTrue(
            "the reminder for a taken dose should be gone",
            alarmAt(LocalDateTime.of(2026, 8, 19, 7, 50)) == null
        )
    }

    @Test
    fun aDeletedPrescriptionLosesItsAlarm() = runBlocking {
        val id = addPrescription("Metformin", LocalTime.of(8, 0), lead = 10)
        scheduler.rearmWindow()

        repository.deletePrescriptionById(id)
        scheduler.rearmWindow()

        assertTrue(alarmAt(LocalDateTime.of(2026, 8, 19, 7, 50)) == null)
    }

    @Test
    fun theHorizonRefreshIsArmedForTheSmallHours() = runBlocking {
        scheduler.rearmWindow()

        assertNotNull(alarmAt(LocalDateTime.of(2026, 8, 20, 3, 0)))
    }

    private suspend fun addPrescription(
        name: String,
        vararg times: LocalTime,
        lead: Int?
    ): Long = repository.addPrescription(
        Prescription(
            name = name,
            color = 0xFF4CAF50,
            dosis = "850mg",
            schedule = Schedule(
                scheduleType = ScheduleType.DAILY,
                timesOfDay = times.toList(),
                startDate = date,
                reminderLeadMinutes = lead
            )
        )
    )

    private fun alarmManager(): AlarmManager = context.getSystemService(AlarmManager::class.java)

    private fun scheduledTriggerTimes(): List<LocalDateTime> =
        shadowOf(alarmManager()).scheduledAlarms.map { alarm ->
            LocalDateTime.ofInstant(Instant.ofEpochMilli(alarm.triggerAtMs), zone)
        }

    private fun doseAlarmTriggerTimes(): List<LocalDateTime> =
        scheduledTriggerTimes().filterNot { it.hour == 3 && it.minute == 0 }

    private fun alarmAt(triggerAt: LocalDateTime): LocalDateTime? =
        scheduledTriggerTimes().firstOrNull { it == triggerAt }
}
