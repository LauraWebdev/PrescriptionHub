package media.laura.prescriptionhub

import java.util.Locale

fun testFormats(
    locale: Locale = Locale.UK,
    use24HourClock: Boolean = true
): DateTimeFormats = DateTimeFormats(
    locale = locale,
    use24HourClock = use24HourClock,
    timePattern = if (use24HourClock) "HH:mm" else "h:mm a",
    datePattern = "dd/MM/yyyy",
    dayAndMonthPattern = "d MMM",
    dayMonthAndYearPattern = "d MMM yyyy"
)
