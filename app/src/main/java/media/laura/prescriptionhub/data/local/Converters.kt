package media.laura.prescriptionhub.data.local

import androidx.room.TypeConverter
import media.laura.prescriptionhub.data.model.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Type converters allowing Room to store complex types (enums, lists, Java time types)
 * as simple SQLite-compatible strings.
 */
class Converters {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_TIME
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @TypeConverter
    fun fromScheduleType(value: ScheduleType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toScheduleType(value: String?): ScheduleType? {
        return value?.let {
            try {
                ScheduleType.valueOf(it.trim().uppercase())
            } catch (e: IllegalArgumentException) {
                ScheduleType.DAILY
            }
        }
    }

    @TypeConverter
    fun fromDayOfWeekList(days: List<DayOfWeek>?): String? {
        if (days == null) return null
        return days.joinToString(separator = ",") { it.name }
    }

    @TypeConverter
    fun toDayOfWeekList(value: String?): List<DayOfWeek>? {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",")
            .mapNotNull { token ->
                val trimmed = token.trim()
                if (trimmed.isEmpty()) {
                    null
                } else {
                    try {
                        DayOfWeek.valueOf(trimmed.uppercase())
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            }
    }

    @TypeConverter
    fun fromLocalTimeList(times: List<LocalTime>?): String? {
        if (times == null) return null
        return times.joinToString(separator = ",") { it.format(timeFormatter) }
    }

    @TypeConverter
    fun toLocalTimeList(value: String?): List<LocalTime>? {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",")
            .mapNotNull { token ->
                val trimmed = token.trim()
                if (trimmed.isEmpty()) {
                    null
                } else if (trimmed == "24:00" || trimmed == "24:00:00") {
                    LocalTime.MIDNIGHT
                } else {
                    try {
                        LocalTime.parse(trimmed, timeFormatter)
                    } catch (e: Exception) {
                        try {
                            LocalTime.parse(trimmed)
                        } catch (e2: Exception) {
                            null
                        }
                    }
                }
            }
    }

    @TypeConverter
    fun fromLocalTime(time: LocalTime?): String? {
        return time?.format(timeFormatter)
    }

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? {
        return value?.let {
            val trimmed = it.trim()
            if (trimmed == "24:00" || trimmed == "24:00:00") {
                LocalTime.MIDNIGHT
            } else {
                try {
                    LocalTime.parse(trimmed, timeFormatter)
                } catch (e: Exception) {
                    try {
                        LocalTime.parse(trimmed)
                    } catch (e2: Exception) {
                        null
                    }
                }
            }
        }
    }

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.format(dateFormatter)
    }

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? {
        return value?.let {
            try {
                LocalDate.parse(it, dateFormatter)
            } catch (e: Exception) {
                try {
                    LocalDate.parse(it)
                } catch (e2: Exception) {
                    null
                }
            }
        }
    }

    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? {
        return dateTime?.format(dateTimeFormatter)
    }

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let {
            val normalized = it.trim().replace(" ", "T")
            try {
                LocalDateTime.parse(normalized, dateTimeFormatter)
            } catch (e: Exception) {
                try {
                    LocalDateTime.parse(normalized)
                } catch (e2: Exception) {
                    null
                }
            }
        }
    }
}
