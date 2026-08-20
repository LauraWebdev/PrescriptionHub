package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.reminder.DoseReminderKey
import media.laura.prescriptionhub.reminder.DoseReminderReceiver
import media.laura.prescriptionhub.reminder.ReminderOutcome
import media.laura.prescriptionhub.reminder.reminderOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderOutcomeTest {

    private val date = LocalDate.of(2026, 8, 19)
    private val time = LocalTime.of(8, 0)
    private val key = DoseReminderKey(prescriptionId = 1, date = date, time = time)
    private val dueAt = LocalDateTime.of(2026, 8, 19, 8, 0)

    @Test
    fun firingPostsAnInsistentReminderBeforeTheDoseIsDue() {
        assertEquals(
            ReminderOutcome.POST_INSISTENT,
            outcome(DoseReminderReceiver.ACTION_FIRE, now = dueAt.minusMinutes(10))
        )
    }

    @Test
    fun theInsistenceWindowRunsFiveMinutesPastTheDueTime() {
        assertEquals(dueAt.plusMinutes(5), key.insistUntil)
    }

    @Test
    fun dismissingInsideTheWindowBringsTheReminderBack() {
        listOf(
            dueAt.minusMinutes(1),
            dueAt,
            dueAt.plusMinutes(4),
            dueAt.plusMinutes(5)
        ).forEach { now ->
            assertEquals(
                "dismissed at $now",
                ReminderOutcome.POST_INSISTENT,
                outcome(DoseReminderReceiver.ACTION_DISMISSED, now = now)
            )
        }
    }

    @Test
    fun dismissingAfterTheWindowLetsTheReminderGo() {
        assertEquals(
            ReminderOutcome.NOTHING,
            outcome(DoseReminderReceiver.ACTION_DISMISSED, now = dueAt.plusMinutes(6))
        )
    }

    @Test
    fun dismissingATakenDoseNeverBringsItBack() {
        assertEquals(
            ReminderOutcome.CANCEL,
            outcome(
                DoseReminderReceiver.ACTION_DISMISSED,
                now = dueAt.minusMinutes(1),
                dose = dose(taken = true)
            )
        )
    }

    @Test
    fun firingAfterTheWindowPostsTheQuietReminder() {
        assertEquals(
            ReminderOutcome.POST_QUIET,
            outcome(DoseReminderReceiver.ACTION_FIRE, now = dueAt.plusMinutes(6))
        )
    }

    @Test
    fun relaxingPostsTheQuietReminderEvenWhenTheAlarmArrivesLate() {
        assertEquals(
            ReminderOutcome.POST_QUIET,
            outcome(DoseReminderReceiver.ACTION_RELAX, now = dueAt.plusHours(3))
        )
    }

    @Test
    fun firingForATakenDoseCancelsInstead() {
        assertEquals(
            ReminderOutcome.CANCEL,
            outcome(
                DoseReminderReceiver.ACTION_FIRE,
                now = dueAt.minusMinutes(10),
                dose = dose(taken = true)
            )
        )
    }

    @Test
    fun firingForADoseThatIsNoLongerScheduledCancelsInstead() {
        assertEquals(
            ReminderOutcome.CANCEL,
            outcome(DoseReminderReceiver.ACTION_FIRE, now = dueAt.minusMinutes(10), dose = null)
        )
    }

    @Test
    fun firingAfterRemindersWereSwitchedOffCancelsInstead() {
        assertEquals(
            ReminderOutcome.CANCEL,
            outcome(
                DoseReminderReceiver.ACTION_FIRE,
                now = dueAt.minusMinutes(10),
                dose = dose(lead = null)
            )
        )
    }

    @Test
    fun takenMarksTheDose() {
        assertEquals(
            ReminderOutcome.MARK_TAKEN,
            outcome(DoseReminderReceiver.ACTION_TAKEN, now = dueAt.minusMinutes(10))
        )
    }

    @Test
    fun snoozeDefersTheReminder() {
        assertEquals(
            ReminderOutcome.SNOOZE,
            outcome(DoseReminderReceiver.ACTION_SNOOZE, now = dueAt.minusMinutes(10))
        )
    }

    @Test
    fun anUnknownActionDoesNothing() {
        assertEquals(
            ReminderOutcome.NOTHING,
            outcome("some.other.ACTION", now = dueAt.minusMinutes(10))
        )
    }

    private fun outcome(
        action: String,
        now: LocalDateTime,
        dose: DoseChecklistItem? = dose()
    ) = reminderOutcome(action = action, key = key, dose = dose, now = now)

    private fun dose(taken: Boolean = false, lead: Int? = 10) = DoseChecklistItem(
        snapshotId = 1,
        prescriptionId = 1,
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
