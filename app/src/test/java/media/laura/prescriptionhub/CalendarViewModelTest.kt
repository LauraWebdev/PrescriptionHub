package media.laura.prescriptionhub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import media.laura.prescriptionhub.data.repository.PrescriptionRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CalendarViewModelTest {

    private lateinit var database: PrescriptionDatabase
    private lateinit var repository: PrescriptionRepository

    private val insertedAt = LocalDateTime.of(2026, 8, 17, 7, 0)
    private var now = LocalDateTime.of(2026, 8, 19, 18, 0)

    private val today: LocalDate get() = now.toLocalDate()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PrescriptionDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = PrescriptionRepository(database.prescriptionDao(), nowProvider = { insertedAt })
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun theCalendarOpensOnToday() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0))

        val state = awaitState(viewModel())

        assertEquals(today, state.selectedDate)
        assertEquals(today, state.today)
    }

    @Test
    fun steppingBackAndForwardMovesOneDay() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0))
        val viewModel = viewModel()

        viewModel.showPreviousDay()
        assertEquals(today.minusDays(1), awaitState(viewModel) { it.selectedDate == today.minusDays(1) }.selectedDate)

        viewModel.showNextDay()
        viewModel.showNextDay()
        assertEquals(today.plusDays(1), awaitState(viewModel) { it.selectedDate == today.plusDays(1) }.selectedDate)
    }

    @Test
    fun selectingADayShowsThatDaysDoses() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0), LocalTime.of(20, 0))
        val viewModel = viewModel()

        val yesterday = today.minusDays(1)
        viewModel.selectDate(yesterday)

        val state = awaitState(viewModel) { it.selectedDate == yesterday }
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), state.groups.map { it.time })
        assertEquals(yesterday, state.groups.first().doses.first().scheduledDate)
    }

    @Test
    fun aPastDayReportsWhatWasActuallyTaken() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0), LocalTime.of(20, 0))
        val yesterday = today.minusDays(1)

        // Only the morning dose was recorded yesterday.
        val doses = repository.getDoseChecklistForDate(yesterday).first()
        repository.setDoseTaken(
            snapshotId = doses.first { it.scheduledTime == LocalTime.of(8, 0) }.snapshotId,
            scheduledDate = yesterday,
            scheduledTime = LocalTime.of(8, 0),
            taken = true,
            takenAt = yesterday.atTime(8, 3)
        )

        val viewModel = viewModel()
        viewModel.selectDate(yesterday)
        val state = awaitState(viewModel) { it.selectedDate == yesterday }

        val morning = state.groups.first { it.time == LocalTime.of(8, 0) }.doses.single()
        val evening = state.groups.first { it.time == LocalTime.of(20, 0) }.doses.single()
        assertTrue(morning.taken)
        assertEquals(yesterday.atTime(8, 3), morning.takenAt)
        assertEquals(false, evening.taken)
        assertNull(evening.takenAt)
    }

    @Test
    fun dayRingsSummarizeTheDaysAroundTheSelectedOne() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0), LocalTime.of(20, 0))
        val yesterday = today.minusDays(1)

        val doses = repository.getDoseChecklistForDate(yesterday).first()
        repository.setDoseTaken(
            snapshotId = doses.first { it.scheduledTime == LocalTime.of(8, 0) }.snapshotId,
            scheduledDate = yesterday,
            scheduledTime = LocalTime.of(8, 0),
            taken = true,
            takenAt = yesterday.atTime(8, 3)
        )

        val state = awaitState(viewModel()) { it.progressByDate.isNotEmpty() }

        val yesterdayProgress = state.progressByDate.getValue(yesterday)
        assertEquals(2, yesterdayProgress.doseCount)
        assertEquals(1, yesterdayProgress.takenCount)
        assertEquals(0.5f, yesterdayProgress.progress, 0f)

        // Upcoming days list what is due, with nothing taken yet.
        val tomorrowProgress = state.progressByDate.getValue(today.plusDays(1))
        assertEquals(2, tomorrowProgress.doseCount)
        assertEquals(0, tomorrowProgress.takenCount)
    }

    @Test
    fun daysBeforeThePrescriptionExistedHaveNoRing() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0))

        val state = awaitState(viewModel()) { it.progressByDate.isNotEmpty() }

        // The prescription was entered on the 17th, so the 16th has nothing to show.
        assertNull(state.progressByDate[LocalDate.of(2026, 8, 16)])
        assertEquals(1, state.progressByDate.getValue(LocalDate.of(2026, 8, 17)).doseCount)
    }

    @Test
    fun refreshingRereadsTheClockSoTheDayDoesNotGoStale() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0))
        val viewModel = viewModel()
        assertEquals(today, awaitState(viewModel).today)

        // The screen stays open past midnight, then comes back to the foreground.
        now = LocalDateTime.of(2026, 8, 20, 9, 0)
        viewModel.refresh()

        val state = awaitState(viewModel) { it.now == now }
        assertEquals(LocalDate.of(2026, 8, 20), state.today)
    }

    private fun viewModel() = CalendarViewModel(
        repository,
        nowProvider = { now },
        listDebounceMillis = 0
    )

    private suspend fun awaitState(
        viewModel: CalendarViewModel,
        predicate: (CalendarUiState) -> Boolean = { true }
    ): CalendarUiState = withTimeout(AWAIT_TIMEOUT_MILLIS.milliseconds) {
        viewModel.uiState.first { !it.isLoading && predicate(it) }
    }

    private suspend fun addDailyPrescription(name: String, vararg times: LocalTime) {
        repository.addPrescription(
            Prescription(
                name = name,
                color = 0xFF102030,
                dosis = "1 pill",
                schedule = Schedule(
                    scheduleType = ScheduleType.DAILY,
                    timesOfDay = times.toList(),
                    startDate = insertedAt.toLocalDate()
                )
            )
        )
    }

    private companion object {
        const val AWAIT_TIMEOUT_MILLIS = 5_000L
    }
}
