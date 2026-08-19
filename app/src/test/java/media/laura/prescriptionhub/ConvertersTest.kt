package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.local.Converters
import media.laura.prescriptionhub.data.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun testScheduleTypeConversion() {
        assertEquals("DAILY", converters.fromScheduleType(ScheduleType.DAILY))
        assertEquals("SPECIFIC_DAYS_OF_WEEK", converters.fromScheduleType(ScheduleType.SPECIFIC_DAYS_OF_WEEK))
        assertEquals("EVERY_X_DAYS", converters.fromScheduleType(ScheduleType.EVERY_X_DAYS))
        assertNull(converters.fromScheduleType(null))

        assertEquals(ScheduleType.DAILY, converters.toScheduleType("DAILY"))
        assertEquals(ScheduleType.SPECIFIC_DAYS_OF_WEEK, converters.toScheduleType("SPECIFIC_DAYS_OF_WEEK"))
        assertEquals(ScheduleType.EVERY_X_DAYS, converters.toScheduleType("EVERY_X_DAYS"))
        assertEquals(ScheduleType.DAILY, converters.toScheduleType("INVALID_VALUE"))
        assertNull(converters.toScheduleType(null))
    }

    @Test
    fun testDayOfWeekListConversion() {
        val days = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val serialized = converters.fromDayOfWeekList(days)
        assertEquals("MONDAY,WEDNESDAY,FRIDAY", serialized)

        val deserialized = converters.toDayOfWeekList(serialized)
        assertEquals(days, deserialized)

        assertNull(converters.fromDayOfWeekList(null))
        assertTrue(converters.toDayOfWeekList(null)?.isEmpty() == true)
        assertTrue(converters.toDayOfWeekList("")?.isEmpty() == true)
        assertTrue(converters.toDayOfWeekList("   ")?.isEmpty() == true)

        // Tolerance against spaces and invalid entries
        val mixed = converters.toDayOfWeekList(" MONDAY , INVALID_DAY , TUESDAY ")
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), mixed)
    }

    @Test
    fun testLocalTimeListConversion() {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(14, 30), LocalTime.of(23, 0))
        val serialized = converters.fromLocalTimeList(times)
        assertEquals("08:00:00,14:30:00,23:00:00", serialized)

        val deserialized = converters.toLocalTimeList(serialized)
        assertEquals(times, deserialized)

        assertNull(converters.fromLocalTimeList(null))
        assertTrue(converters.toLocalTimeList(null)?.isEmpty() == true)
        assertTrue(converters.toLocalTimeList("")?.isEmpty() == true)

        // Parsing standard HH:mm and 24:00 edge case
        val parsedTimes = converters.toLocalTimeList("08:00, 16:00, 24:00")
        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(16, 0), LocalTime.MIDNIGHT),
            parsedTimes
        )
    }

    @Test
    fun testLocalDateConversion() {
        val date = LocalDate.of(2026, 8, 19)
        val serialized = converters.fromLocalDate(date)
        assertEquals("2026-08-19", serialized)

        val deserialized = converters.toLocalDate(serialized)
        assertEquals(date, deserialized)

        assertNull(converters.fromLocalDate(null))
        assertNull(converters.toLocalDate(null))
    }

    @Test
    fun testLocalDateTimeConversion() {
        val dateTime = LocalDateTime.of(2026, 8, 19, 8, 4, 5)
        val serialized = converters.fromLocalDateTime(dateTime)
        assertEquals("2026-08-19T08:04:05", serialized)

        val deserialized = converters.toLocalDateTime(serialized)
        assertEquals(dateTime, deserialized)

        // SQLite-style space separator should also be parsed.
        val sqliteFormatted = converters.toLocalDateTime("2026-08-19 08:04:05")
        assertEquals(dateTime, sqliteFormatted)

        assertNull(converters.fromLocalDateTime(null))
        assertNull(converters.toLocalDateTime(null))
    }
}
