package media.laura.prescriptionhub

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import media.laura.prescriptionhub.data.local.PrescriptionDatabase
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.data.model.DoseDayProgress
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import media.laura.prescriptionhub.data.repository.PrescriptionRepository
import media.laura.prescriptionhub.data.repository.PrescriptionService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CalendarWindowAndLoadingTest {

    private lateinit var database: PrescriptionDatabase
    private lateinit var counting: CountingPrescriptionService

    private val now = LocalDateTime.of(2026, 8, 19, 9, 0)
    private val today: LocalDate get() = now.toLocalDate()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PrescriptionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        counting = CountingPrescriptionService(
            PrescriptionRepository(
                prescriptionDao = database.prescriptionDao(),
                nowProvider = { now },
                workDispatcher = UnconfinedTestDispatcher()
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun scrollingWithinTheWindowDoesNotReloadIt() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0))
        val viewModel = viewModel()
        awaitLoaded(viewModel)

        val subscriptionsAfterFirstLoad = counting.progressSubscriptions
        // Guards against the assertion below passing because nothing was ever counted.
        assertTrue(subscriptionsAfterFirstLoad >= 1)

        (1..15).forEach { offset -> viewModel.selectDate(today.plusDays(offset.toLong())) }
        val state = awaitLoaded(viewModel) { it.selectedDate == today.plusDays(15) }

        assertEquals(subscriptionsAfterFirstLoad, counting.progressSubscriptions)
        assertTrue(state.progressByDate.isNotEmpty())
    }

    @Test
    fun scrollingBeyondTheMarginReloadsTheWindow() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0))
        val viewModel = viewModel()
        awaitLoaded(viewModel)

        val subscriptionsAfterFirstLoad = counting.progressSubscriptions
        val faraway = today.plusDays(30)
        viewModel.selectDate(faraway)
        val state = awaitLoaded(viewModel) { it.selectedDate == faraway }

        assertTrue(counting.progressSubscriptions > subscriptionsAfterFirstLoad)
        assertTrue(state.progressByDate.containsKey(faraway))
    }

    @Test
    fun theRingRowKeepsItsDataWhileTheListLoadsAnotherDay() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0), LocalTime.of(20, 0))
        val viewModel = viewModel()
        awaitLoaded(viewModel)

        val target = today.plusDays(3)
        viewModel.selectDate(target)

        // Every state from here on must still have rings, loading or not.
        val states = mutableListOf<CalendarUiState>()
        withTimeout(AWAIT_TIMEOUT_MILLIS.milliseconds) {
            viewModel.uiState.first { state ->
                states.add(state)
                !state.isLoading && state.selectedDate == target
            }
        }

        assertTrue(states.isNotEmpty())
        assertTrue(states.all { it.progressByDate.isNotEmpty() })
        assertEquals(target, states.last().loadedDate)
        assertFalse(states.last().isLoading)
    }

    @Test
    fun theStateReportsLoadingUntilTheListMatchesTheSelection() {
        val state = CalendarUiState(selectedDate = today, now = now)
        assertTrue(state.isLoading)
        assertTrue(state.copy(loadedDate = today.minusDays(1)).isLoading)
        assertFalse(state.copy(loadedDate = today).isLoading)
    }

    @Test
    fun checkingAWholeGroupWritesItInOneCall() = runBlocking {
        addDailyPrescription("Metformin", LocalTime.of(8, 0))
        addDailyPrescription("Candecor", LocalTime.of(8, 0))
        addDailyPrescription("Progesteron", LocalTime.of(8, 0))
        val viewModel = TodayViewModel(counting, nowProvider = { now })

        val group = withTimeout(AWAIT_TIMEOUT_MILLIS.milliseconds) {
            viewModel.uiState.first { !it.isLoading && it.groups.size == 1 }.groups.single()
        }
        assertEquals(3, group.doses.size)
        viewModel.checkAll(group)

        withTimeout(AWAIT_TIMEOUT_MILLIS.milliseconds) {
            viewModel.uiState.first { state -> state.groups.singleOrNull()?.isComplete == true }
        }

        assertEquals(1, counting.setDosesTakenCalls)
        assertEquals(0, counting.setDoseTakenCalls)
    }

    private fun viewModel() = CalendarViewModel(
        counting,
        nowProvider = { now },
        listDebounceMillis = 0
    )

    private suspend fun awaitLoaded(
        viewModel: CalendarViewModel,
        predicate: (CalendarUiState) -> Boolean = { true }
    ): CalendarUiState = withTimeout(AWAIT_TIMEOUT_MILLIS.milliseconds) {
        viewModel.uiState.first { !it.isLoading && predicate(it) }
    }

    private suspend fun addDailyPrescription(name: String, vararg times: LocalTime) {
        counting.addPrescription(
            Prescription(
                name = name,
                color = 0xFF102030,
                dosis = "1 pill",
                schedule = Schedule(
                    scheduleType = ScheduleType.DAILY,
                    timesOfDay = times.toList(),
                    startDate = today
                )
            )
        )
    }

    private class CountingPrescriptionService(
        private val delegate: PrescriptionService
    ) : PrescriptionService by delegate {

        private val progressSubscriptionCount = AtomicInteger()
        private val singleWrites = AtomicInteger()
        private val batchWrites = AtomicInteger()

        val progressSubscriptions: Int get() = progressSubscriptionCount.get()
        val setDoseTakenCalls: Int get() = singleWrites.get()
        val setDosesTakenCalls: Int get() = batchWrites.get()

        override fun getDoseProgressForDates(
            startDate: LocalDate,
            endDate: LocalDate
        ): Flow<List<DoseDayProgress>> =
            delegate.getDoseProgressForDates(startDate, endDate)
                .onStart { progressSubscriptionCount.incrementAndGet() }

        override suspend fun setDoseTaken(
            snapshotId: Long,
            scheduledDate: LocalDate,
            scheduledTime: LocalTime,
            taken: Boolean,
            takenAt: LocalDateTime?
        ) {
            singleWrites.incrementAndGet()
            delegate.setDoseTaken(snapshotId, scheduledDate, scheduledTime, taken, takenAt)
        }

        override suspend fun setDosesTaken(doses: List<DoseChecklistItem>, taken: Boolean) {
            batchWrites.incrementAndGet()
            delegate.setDosesTaken(doses, taken)
        }
    }

    private companion object {
        const val AWAIT_TIMEOUT_MILLIS = 5_000L
    }
}
