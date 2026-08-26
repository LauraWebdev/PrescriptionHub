package media.laura.prescriptionhub.data.backup

import media.laura.prescriptionhub.R
import media.laura.prescriptionhub.data.model.DoseIntakeRecord
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.PrescriptionSnapshot
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Reads and writes the JSON document behind [BackupData].
 */
object BackupJson {
    const val FORMAT = "prescription-hub-backup"
    const val VERSION = 1

    private const val INDENT = 2

    private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_TIME
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun encode(data: BackupData): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("exportedAt", data.exportedAt.format(DATE_TIME))
            .put("appVersion", data.appVersion)
            .put("prescriptions", data.prescriptions.toJsonArray(::prescriptionToJson))
            .put("snapshots", data.snapshots.toJsonArray(::snapshotToJson))
            .put("doseIntakeRecords", data.doseIntakeRecords.toJsonArray(::doseIntakeRecordToJson))
        return root.toString(INDENT)
    }

    /**
     * Parses [text] into a [BackupData].
     *
     * @throws BackupFormatException When [text] is not a backup this build understands.
     */
    fun decode(text: String): BackupData {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException(R.string.backup_error_not_json, e)
        }

        if (root.optString("format") != FORMAT) {
            throw BackupFormatException(R.string.backup_error_not_a_backup)
        }

        val version = root.optInt("version", 0)
        if (version < 1) {
            throw BackupFormatException(R.string.backup_error_no_version)
        }
        if (version > VERSION) {
            throw BackupFormatException(R.string.backup_error_newer_version)
        }

        return try {
            BackupData(
                exportedAt = LocalDateTime.parse(root.getString("exportedAt"), DATE_TIME),
                appVersion = root.optString("appVersion"),
                prescriptions = root.readList("prescriptions", ::prescriptionFromJson),
                snapshots = root.readList("snapshots", ::snapshotFromJson),
                doseIntakeRecords = root.readList("doseIntakeRecords", ::doseIntakeRecordFromJson)
            )
        } catch (e: JSONException) {
            throw BackupFormatException(R.string.backup_error_incomplete, e)
        } catch (e: DateTimeParseException) {
            throw BackupFormatException(R.string.backup_error_bad_date, e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException(R.string.backup_error_bad_value, e)
        }
    }

    private fun prescriptionToJson(prescription: Prescription): JSONObject = JSONObject()
        .put("id", prescription.id)
        .put("name", prescription.name)
        .put("color", prescription.color)
        .put("dosis", prescription.dosis)
        .put("schedule", scheduleToJson(prescription.schedule))

    private fun prescriptionFromJson(json: JSONObject): Prescription = Prescription(
        id = json.getLong("id"),
        name = json.getString("name"),
        color = json.getLong("color"),
        dosis = json.getString("dosis"),
        schedule = scheduleFromJson(json.getJSONObject("schedule"))
    )

    private fun snapshotToJson(snapshot: PrescriptionSnapshot): JSONObject = JSONObject()
        .put("id", snapshot.id)
        .put("prescriptionId", snapshot.prescriptionId)
        .put("name", snapshot.name)
        .put("color", snapshot.color)
        .put("dosis", snapshot.dosis)
        .put("schedule", scheduleToJson(snapshot.schedule))
        .put("validFrom", snapshot.validFrom.format(DATE_TIME))
        .putNullable("validTo", snapshot.validTo?.format(DATE_TIME))

    private fun snapshotFromJson(json: JSONObject): PrescriptionSnapshot = PrescriptionSnapshot(
        id = json.getLong("id"),
        prescriptionId = json.getLong("prescriptionId"),
        name = json.getString("name"),
        color = json.getLong("color"),
        dosis = json.getString("dosis"),
        schedule = scheduleFromJson(json.getJSONObject("schedule")),
        validFrom = LocalDateTime.parse(json.getString("validFrom"), DATE_TIME),
        validTo = json.readNullableString("validTo")?.let { LocalDateTime.parse(it, DATE_TIME) }
    )

    private fun doseIntakeRecordToJson(record: DoseIntakeRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("snapshotId", record.snapshotId)
        .put("scheduledDate", record.scheduledDate.format(DATE))
        .put("scheduledTime", record.scheduledTime.format(TIME))
        .put("taken", record.taken)
        .putNullable("takenAt", record.takenAt?.format(DATE_TIME))

    private fun doseIntakeRecordFromJson(json: JSONObject): DoseIntakeRecord = DoseIntakeRecord(
        id = json.getLong("id"),
        snapshotId = json.getLong("snapshotId"),
        scheduledDate = LocalDate.parse(json.getString("scheduledDate"), DATE),
        scheduledTime = LocalTime.parse(json.getString("scheduledTime"), TIME),
        taken = json.getBoolean("taken"),
        takenAt = json.readNullableString("takenAt")?.let { LocalDateTime.parse(it, DATE_TIME) }
    )

    private fun scheduleToJson(schedule: Schedule): JSONObject = JSONObject()
        .put("scheduleType", schedule.scheduleType.name)
        .put("daysOfWeek", JSONArray(schedule.daysOfWeek.map { it.name }))
        .putNullable("everyXDays", schedule.everyXDays)
        .put("timesOfDay", JSONArray(schedule.timesOfDay.map { it.format(TIME) }))
        .put("startDate", schedule.startDate.format(DATE))
        .putNullable("reminderLeadMinutes", schedule.reminderLeadMinutes)

    private fun scheduleFromJson(json: JSONObject): Schedule = Schedule(
        scheduleType = ScheduleType.valueOf(json.getString("scheduleType")),
        daysOfWeek = json.readStrings("daysOfWeek").map { DayOfWeek.valueOf(it) },
        everyXDays = json.readNullableInt("everyXDays"),
        timesOfDay = json.readStrings("timesOfDay").map { LocalTime.parse(it, TIME) },
        startDate = LocalDate.parse(json.getString("startDate"), DATE),
        reminderLeadMinutes = json.readNullableInt("reminderLeadMinutes")
    )

    private fun <T> List<T>.toJsonArray(toJson: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(toJson(it)) } }

    private fun <T> JSONObject.readList(name: String, fromJson: (JSONObject) -> T): List<T> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { index -> fromJson(array.getJSONObject(index)) }
    }

    private fun JSONObject.readStrings(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { index -> array.getString(index) }
    }

    private fun JSONObject.readNullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.readNullableInt(name: String): Int? =
        if (isNull(name)) null else getInt(name)

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)
}
