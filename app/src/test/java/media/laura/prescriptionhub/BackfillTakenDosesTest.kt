package media.laura.prescriptionhub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import media.laura.prescriptionhub.data.repository.PrescriptionRepository
import media.laura.prescriptionhub.data.repository.PrescriptionService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Covers a prescription whose start date has already passed.
 */
@RunWith(RobolectricTestRunner::class)
class BackfillTakenDosesTest {

    private val today = LocalDate.of(2026, 8, 26)
    private val threeDaysAgo = LocalDate.of(2026, 8, 23)

    /** Mid-afternoon, so an 08:00 dose is behind us today and a 20:00 dose is not. */
    private var now = LocalDateTime.of(2026, 8, 26, 15, 0)

    private lateinit var database: PrescriptionDatabase
    private lateinit var service: PrescriptionService

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PrescriptionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = PrescriptionRepository(database.prescriptionDao(), nowProvider = { now })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aPastStartDateRevealsItsDaysAsUntakenBeforeAnyBackfill() = runBlocking {
        addPrescription(startDate = threeDaysAgo)

        val doses = service.getDoseChecklistBetween(threeDaysAgo.atStartOfDay(), now)

        assertEquals(
            listOf(threeDaysAgo, threeDaysAgo.plusDays(1), threeDaysAgo.plusDays(2), today),
            doses.map { it.scheduledDate }.distinct()
        )
        // Three whole days of 08:00 and 20:00, plus today's 08:00.
        assertEquals(7, doses.size)
        assertTrue(doses.none { it.taken })
    }

    @Test
    fun aStartDateOfTodayLeavesHistoryBeginningWhenThePrescriptionWasAdded() = runBlocking {
        addPrescription(startDate = today)

        val snapshot = database.prescriptionDao().getAllSnapshotsOnce().single()

        assertEquals(now, snapshot.validFrom)
    }

    @Test
    fun backfillRecordsEveryDoseAlreadyDueAtItsOwnScheduledTime() = runBlocking {
        val id = addPrescription(startDate = threeDaysAgo)

        val recorded = service.backfillTakenDoses(id, now)

        // Three whole days of 08:00 and 20:00, plus today's 08:00.
        assertEquals(7, recorded)
        val taken = dosesUpTo(now).filter { it.taken }
        assertEquals(7, taken.size)
        assertTrue(taken.all { it.takenAt == it.scheduledDateTime })
    }

    @Test
    fun backfillLeavesDosesThatAreNotDueYetAlone() = runBlocking {
        val id = addPrescription(startDate = threeDaysAgo)

        service.backfillTakenDoses(id, now)

        val tonight = service.getDoseChecklistForDateOnce(today)
            .single { it.scheduledTime == LocalTime.of(20, 0) }
        assertFalse(tonight.taken)
        assertNull(tonight.takenAt)
    }

    @Test
    fun backfillDoesNotOverruleADoseTheUserAlreadyAnswered() = runBlocking {
        val id = addPrescription(startDate = threeDaysAgo)
        val snapshotId = database.prescriptionDao().getOpenSnapshotForPrescription(id)!!.id
        service.setDoseTaken(snapshotId, threeDaysAgo, LocalTime.of(8, 0), taken = false)

        val recorded = service.backfillTakenDoses(id, now)

        assertEquals(6, recorded)
        val missed = dosesUpTo(now).single {
            it.scheduledDate == threeDaysAgo && it.scheduledTime == LocalTime.of(8, 0)
        }
        assertFalse(missed.taken)
    }

    @Test
    fun backfillTouchesOnlyThePrescriptionItWasAskedAbout() = runBlocking {
        val backfilled = addPrescription(startDate = threeDaysAgo, name = "Metformin")
        addPrescription(startDate = threeDaysAgo, name = "Vitamin D")

        service.backfillTakenDoses(backfilled, now)

        val taken = dosesUpTo(now).filter { it.taken }
        assertTrue(taken.all { it.prescriptionName == "Metformin" })
    }

    @Test
    fun runningBackfillTwiceRecordsNothingTheSecondTime() = runBlocking {
        val id = addPrescription(startDate = threeDaysAgo)

        val first = service.backfillTakenDoses(id, now)
        val second = service.backfillTakenDoses(id, now)

        assertEquals(7, first)
        assertEquals(0, second)
    }

    @Test
    fun backfillOnAPrescriptionThatDoesNotExistRecordsNothing() = runBlocking {
        assertEquals(0, service.backfillTakenDoses(prescriptionId = 404, until = now))
    }

    @Test
    fun editingAPrescriptionOntoAnEarlierStartDateRevealsTheDaysBetween() = runBlocking {
        val id = addPrescription(startDate = today)
        val stored = service.getPrescription(id)!!

        now = now.plusMinutes(30)
        service.updatePrescription(
            stored.copy(schedule = stored.schedule.copy(startDate = threeDaysAgo))
        )

        val doses = dosesUpTo(now)
        assertEquals(threeDaysAgo, doses.first().scheduledDate)
        assertEquals(7, doses.size)
    }

    @Test
    fun editingAPrescriptionWithoutMovingItsStartDateLeavesHistoryWhereItWas() = runBlocking {
        val id = addPrescription(startDate = today)
        val addedAt = now
        val stored = service.getPrescription(id)!!

        now = now.plusMinutes(30)
        service.updatePrescription(stored.copy(dosis = "1000mg"))

        val earliest = database.prescriptionDao().getAllSnapshotsOnce().minBy { it.validFrom }
        assertEquals(addedAt, earliest.validFrom)
    }

    @Test
    fun backfillAfterAnEditCoversTheWholeRevealedHistory() = runBlocking {
        val id = addPrescription(startDate = today)
        val stored = service.getPrescription(id)!!

        now = now.plusMinutes(30)
        service.updatePrescription(
            stored.copy(schedule = stored.schedule.copy(startDate = threeDaysAgo))
        )
        val recorded = service.backfillTakenDoses(id, now)

        assertEquals(7, recorded)
        assertTrue(dosesUpTo(now).all { it.taken })
    }

    private suspend fun addPrescription(
        startDate: LocalDate,
        name: String = "Metformin"
    ): Long = service.addPrescription(
        Prescription(
            name = name,
            color = 0xFF4CAF50,
            dosis = "850mg",
            schedule = Schedule(
                scheduleType = ScheduleType.DAILY,
                timesOfDay = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                startDate = startDate
            )
        )
    )

    private suspend fun dosesUpTo(until: LocalDateTime): List<DoseChecklistItem> =
        service.getDoseChecklistBetween(threeDaysAgo.atStartOfDay(), until)

    private suspend fun PrescriptionService.getDoseChecklistForDateOnce(
        date: LocalDate
    ): List<DoseChecklistItem> =
        getDoseChecklistBetween(date.atStartOfDay(), date.atTime(LocalTime.MAX))
}
