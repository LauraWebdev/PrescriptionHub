package media.laura.prescriptionhub

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import media.laura.prescriptionhub.data.repository.AppDataWiper
import media.laura.prescriptionhub.data.repository.PrescriptionRepository
import media.laura.prescriptionhub.reminder.DoseReminderScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class AppDataWiperTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val date = LocalDate.of(2026, 8, 19)
    private val now = LocalDateTime.of(2026, 8, 19, 7, 0)

    private lateinit var context: Context
    private lateinit var database: PrescriptionDatabase
    private lateinit var repository: PrescriptionRepository
    private lateinit var scheduler: DoseReminderScheduler
    private lateinit var wiper: AppDataWiper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PrescriptionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PrescriptionRepository(database.prescriptionDao(), nowProvider = { now })
        scheduler = newScheduler()
        wiper = newWiper(scheduler)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun wipingRemovesEveryPrescriptionSnapshotAndDoseRecord() = runBlocking {
        val id = addPrescription(LocalTime.of(8, 0), lead = 10)
        repository.setDoseTaken(
            snapshotId = database.prescriptionDao().getOpenSnapshotForPrescription(id)!!.id,
            scheduledDate = date,
            scheduledTime = LocalTime.of(8, 0),
            taken = true
        )
        assertTrue(prescriptionCount() > 0)
        assertTrue(snapshotCount() > 0)
        assertTrue(doseRecordCount() > 0)

        wiper.wipe()

        assertEquals(0, prescriptionCount())
        assertEquals(0, snapshotCount())
        assertEquals(0, doseRecordCount())
    }

    @Test
    fun wipingCancelsEveryAlarmIncludingTheHorizonRefresh() = runBlocking {
        addPrescription(LocalTime.of(8, 0), LocalTime.of(20, 0), lead = 10)
        scheduler.rearmWindow()
        assertTrue(scheduledAlarmCount() > 0)

        wiper.wipe()

        assertEquals(0, scheduledAlarmCount())
    }

    @Test
    fun wipingCancelsAlarmsThisProcessDidNotArm() = runBlocking {
        addPrescription(LocalTime.of(8, 0), LocalTime.of(20, 0), lead = 10)
        scheduler.rearmWindow()
        assertTrue(scheduledAlarmCount() > 0)

        // A fresh scheduler stands in for a restarted process: the alarms are still pending
        // with the system, but nothing is tracked in memory any more.
        val restarted = newScheduler()
        newWiper(restarted).wipe()

        assertEquals(0, scheduledAlarmCount())
    }

    private fun newScheduler(): DoseReminderScheduler = DoseReminderScheduler(
        context = context,
        serviceProvider = { repository },
        zoneId = zone,
        nowProvider = { now }
    )

    private fun newWiper(scheduler: DoseReminderScheduler): AppDataWiper = AppDataWiper(
        context = context,
        database = database,
        reminderScheduler = scheduler,
        workDispatcher = Dispatchers.Unconfined
    )

    private suspend fun addPrescription(vararg times: LocalTime, lead: Int?): Long =
        repository.addPrescription(
            Prescription(
                name = "Metformin",
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

    private suspend fun prescriptionCount(): Int =
        database.prescriptionDao().getAllPrescriptions().first().size

    private suspend fun snapshotCount(): Int = database.prescriptionDao()
        .getSnapshotsOverlappingOnce(now.minusYears(1), now.plusYears(1))
        .size

    private suspend fun doseRecordCount(): Int = database.prescriptionDao()
        .getDoseIntakeRecordsBetween(date.minusYears(1), date.plusYears(1))
        .size

    private fun scheduledAlarmCount(): Int =
        shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.size
}
