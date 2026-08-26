package media.laura.prescriptionhub

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class CalendarDateFormatTest {

    private val today = LocalDate.of(2026, 8, 19)
    private val formats = testFormats()

    @Test
    fun theSelectedDayIsMarkedWhenItIsToday() {
        assertEquals(
            "Today, 19 Aug",
            formatCalendarTitle(date = today, today = today, formats = formats)
        )
    }

    @Test
    fun anotherDayOfTheSameYearIsWrittenWithoutTheYear() {
        assertEquals(
            "18 Aug",
            formatCalendarTitle(date = today.minusDays(1), today = today, formats = formats)
        )
    }

    @Test
    fun aDayInAnotherYearCarriesTheYear() {
        assertEquals(
            "31 Dec 2025",
            formatCalendarTitle(
                date = LocalDate.of(2025, 12, 31),
                today = today,
                formats = formats
            )
        )
    }

    @Test
    fun theMonthIsNamedInTheDeviceLanguage() {
        val german = DateTimeFormats(
            locale = Locale.GERMANY,
            use24HourClock = true,
            timePattern = "HH:mm",
            datePattern = "dd.MM.yyyy",
            dayAndMonthPattern = "d. MMM",
            dayMonthAndYearPattern = "d. MMM yyyy"
        )

        assertEquals(
            "Today, 19. Aug.",
            formatCalendarTitle(date = today, today = today, formats = german)
        )
    }
}
