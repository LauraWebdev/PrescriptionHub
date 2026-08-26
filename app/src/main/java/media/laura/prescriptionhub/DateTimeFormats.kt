package media.laura.prescriptionhub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Class for formatting dates and times in the users locale way.
 *
 * @param locale The locale month and weekday names are written in.
 * @param use24HourClock Defines 24 hour clock format. (06:00 vs 04:00pm)
 */
@Immutable
class DateTimeFormats(
    private val locale: Locale,
    val use24HourClock: Boolean,
    timePattern: String,
    datePattern: String,
    dayAndMonthPattern: String,
    dayMonthAndYearPattern: String
) {
    private val timeFormatter = DateTimeFormatter.ofPattern(timePattern, locale)
    private val dateFormatter = DateTimeFormatter.ofPattern(datePattern, locale)
    private val dayAndMonthFormatter = DateTimeFormatter.ofPattern(dayAndMonthPattern, locale)
    private val dayMonthAndYearFormatter =
        DateTimeFormatter.ofPattern(dayMonthAndYearPattern, locale)

    fun time(time: LocalTime): String = timeFormatter.format(time)
    fun date(date: LocalDate): String = dateFormatter.format(date)
    fun dayAndMonth(date: LocalDate): String = dayAndMonthFormatter.format(date)
    fun dayMonthAndYear(date: LocalDate): String = dayMonthAndYearFormatter.format(date)
    fun weekdayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale)
    fun weekdayNarrow(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, locale)
    fun weekdayFull(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, locale)

    companion object {
        /**
         * Reads the locale and the 24-hour preference the device is set to.
         */
        fun from(context: Context): DateTimeFormats {
            val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
            val use24HourClock = DateFormat.is24HourFormat(context)
            return DateTimeFormats(
                locale = locale,
                use24HourClock = use24HourClock,
                timePattern = bestPattern(locale, if (use24HourClock) "Hm" else "hm"),
                datePattern = bestPattern(locale, "yMd"),
                dayAndMonthPattern = bestPattern(locale, "MMMd"),
                dayMonthAndYearPattern = bestPattern(locale, "yMMMd")
            )
        }

        fun forLocale(locale: Locale = Locale.getDefault()): DateTimeFormats = DateTimeFormats(
            locale = locale,
            use24HourClock = true,
            timePattern = "HH:mm",
            datePattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT,
                null,
                IsoChronology.INSTANCE,
                locale
            ),
            dayAndMonthPattern = "d MMM",
            dayMonthAndYearPattern = "d MMM y"
        )

        private fun bestPattern(locale: Locale, skeleton: String): String =
            DateFormat.getBestDateTimePattern(locale, skeleton)
    }
}

val LocalDateTimeFormats = compositionLocalOf { DateTimeFormats.forLocale() }

/** Reads the device's date and time settings, and re-reads them whenever the user changes them. */
@Composable
fun rememberDateTimeFormats(): DateTimeFormats {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var formats by remember(context, configuration) {
        mutableStateOf(DateTimeFormats.from(context))
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedFrom: Context, intent: Intent) {
                formats = DateTimeFormats.from(receivedFrom)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_TIME_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    return formats
}
