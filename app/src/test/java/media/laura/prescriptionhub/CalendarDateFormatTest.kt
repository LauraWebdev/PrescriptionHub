package media.laura.prescriptionhub

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CalendarDateFormatTest {

    private val today = LocalDate.of(2026, 8, 19)

    @Test
    fun theSelectedDayIsMarkedWhenItIsToday() {
        assertEquals("Today, 19th Aug", formatCalendarTitle(date = today, today = today))
    }

    @Test
    fun anotherDayOfTheSameYearIsWrittenWithoutTheYear() {
        assertEquals("18th Aug", formatCalendarTitle(date = today.minusDays(1), today = today))
    }

    @Test
    fun aDayInAnotherYearCarriesTheYear() {
        assertEquals(
            "31st Dec 2025",
            formatCalendarTitle(date = LocalDate.of(2025, 12, 31), today = today)
        )
    }

    @Test
    fun ordinalSuffixesFollowEnglishRules() {
        assertEquals(listOf("st", "nd", "rd", "th"), listOf(1, 2, 3, 4).map(::getDayOfMonthSuffix))
        assertEquals(listOf("th", "th", "th"), listOf(11, 12, 13).map(::getDayOfMonthSuffix))
        assertEquals(listOf("st", "nd", "rd"), listOf(21, 22, 23).map(::getDayOfMonthSuffix))
    }
}
