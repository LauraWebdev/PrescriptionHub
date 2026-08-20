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
import media.laura.prescriptionhub.data.model.DoseChecklistItem
import media.laura.prescriptionhub.data.model.Prescription
import media.laura.prescriptionhub.data.model.Schedule
import media.laura.prescriptionhub.data.model.ScheduleType
import media.laura.prescriptionhub.data.repository.PrescriptionRepository
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
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TodayViewModelTest {

    private lateinit var database: PrescriptionDatabase
    private lateinit var repository: PrescriptionRepository

    private var now = LocalDateTime.of(2026, 8, 19, 7, 0)
    private val date: LocalDate get() = now.toLocalDate()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            PrescriptionDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = PrescriptionRepository(database.prescriptionDao(), nowProvider = { now })
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun dosesAreGroupedByTimeOfDay() = runBlocking {
        addPrescription("Metformin", LocalTime.of(8, 0), LocalTime.of(23, 0))
        addPrescription("Candecor", LocalTime.of(8, 0))
        val viewModel = TodayViewModel(repository, nowProvider = { now })

        val groups = awaitGroups(viewModel) { it.size == 2 }

        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(23, 0)), groups.map { it.time })
        assertEquals(listOf(2, 1), groups.map { it.doses.size })
    }

    @Test
    fun checkingADoseStoresTheTakenTimestamp() = runBlocking {
        addPrescription("Metformin", LocalTime.of(8, 0))
        val viewModel = TodayViewModel(repository, nowProvider = { now })
        now = LocalDateTime.of(2026, 8, 19, 8, 4)

        viewModel.setDoseTaken(dose(viewModel, "Metformin"), taken = true)

        val taken = awaitDose(viewModel, "Metformin") { it.taken }
        assertTrue(taken.taken)
        assertEquals(LocalDateTime.of(2026, 8, 19, 8, 4), taken.takenAt)
    }

    @Test
    fun uncheckingADoseClearsTheTakenTimestamp() = runBlocking {
        addPrescription("Metformin", LocalTime.of(8, 0))
        val viewModel = TodayViewModel(repository, nowProvider = { now })

        viewModel.setDoseTaken(dose(viewModel, "Metformin"), taken = true)
        awaitDose(viewModel, "Metformin") { it.taken }
        viewModel.setDoseTaken(dose(viewModel, "Metformin"), taken = false)

        val open = awaitDose(viewModel, "Metformin") { !it.taken }
        assertFalse(open.taken)
        assertNull(open.takenAt)
    }

    @Test
    fun checkAllTakesEveryOpenDoseOfTheGroup() = runBlocking {
        addPrescription("Metformin", LocalTime.of(8, 0))
        addPrescription("Candecor", LocalTime.of(8, 0))
        addPrescription("Progesteron", LocalTime.of(23, 0))
        val viewModel = TodayViewModel(repository, nowProvider = { now })

        viewModel.checkAll(awaitGroups(viewModel) { it.size == 2 }.first())

        val groups = awaitGroups(viewModel) { it.first().isComplete }
        assertTrue(groups.first().isComplete)
        assertFalse(groups.last().isComplete)
    }

    @Test
    fun checkAllKeepsTheEarlierTimestampOfAlreadyTakenDoses() = runBlocking {
        addPrescription("Metformin", LocalTime.of(8, 0))
        addPrescription("Candecor", LocalTime.of(8, 0))
        val viewModel = TodayViewModel(repository, nowProvider = { now })

        now = LocalDateTime.of(2026, 8, 19, 8, 4)
        viewModel.setDoseTaken(dose(viewModel, "Metformin"), taken = true)
        awaitDose(viewModel, "Metformin") { it.taken }

        now = LocalDateTime.of(2026, 8, 19, 9, 30)
        viewModel.checkAll(awaitGroups(viewModel).single())

        val candecor = awaitDose(viewModel, "Candecor") { it.taken }
        assertEquals(LocalDateTime.of(2026, 8, 19, 9, 30), candecor.takenAt)
        assertEquals(
            LocalDateTime.of(2026, 8, 19, 8, 4),
            dose(viewModel, "Metformin").takenAt
        )
    }

    @Test
    fun checkAllLeavesTheListUnchanged() = runBlocking {
        addPrescription("Metformin", LocalTime.of(8, 0))
        addPrescription("Candecor", LocalTime.of(8, 0))
        addPrescription("Progesteron", LocalTime.of(23, 0))
        val viewModel = TodayViewModel(repository, nowProvider = { now })

        val before = awaitGroups(viewModel) { it.size == 2 }
        before.forEach { viewModel.checkAll(it) }
        val after = awaitGroups(viewModel) { groups -> groups.all { it.isComplete } }

        assertEquals(before.map { it.time }, after.map { it.time })
        assertEquals(before.map { it.doses.size }, after.map { it.doses.size })
        assertTrue(after.all { it.isComplete })
    }

    @Test
    fun addingAPrescriptionUpdatesTheChecklistWithoutReload() = runBlocking {
        addPrescription("Gynokadin", LocalTime.of(23, 0))
        val viewModel = TodayViewModel(repository, nowProvider = { now })
        assertEquals(1, awaitGroups(viewModel) { it.size == 1 }.size)

        // Entered in the evening, after its slots have passed: the day stays complete.
        now = LocalDateTime.of(2026, 8, 19, 17, 37)
        addPrescription("Lorem Ipsum 3", LocalTime.of(6, 0), LocalTime.of(12, 0), LocalTime.of(15, 0))

        val groups = awaitGroups(viewModel) { it.size == 4 }
        assertEquals(
            listOf(LocalTime.of(6, 0), LocalTime.of(12, 0), LocalTime.of(15, 0), LocalTime.of(23, 0)),
            groups.map { it.time }
        )
    }

    /**
     * Waits for a loaded state matching [predicate]. Room publishes its updates asynchronously, so
     * reading the state flow's current value right after a write could still see the previous one.
     */
    private suspend fun awaitGroups(
        viewModel: TodayViewModel,
        predicate: (List<DoseTimeGroup>) -> Boolean = { true }
    ): List<DoseTimeGroup> = withTimeout(AWAIT_TIMEOUT_MILLIS.milliseconds) {
        viewModel.uiState.first { !it.isLoading && predicate(it.groups) }.groups
    }

    private suspend fun awaitDose(
        viewModel: TodayViewModel,
        name: String,
        predicate: (DoseChecklistItem) -> Boolean
    ): DoseChecklistItem {
        val groups = awaitGroups(viewModel) { groups ->
            groups.flatMap { it.doses }.any { it.prescriptionName == name && predicate(it) }
        }
        return groups.flatMap { it.doses }.first { it.prescriptionName == name }
    }

    private suspend fun dose(viewModel: TodayViewModel, name: String): DoseChecklistItem =
        awaitGroups(viewModel) { groups ->
            groups.flatMap { it.doses }.any { it.prescriptionName == name }
        }.flatMap { it.doses }.first { it.prescriptionName == name }

    private suspend fun addPrescription(name: String, vararg times: LocalTime) {
        repository.addPrescription(
            Prescription(
                name = name,
                color = 0xFF102030,
                dosis = "1 pill",
                schedule = Schedule(
                    scheduleType = ScheduleType.DAILY,
                    timesOfDay = times.toList(),
                    startDate = date
                )
            )
        )
    }

    private companion object {
        const val AWAIT_TIMEOUT_MILLIS = 5_000L
    }
}
