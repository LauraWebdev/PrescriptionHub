package media.laura.prescriptionhub

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import media.laura.prescriptionhub.data.model.DoseDayProgress
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.LocalDate
import kotlin.math.abs

/**
 * The rolling row of day rings above the calendar list.
 *
 * @param selectedDate The day in the middle.
 * @param today The current date, telling past days from upcoming ones.
 * @param progressByDate Completion per day. A day that is absent has no dose scheduled.
 * @param onDateSelected Invoked with the day that reached the middle, or that was tapped.
 */
@Composable
fun DayProgressRow(
    selectedDate: LocalDate,
    today: LocalDate,
    progressByDate: Map<LocalDate, DoseDayProgress>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    // Slots are anchored on today, so a date keeps the same index for as long as the day lasts.
    val dateForIndex: (Int) -> LocalDate = remember(today) {
        { index -> today.plusDays((index - ANCHOR_INDEX).toLong()) }
    }
    val indexForDate: (LocalDate) -> Int = remember(today) {
        { date -> ANCHOR_INDEX + (date.toEpochDay() - today.toEpochDay()).toInt() }
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = indexForDate(selectedDate))
    val currentOnDateSelected by rememberUpdatedState(onDateSelected)

    // The ring nearest the middle of the viewport: the one the row is pointing at.
    val centeredIndex by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .minByOrNull { abs(it.offset + it.size / 2 - viewportCenter) }
                ?.index
        }
    }

    // Which rings currently overlap the highlight.
    val overlapDistancePx = with(LocalDensity.current) {
        (HIGHLIGHT_SIZE / 2 + INDICATOR_SIZE / 2).toPx()
    }
    val ringsOverHighlight by remember(listState, overlapDistancePx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
            layoutInfo.visibleItemsInfo
                .filter { abs(it.offset + it.size / 2 - viewportCenter) < overlapDistancePx }
                .map { it.index }
                .toSet()
        }
    }

    // Switch day the moment a ring reaches the middle
    LaunchedEffect(listState, dateForIndex) {
        snapshotFlow { centeredIndex }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { index -> currentOnDateSelected(dateForIndex(index)) }
    }

    LaunchedEffect(selectedDate, indexForDate) {
        val target = indexForDate(selectedDate)
        val current = centeredIndex
        if (current != target && !listState.isScrollInProgress) {
            if (current != null && abs(target - current) <= MAX_ANIMATED_DAYS) {
                listState.animateScrollToItem(target)
            } else {
                listState.scrollToItem(target)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Half the row minus half a slot on each side, so any ring can reach the middle.
        val sidePadding = (maxWidth - SLOT_SIZE) / 2

        // Current day background
        Box(
            modifier = Modifier
                .size(HIGHLIGHT_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )

        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = PaddingValues(horizontal = sidePadding),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            items(count = DAY_COUNT) { index ->
                val date = dateForIndex(index)
                DayProgressRing(
                    date = date,
                    progress = progressByDate[date],
                    isOverHighlight = index in ringsOverHighlight,
                    isUpcoming = date > today,
                    today = today,
                    onClick = { currentOnDateSelected(date) }
                )
            }
        }
    }
}

/**
 * One day as a ring: how much of it was taken.
 *
 * @param isOverHighlight Whether this ring currently overlaps the row's filled circle
 */
@Composable
private fun DayProgressRing(
    date: LocalDate,
    progress: DoseDayProgress?,
    isOverHighlight: Boolean,
    isUpcoming: Boolean,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val share by animateFloatAsState(
        targetValue = progress?.progress ?: 0f,
        label = "dayProgress"
    )
    val isMuted = isUpcoming || progress == null
    val indicatorColor = when {
        !isMuted -> MaterialTheme.colorScheme.primary
        isOverHighlight -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = MUTED_ALPHA)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = MUTED_ALPHA)
    }
    val trackColor = when {
        isOverHighlight -> MaterialTheme.colorScheme.primary.copy(alpha = HIGHLIGHTED_TRACK_ALPHA)
        isMuted -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = MUTED_TRACK_ALPHA)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    Box(
        modifier = modifier.size(SLOT_SIZE),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(TOUCH_SIZE)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = describeDay(date, progress, today) },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { share },
                color = indicatorColor,
                trackColor = trackColor,
                strokeWidth = 4.dp,
                modifier = Modifier
                    .size(INDICATOR_SIZE)
                    // The ring already speaks its day and count, so drop the raw progress reading.
                    .clearAndSetSemantics {}
            )
        }
    }
}

/** What a screen reader says about a day ring. */
private fun describeDay(date: LocalDate, progress: DoseDayProgress?, today: LocalDate): String {
    val day = formatCalendarDate(date = date, today = today)
    return if (progress == null) {
        "$day, nothing scheduled"
    } else {
        "$day, ${progress.takenCount} of ${progress.doseCount} taken"
    }
}

private val SLOT_SIZE: Dp = 72.dp
private val TOUCH_SIZE: Dp = 48.dp
private val INDICATOR_SIZE: Dp = 36.dp
private val HIGHLIGHT_SIZE: Dp = 60.dp
private const val MUTED_ALPHA = 0.35f
private const val MUTED_TRACK_ALPHA = 0.5f
private const val HIGHLIGHTED_TRACK_ALPHA = 0.3f

/* Distances further than this don't animate the UI. */
private const val MAX_ANIMATED_DAYS = 14

/** Roughly 274 years either way: far beyond what anyone scrolls, so the row has no reachable end. */
private const val DAY_COUNT = 200_001
private const val ANCHOR_INDEX = DAY_COUNT / 2

@Preview(showBackground = true)
@Composable
fun DayProgressRowPreview() {
    val today = LocalDate.of(2026, 8, 19)
    PrescriptionHubTheme {
        DayProgressRow(
            selectedDate = today,
            today = today,
            progressByDate = listOf(
                DoseDayProgress(today.minusDays(2), doseCount = 4, takenCount = 3),
                DoseDayProgress(today.minusDays(1), doseCount = 4, takenCount = 4),
                DoseDayProgress(today, doseCount = 6, takenCount = 2),
                DoseDayProgress(today.plusDays(1), doseCount = 6, takenCount = 0),
                DoseDayProgress(today.plusDays(2), doseCount = 6, takenCount = 0)
            ).associateBy { it.date },
            onDateSelected = {}
        )
    }
}
