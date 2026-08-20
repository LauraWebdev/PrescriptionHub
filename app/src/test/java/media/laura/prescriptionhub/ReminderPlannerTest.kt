package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.reminder.planReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderPlannerTest {

    private val date = LocalDate.of(2026, 8, 19)
    private val now = LocalDateTime.of(2026, 8, 19, 7, 0)
    private val windowEnd = now.plusHours(48)

    @Test
    fun aDoseWithoutAReminderIsSkipped() {
        val planned = planReminders(listOf(dose(lead = null)), now, windowEnd)

        assertTrue(planned.isEmpty())
    }

    @Test
    fun aReminderFiresItsLeadTimeBeforeTheDose() {
        val planned = planReminders(listOf(dose(lead = 10)), now, windowEnd)

        assertEquals(1, planned.size)
        assertEquals(LocalDateTime.of(2026, 8, 19, 7, 50), planned.first().triggerAt)
    }

    @Test
    fun everyOfferedLeadTimeIsHonoured() {
        reminderLeadOptions.filterNotNull().forEach { lead ->
            val planned = planReminders(listOf(dose(lead = lead)), now, windowEnd)

            assertEquals(
                "lead of $lead minutes",
                LocalDateTime.of(2026, 8, 19, 8, 0).minusMinutes(lead.toLong()),
                planned.single().triggerAt
            )
        }
    }

    @Test
    fun aTakenDoseIsSkipped() {
        val planned = planReminders(listOf(dose(lead = 10, taken = true)), now, windowEnd)

        assertTrue(planned.isEmpty())
    }

    @Test
    fun aReminderWhoseMomentHasPassedIsSkipped() {
        val planned = planReminders(
            doses = listOf(dose(lead = 10)),
            now = LocalDateTime.of(2026, 8, 19, 7, 55),
            windowEnd = windowEnd
        )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun aReminderExactlyAtNowIsStillArmed() {
        val planned = planReminders(
            doses = listOf(dose(lead = 10)),
            now = LocalDateTime.of(2026, 8, 19, 7, 50),
            windowEnd = windowEnd
        )

        assertEquals(1, planned.size)
    }

    @Test
    fun aReminderBeyondTheWindowIsLeftForALaterPass() {
        val planned = planReminders(
            doses = listOf(dose(lead = 10, date = date.plusDays(5))),
            now = now,
            windowEnd = windowEnd
        )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun aReminderExactlyAtTheWindowEndIsArmed() {
        val dueAt = windowEnd.plusMinutes(10)
        val planned = planReminders(
            doses = listOf(dose(lead = 10, date = dueAt.toLocalDate(), time = dueAt.toLocalTime())),
            now = now,
            windowEnd = windowEnd
        )

        assertEquals(windowEnd, planned.single().triggerAt)
    }

    @Test
    fun aLeadTimeCanReachBackIntoThePreviousDay() {
        val planned = planReminders(
            doses = listOf(dose(lead = 60, time = LocalTime.of(0, 5))),
            now = LocalDateTime.of(2026, 8, 18, 20, 0),
            windowEnd = LocalDateTime.of(2026, 8, 20, 20, 0)
        )

        assertEquals(LocalDateTime.of(2026, 8, 18, 23, 5), planned.single().triggerAt)
    }

    @Test
    fun theKeyIdentifiesThePrescriptionAndSlotRatherThanTheSnapshot() {
        val planned = planReminders(
            doses = listOf(dose(lead = 10, snapshotId = 77, prescriptionId = 3)),
            now = now,
            windowEnd = windowEnd
        )

        val key = planned.single().key
        assertEquals(3L, key.prescriptionId)
        assertEquals(date, key.date)
        assertEquals(LocalTime.of(8, 0), key.time)
    }

    @Test
    fun remindersComeBackInFiringOrder() {
        val planned = planReminders(
            doses = listOf(
                dose(lead = 5, time = LocalTime.of(20, 0), prescriptionId = 2),
                dose(lead = 10, time = LocalTime.of(8, 0), prescriptionId = 1)
            ),
            now = now,
            windowEnd = windowEnd
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 8, 19, 7, 50),
                LocalDateTime.of(2026, 8, 19, 19, 55)
            ),
            planned.map { it.triggerAt }
        )
    }

    private fun dose(
        lead: Int?,
        taken: Boolean = false,
        date: LocalDate = this.date,
        time: LocalTime = LocalTime.of(8, 0),
        snapshotId: Long = 1,
        prescriptionId: Long = 1
    ) = DoseChecklistItem(
        snapshotId = snapshotId,
        prescriptionId = prescriptionId,
        prescriptionName = "Metformin",
        prescriptionColor = 0xFF4CAF50,
        dosis = "850mg",
        scheduledDate = date,
        scheduledTime = time,
        taken = taken,
        takenAt = null,
        reminderLeadMinutes = lead
    )
}
