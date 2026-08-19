package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.model.DoseChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DoseStatusTest {

    @Test
    fun takenDoseReportsTheTimeItWasTakenAt() {
        val dose = dose(taken = true, takenAt = LocalDateTime.of(2026, 8, 19, 8, 3))

        assertEquals(DoseStatus.TAKEN, doseStatus(dose, now = LocalDateTime.of(2026, 8, 19, 18, 0)))
        assertEquals("Taken 08:03", formatDoseStatus(dose, now = LocalDateTime.of(2026, 8, 19, 18, 0)))
    }

    @Test
    fun takenDoseWithoutATimestampStillReadsAsTaken() {
        val dose = dose(taken = true, takenAt = null)

        assertEquals("Taken", formatDoseStatus(dose, now = LocalDateTime.of(2026, 8, 19, 18, 0)))
    }

    @Test
    fun openDoseWhoseTimeHasPassedCountsAsMissed() {
        val dose = dose(taken = false)

        assertEquals(DoseStatus.MISSED, doseStatus(dose, now = LocalDateTime.of(2026, 8, 19, 18, 0)))
        assertEquals("Not taken", formatDoseStatus(dose, now = LocalDateTime.of(2026, 8, 19, 18, 0)))
    }

    @Test
    fun openDoseThatIsStillDueReportsNothing() {
        val dose = dose(taken = false)

        assertEquals(DoseStatus.UPCOMING, doseStatus(dose, now = LocalDateTime.of(2026, 8, 19, 7, 0)))
        assertNull(formatDoseStatus(dose, now = LocalDateTime.of(2026, 8, 19, 7, 0)))
    }

    @Test
    fun doseOnAFutureDayReportsNothing() {
        val dose = dose(taken = false, date = LocalDate.of(2026, 8, 25))

        assertEquals(DoseStatus.UPCOMING, doseStatus(dose, now = LocalDateTime.of(2026, 8, 19, 18, 0)))
        assertNull(formatDoseStatus(dose, now = LocalDateTime.of(2026, 8, 19, 18, 0)))
    }

    @Test
    fun doseExactlyAtTheCurrentMomentIsAlreadyDue() {
        val dose = dose(taken = false)

        assertEquals(DoseStatus.MISSED, doseStatus(dose, now = LocalDateTime.of(2026, 8, 19, 8, 0)))
    }

    private fun dose(
        taken: Boolean,
        takenAt: LocalDateTime? = null,
        date: LocalDate = LocalDate.of(2026, 8, 19)
    ) = DoseChecklistItem(
        snapshotId = 1,
        prescriptionId = 1,
        prescriptionName = "Metformin",
        prescriptionColor = 0xFF102030,
        dosis = "850mg",
        scheduledDate = date,
        scheduledTime = LocalTime.of(8, 0),
        taken = taken,
        takenAt = takenAt
    )
}
