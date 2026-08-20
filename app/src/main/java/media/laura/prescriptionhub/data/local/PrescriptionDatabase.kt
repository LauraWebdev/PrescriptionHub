package media.laura.prescriptionhub.data.local

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.DoseIntakeRecord
import media.laura.prescriptionhub.data.model.PrescriptionSnapshot

/**
 * Main Room Database configuration for PrescriptionHub.
 */
@Database(
    entities = [
        Prescription::class,
        PrescriptionSnapshot::class,
        DoseIntakeRecord::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PrescriptionDatabase : RoomDatabase() {

    abstract fun prescriptionDao(): PrescriptionDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                        `validTo` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_prescription_snapshots_prescriptionId` ON `prescription_snapshots` (`prescriptionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_prescription_snapshots_validFrom` ON `prescription_snapshots` (`validFrom`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_prescription_snapshots_validTo` ON `prescription_snapshots` (`validTo`)")

                db.execSQL(
                    """
                    INSERT INTO `prescription_snapshots` (
                        `prescriptionId`,
                        `name`,
                        `color`,
                        `dosis`,
                        `schedule_scheduleType`,
                        `schedule_daysOfWeek`,
                        `schedule_everyXDays`,
                        `schedule_timesOfDay`,
                        `schedule_startDate`,
                        `validFrom`,
                        `validTo`
                    )
                    SELECT
                        `id`,
                        `name`,
                        `color`,
                        `dosis`,
                        COALESCE(`scheduleType`, 'DAILY'),
                        COALESCE(`daysOfWeek`, ''),
                        `everyXDays`,
                        COALESCE(`timesOfDay`, ''),
                        COALESCE(`startDate`, '1970-01-01'),
                        '1970-01-01T00:00:00',
                        NULL
                    FROM `prescriptions`
                    """.trimIndent()
                )

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
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `prescriptions` ADD COLUMN `reminderLeadMinutes` INTEGER")
                db.execSQL("ALTER TABLE `prescription_snapshots` ADD COLUMN `schedule_reminderLeadMinutes` INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dose_intake_records_scheduledDate` ON `dose_intake_records` (`scheduledDate`)")
            }
        }

        val MIGRATIONS: Array<Migration>
            get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        @Volatile
        private var INSTANCE: PrescriptionDatabase? = null

        fun getDatabase(context: Context): PrescriptionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PrescriptionDatabase::class.java,
                    "prescription_database"
                ).addMigrations(*MIGRATIONS).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
