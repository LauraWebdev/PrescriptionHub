package media.laura.prescriptionhub

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class PrescriptionDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: PrescriptionDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.getDatabasePath(DB_NAME).delete()
    }

    @Test
    fun theScheduledDateIndexExistsOnAFreshDatabase() {
        val db = Room.inMemoryDatabaseBuilder(context, PrescriptionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

        assertTrue(INDEX_NAME in indicesOnIntakeRecords(db))
    }

    @Test
    fun aVersion3DatabaseMigratesAndKeepsItsRows() = runBlocking {
        createVersion3Database()

        val db = Room.databaseBuilder(context, PrescriptionDatabase::class.java, DB_NAME)
            .addMigrations(*PrescriptionDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

        // Opening runs MIGRATION_3_4 and then makes Room validate the resulting schema.
        val prescriptions = db.prescriptionDao().getAllPrescriptions().first()

        assertEquals(1, prescriptions.size)
        assertEquals("Metformin", prescriptions.single().name)
        assertTrue(INDEX_NAME in indicesOnIntakeRecords(db))
    }

    @Test
    fun theMigratedDatabaseStillAcceptsWrites() = runBlocking {
        createVersion3Database()

        val db = Room.databaseBuilder(context, PrescriptionDatabase::class.java, DB_NAME)
            .addMigrations(*PrescriptionDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
            .also { database = it }

        db.prescriptionDao().insertPrescription(
            Prescription(
                name = "Candecor",
                color = 0xFF102030,
                dosis = "8mg",
                schedule = Schedule(
                    scheduleType = ScheduleType.DAILY,
                    timesOfDay = listOf(LocalTime.of(8, 0)),
                    startDate = LocalDate.of(2026, 8, 19)
                )
            )
        )

        assertEquals(2, db.prescriptionDao().getAllPrescriptions().first().size)
    }

    private fun indicesOnIntakeRecords(db: PrescriptionDatabase): List<String> {
        val names = mutableListOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'dose_intake_records'",
            emptyArray()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0))
            }
        }
        return names
    }

    /**
     * Recreates the schema a version 3 install carries and seeds one row so the migration has data to preserve.
     */
    private fun createVersion3Database() {
        val path: File = context.getDatabasePath(DB_NAME)
        path.parentFile?.mkdirs()
        path.delete()

        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `prescriptions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `color` INTEGER NOT NULL,
                    `dosis` TEXT NOT NULL,
                    `scheduleType` TEXT NOT NULL,
                    `daysOfWeek` TEXT NOT NULL,
                    `everyXDays` INTEGER,
                    `timesOfDay` TEXT NOT NULL,
                    `startDate` TEXT NOT NULL,
                    `reminderLeadMinutes` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `prescription_snapshots` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `prescriptionId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `color` INTEGER NOT NULL,
                    `dosis` TEXT NOT NULL,
                    `schedule_scheduleType` TEXT NOT NULL,
                    `schedule_daysOfWeek` TEXT NOT NULL,
                    `schedule_everyXDays` INTEGER,
                    `schedule_timesOfDay` TEXT NOT NULL,
                    `schedule_startDate` TEXT NOT NULL,
                    `validFrom` TEXT NOT NULL,
                    `validTo` TEXT,
                    `schedule_reminderLeadMinutes` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prescription_snapshots_prescriptionId` ON `prescription_snapshots` (`prescriptionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prescription_snapshots_validFrom` ON `prescription_snapshots` (`validFrom`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_prescription_snapshots_validTo` ON `prescription_snapshots` (`validTo`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `dose_intake_records` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `snapshotId` INTEGER NOT NULL,
                    `scheduledDate` TEXT NOT NULL,
                    `scheduledTime` TEXT NOT NULL,
                    `taken` INTEGER NOT NULL,
                    `takenAt` TEXT,
                    FOREIGN KEY(`snapshotId`) REFERENCES `prescription_snapshots`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_dose_intake_records_snapshotId` ON `dose_intake_records` (`snapshotId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_dose_intake_records_snapshotId_scheduledDate_scheduledTime` ON `dose_intake_records` (`snapshotId`, `scheduledDate`, `scheduledTime`)")

            db.execSQL(
                """
                INSERT INTO `prescriptions`
                    (`name`, `color`, `dosis`, `scheduleType`, `daysOfWeek`, `everyXDays`, `timesOfDay`, `startDate`, `reminderLeadMinutes`)
                VALUES
                    ('Metformin', 4278190080, '850mg', 'DAILY', '', NULL, '08:00', '2026-08-19', NULL)
                """.trimIndent()
            )

            db.version = 3
        }
    }

    private companion object {
        const val DB_NAME = "migration_test_database"
        const val INDEX_NAME = "index_dose_intake_records_scheduledDate"
    }
}
