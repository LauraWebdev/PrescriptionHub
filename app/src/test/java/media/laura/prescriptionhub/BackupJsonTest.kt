package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.backup.BackupData
import media.laura.prescriptionhub.data.backup.BackupFormatException
import media.laura.prescriptionhub.data.backup.BackupJson
import media.laura.prescriptionhub.data.model.DoseIntakeRecord
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.PrescriptionSnapshot
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class BackupJsonTest {

    private val exportedAt = LocalDateTime.of(2026, 8, 26, 14, 5)

    @Test
    fun aFullDatasetSurvivesTheRoundTrip() {
        val data = sampleData()

        val restored = BackupJson.decode(BackupJson.encode(data))

        assertEquals(data, restored)
    }

    @Test
    fun anEmptyDatasetSurvivesTheRoundTrip() {
        val data = BackupData(exportedAt = exportedAt, appVersion = "1.2")

        val restored = BackupJson.decode(BackupJson.encode(data))

        assertEquals(data, restored)
    }

    @Test
    fun optionalFieldsStayNullThroughTheRoundTrip() {
        val data = sampleData()

        val restored = BackupJson.decode(BackupJson.encode(data))

        val snapshot = restored.snapshots.single { it.validTo == null }
        assertNull(snapshot.schedule.everyXDays)
        assertNull(snapshot.schedule.reminderLeadMinutes)
        assertNull(restored.doseIntakeRecords.single { !it.taken }.takenAt)
    }

    @Test
    fun theDocumentIsTaggedSoOtherFilesCanBeToldApart() {
        val root = JSONObject(BackupJson.encode(sampleData()))

        assertEquals(BackupJson.FORMAT, root.getString("format"))
        assertEquals(BackupJson.VERSION, root.getInt("version"))
        assertEquals("2026-08-26T14:05:00", root.getString("exportedAt"))
    }

    @Test
    fun somethingThatIsNotJsonIsRejected() {
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decode("not a backup at all")
        }
    }

    @Test
    fun jsonFromSomewhereElseIsRejected() {
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decode("""{"format":"someone-elses-app","version":1}""")
        }
    }

    @Test
    fun aBackupWithoutAVersionIsRejected() {
        assertThrows(BackupFormatException::class.java) {
            BackupJson.decode("""{"format":"${BackupJson.FORMAT}"}""")
        }
    }

    @Test
    fun aBackupFromANewerAppIsRejected() {
        val newer = """{"format":"${BackupJson.FORMAT}","version":${BackupJson.VERSION + 1}}"""

        assertThrows(BackupFormatException::class.java) { BackupJson.decode(newer) }
    }

    @Test
    fun aBackupMissingRequiredFieldsIsRejected() {
        val text = BackupJson.encode(sampleData()).replace("\"name\"", "\"nmae\"")

        assertThrows(BackupFormatException::class.java) { BackupJson.decode(text) }
    }

    @Test
    fun aBackupWithAnUnreadableDateIsRejected() {
        val text = BackupJson.encode(sampleData()).replace("2026-08-19", "the nineteenth")

        assertThrows(BackupFormatException::class.java) { BackupJson.decode(text) }
    }

    @Test
    fun aBackupWithAnUnknownScheduleTypeIsRejected() {
        val text = BackupJson.encode(sampleData()).replace("\"DAILY\"", "\"HOURLY\"")

        assertThrows(BackupFormatException::class.java) { BackupJson.decode(text) }
    }

    private fun sampleData(): BackupData {
        val daily = Schedule(
            scheduleType = ScheduleType.DAILY,
            timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)),
            startDate = LocalDate.of(2026, 8, 19)
        )
        val weekly = Schedule(
            scheduleType = ScheduleType.SPECIFIC_DAYS_OF_WEEK,
            daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            everyXDays = 3,
            timesOfDay = listOf(LocalTime.of(12, 15)),
            startDate = LocalDate.of(2026, 8, 20),
            reminderLeadMinutes = 15
        )

        return BackupData(
            exportedAt = exportedAt,
            appVersion = "1.2",
            prescriptions = listOf(
                Prescription(id = 1, name = "Metformin", color = 0xFF4CAF50, dosis = "850mg", schedule = daily),
                Prescription(id = 2, name = "Vitamin D", color = 0xFF2196F3, dosis = "1 pill", schedule = weekly)
            ),
            snapshots = listOf(
                PrescriptionSnapshot(
                    id = 10,
                    prescriptionId = 1,
                    name = "Metformin",
                    color = 0xFF4CAF50,
                    dosis = "850mg",
                    schedule = daily,
                    validFrom = LocalDateTime.of(2026, 8, 19, 7, 0),
                    validTo = null
                ),
                PrescriptionSnapshot(
                    id = 11,
                    prescriptionId = 2,
                    name = "Vitamin D",
                    color = 0xFF2196F3,
                    dosis = "1 pill",
                    schedule = weekly,
                    validFrom = LocalDateTime.of(2026, 8, 20, 7, 0),
                    validTo = LocalDateTime.of(2026, 8, 25, 7, 0)
                )
            ),
            doseIntakeRecords = listOf(
                DoseIntakeRecord(
                    id = 100,
                    snapshotId = 10,
                    scheduledDate = LocalDate.of(2026, 8, 19),
                    scheduledTime = LocalTime.of(8, 0),
                    taken = true,
                    takenAt = LocalDateTime.of(2026, 8, 19, 8, 3)
                ),
                DoseIntakeRecord(
                    id = 101,
                    snapshotId = 10,
                    scheduledDate = LocalDate.of(2026, 8, 19),
                    scheduledTime = LocalTime.of(20, 30),
                    taken = false,
                    takenAt = null
                )
            )
        )
    }
}
