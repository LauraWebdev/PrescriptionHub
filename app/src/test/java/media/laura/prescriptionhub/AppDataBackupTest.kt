package media.laura.prescriptionhub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import media.laura.prescriptionhub.data.backup.AppDataBackup
import media.laura.prescriptionhub.data.backup.BackupJson
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.model.DoseIntakeRecord
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class AppDataBackupTest {

    private val zone: ZoneId = ZoneId.of("Europe/Berlin")
    private val date = LocalDate.of(2026, 8, 19)
    private val now = LocalDateTime.of(2026, 8, 19, 7, 0)

    private lateinit var context: Context
    private lateinit var database: PrescriptionDatabase
    private lateinit var repository: PrescriptionRepository
    private lateinit var backup: AppDataBackup

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PrescriptionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PrescriptionRepository(database.prescriptionDao(), nowProvider = { now })
        val scheduler = DoseReminderScheduler(
            context = context,
            serviceProvider = { repository },
            zoneId = zone,
            nowProvider = { now }
        )
        backup = AppDataBackup(
            context = context,
            database = database,
            appDataWiper = AppDataWiper(
                context = context,
                database = database,
                reminderScheduler = scheduler,
                workDispatcher = Dispatchers.Unconfined
            ),
            reminderScheduler = scheduler,
            appVersion = "1.2",
            nowProvider = { now },
            workDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportCarriesEveryPrescriptionSnapshotAndDoseRecord() = runBlocking {
        seedTwoPrescriptions()

        val data = backup.export()

        assertEquals(now, data.exportedAt)
        assertEquals("1.2", data.appVersion)
        assertEquals(listOf("Metformin", "Vitamin D"), data.prescriptions.map { it.name })
        assertEquals(2, data.snapshots.size)
        assertEquals(1, data.doseIntakeRecords.size)
        assertTrue(data.doseIntakeRecords.single().taken)
    }

    @Test
    fun importingAnExportOfADifferentDatasetReplacesWhatIsStored() = runBlocking {
        seedTwoPrescriptions()
        val original = backup.export()

        // Stand in for a second device: a database holding something else entirely.
        database.clearAllTables()
        repository.addPrescription(prescription(name = "Ibuprofen", time = LocalTime.of(9, 0)))

        val summary = backup.restore(original)

        assertEquals(2, summary.prescriptionCount)
        assertEquals(1, summary.doseRecordCount)
        assertEquals(original.prescriptions, database.prescriptionDao().getAllPrescriptionsOnce())
        assertEquals(original.snapshots, database.prescriptionDao().getAllSnapshotsOnce())
        assertEquals(
            original.doseIntakeRecords,
            database.prescriptionDao().getAllDoseIntakeRecordsOnce()
        )
    }

    @Test
    fun aFullRoundTripThroughJsonRestoresTheSameRows() = runBlocking {
        seedTwoPrescriptions()
        val original = backup.export()

        backup.restore(BackupJson.decode(BackupJson.encode(original)))

        assertEquals(original, backup.export())
    }

    @Test
    fun doseRecordsWithoutTheirSnapshotAreDropped() = runBlocking {
        seedTwoPrescriptions()
        val original = backup.export()
        val orphaned = original.copy(
            doseIntakeRecords = original.doseIntakeRecords + DoseIntakeRecord(
                id = 9_000,
                snapshotId = 9_999,
                scheduledDate = date,
                scheduledTime = LocalTime.of(6, 0),
                taken = true,
                takenAt = now
            )
        )

        val summary = backup.restore(orphaned)

        assertEquals(1, summary.doseRecordCount)
        assertEquals(
            original.doseIntakeRecords,
            database.prescriptionDao().getAllDoseIntakeRecordsOnce()
        )
    }

    @Test
    fun aPrescriptionAddedAfterAnImportDoesNotReuseAnImportedId() = runBlocking {
        seedTwoPrescriptions()
        val original = backup.export()
        backup.restore(original)

        val newId = repository.addPrescription(
            prescription(name = "Ibuprofen", time = LocalTime.of(9, 0))
        )

        assertTrue(newId > original.prescriptions.maxOf { it.id })
    }

    @Test
    fun importingAnEmptyBackupLeavesNothingBehind() = runBlocking {
        seedTwoPrescriptions()
        val empty = backup.export().copy(
            prescriptions = emptyList(),
            snapshots = emptyList(),
            doseIntakeRecords = emptyList()
        )

        val summary = backup.restore(empty)

        assertEquals(0, summary.prescriptionCount)
        assertEquals(0, summary.doseRecordCount)
        assertTrue(database.prescriptionDao().getAllPrescriptionsOnce().isEmpty())
        assertTrue(database.prescriptionDao().getAllSnapshotsOnce().isEmpty())
        assertTrue(database.prescriptionDao().getAllDoseIntakeRecordsOnce().isEmpty())
    }

    private suspend fun seedTwoPrescriptions() {
        val metforminId = repository.addPrescription(
            prescription(name = "Metformin", time = LocalTime.of(8, 0), lead = 10)
        )
        repository.addPrescription(prescription(name = "Vitamin D", time = LocalTime.of(12, 15)))

        repository.setDoseTaken(
            snapshotId = database.prescriptionDao()
                .getOpenSnapshotForPrescription(metforminId)!!.id,
            scheduledDate = date,
            scheduledTime = LocalTime.of(8, 0),
            taken = true
        )
    }

    private fun prescription(name: String, time: LocalTime, lead: Int? = null) = Prescription(
        name = name,
        color = 0xFF4CAF50,
        dosis = "850mg",
        schedule = Schedule(
            scheduleType = ScheduleType.DAILY,
            timesOfDay = listOf(time),
            startDate = date,
            reminderLeadMinutes = lead
        )
    )
}
