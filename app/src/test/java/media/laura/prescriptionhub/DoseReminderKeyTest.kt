package media.laura.prescriptionhub

import android.net.Uri
import media.laura.prescriptionhub.reminder.DoseReminderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class DoseReminderKeyTest {

    @Test
    fun aKeySurvivesTheRoundTripThroughItsUri() {
        val key = DoseReminderKey(
            prescriptionId = 42,
            date = LocalDate.of(2026, 8, 19),
            time = LocalTime.of(20, 30)
        )

        assertEquals(key, DoseReminderKey.fromUri(key.toUri()))
    }

    @Test
    fun midnightSurvivesTheRoundTrip() {
        val key = DoseReminderKey(
            prescriptionId = 1,
            date = LocalDate.of(2026, 1, 1),
            time = LocalTime.MIDNIGHT
        )

        assertEquals(key, DoseReminderKey.fromUri(key.toUri()))
    }

    @Test
    fun differentSlotsGetDifferentUris() {
        val date = LocalDate.of(2026, 8, 19)
        val uris = listOf(
            DoseReminderKey(1, date, LocalTime.of(8, 0)),
            DoseReminderKey(1, date, LocalTime.of(20, 0)),
            DoseReminderKey(2, date, LocalTime.of(8, 0)),
            DoseReminderKey(1, date.plusDays(1), LocalTime.of(8, 0))
        ).map { it.toUri().toString() }

        assertEquals(uris.size, uris.toSet().size)
    }

    @Test
    fun secondsAreKeptApartFromMinutes() {
        val date = LocalDate.of(2026, 8, 19)

        assertNotEquals(
            DoseReminderKey(1, date, LocalTime.of(8, 0, 30)).toUri(),
            DoseReminderKey(1, date, LocalTime.of(8, 0)).toUri()
        )
    }

    @Test
    fun aForeignUriIsRejected() {
        assertNull(DoseReminderKey.fromUri(Uri.parse("https://example.com/dose/1/2/3")))
        assertNull(DoseReminderKey.fromUri(Uri.parse("prescriptionhub://other/1/2/3")))
        assertNull(DoseReminderKey.fromUri(Uri.parse("prescriptionhub://dose/1/2")))
        assertNull(DoseReminderKey.fromUri(Uri.parse("prescriptionhub://dose/a/b/c")))
        assertNull(DoseReminderKey.fromUri(null))
    }

    @Test
    fun anOutOfRangeTimeIsRejectedRatherThanThrowing() {
        assertNull(DoseReminderKey.fromUri(Uri.parse("prescriptionhub://dose/1/20000/999999")))
    }

    @Test
    fun theTagIsTheUri() {
        val key = DoseReminderKey(7, LocalDate.of(2026, 8, 19), LocalTime.of(8, 0))

        assertEquals(key.toUri().toString(), key.toTag())
    }
}
