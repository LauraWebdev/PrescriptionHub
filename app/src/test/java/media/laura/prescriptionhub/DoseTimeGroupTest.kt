package media.laura.prescriptionhub

import media.laura.prescriptionhub.data.model.DoseChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Test

class DoseTimeGroupTest {

    @Test
    fun dosesAreSplitIntoOneGroupPerTimeOfDay() {
        val groups = groupDosesByTime(
            listOf(
                dose(name = "Metformin", time = LocalTime.of(8, 0)),
                dose(name = "Candecor", time = LocalTime.of(8, 0)),
                dose(name = "Progesteron", time = LocalTime.of(23, 0))
            )
        )

        assertEquals(listOf(2, 1), groups.map { it.doses.size })
    }

    @Test
    fun groupsAreOrderedEarliestFirstRegardlessOfInputOrder() {
        val groups = groupDosesByTime(
            listOf(
                dose(name = "Progesteron", time = LocalTime.of(23, 0)),
                dose(name = "Metformin", time = LocalTime.of(8, 0)),
                dose(name = "Candecor", time = LocalTime.of(12, 30))
            )
        )

        assertEquals(
            listOf(LocalTime.of(8, 0), LocalTime.of(12, 30), LocalTime.of(23, 0)),
            groups.map { it.time }
        )
    }

    @Test
    fun dosesInsideAGroupAreSortedByName() {
        val groups = groupDosesByTime(
            listOf(
                dose(name = "Progesteron", time = LocalTime.of(8, 0)),
                dose(name = "Candecor", time = LocalTime.of(8, 0)),
                dose(name = "Metformin", time = LocalTime.of(8, 0))
            )
        )

        assertEquals(
            listOf("Candecor", "Metformin", "Progesteron"),
            groups.single().doses.map { it.prescriptionName }
        )
    }

    @Test
    fun takenDosesStayInTheirGroup() {
        val groups = groupDosesByTime(
            listOf(
                dose(name = "Metformin", time = LocalTime.of(8, 0), taken = true),
                dose(name = "Candecor", time = LocalTime.of(8, 0), taken = true),
                dose(name = "Progesteron", time = LocalTime.of(8, 0), taken = false)
            )
        )

        assertEquals(3, groups.single().doses.size)
    }

    @Test
    fun groupWithoutTakenDosesHasNoProgress() {
        val group = groupDosesByTime(
            listOf(
                dose(name = "Metformin", time = LocalTime.of(8, 0)),
                dose(name = "Candecor", time = LocalTime.of(8, 0))
            )
        ).single()

        assertEquals(0, group.takenCount)
        assertEquals(0f, group.progress, 0f)
        assertFalse(group.isComplete)
    }

    @Test
    fun partiallyTakenGroupReportsItsShare() {
        val group = groupDosesByTime(
            listOf(
                dose(name = "Metformin", time = LocalTime.of(8, 0), taken = true),
                dose(name = "Candecor", time = LocalTime.of(8, 0), taken = false),
                dose(name = "Progesteron", time = LocalTime.of(8, 0), taken = false),
                dose(name = "Gynokadin", time = LocalTime.of(8, 0), taken = false)
            )
        ).single()

        assertEquals(1, group.takenCount)
        assertEquals(0.25f, group.progress, 0f)
        assertFalse(group.isComplete)
    }

    @Test
    fun fullyTakenGroupIsComplete() {
        val group = groupDosesByTime(
            listOf(
                dose(name = "Metformin", time = LocalTime.of(8, 0), taken = true),
                dose(name = "Candecor", time = LocalTime.of(8, 0), taken = true)
            )
        ).single()

        assertEquals(2, group.takenCount)
        assertEquals(1f, group.progress, 0f)
        assertTrue(group.isComplete)
    }

    @Test
    fun emptyGroupIsNeitherCompleteNorInProgress() {
        val group = DoseTimeGroup(time = LocalTime.of(8, 0), doses = emptyList())

        assertEquals(0f, group.progress, 0f)
        assertFalse(group.isComplete)
    }

    @Test
    fun emptyChecklistHasNoGroups() {
        assertTrue(groupDosesByTime(emptyList()).isEmpty())
    }

    private fun dose(
        name: String,
        time: LocalTime,
        taken: Boolean = false
    ) = DoseChecklistItem(
        snapshotId = name.hashCode().toLong(),
        prescriptionId = name.hashCode().toLong(),
        prescriptionName = name,
        prescriptionColor = 0xFF102030,
        dosis = "1 pill",
        scheduledDate = LocalDate.of(2026, 8, 19),
        scheduledTime = time,
        taken = taken,
        takenAt = if (taken) LocalDate.of(2026, 8, 19).atTime(time) else null
    )
}
