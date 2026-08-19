package media.laura.prescriptionhub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun getDayOfMonthSuffix(day: Int): String {
    if (day in 11..13) return "th"
    return when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
}

fun formatCalendarDate(date: LocalDate): String {
    val day = date.dayOfMonth
    val suffix = getDayOfMonthSuffix(day)
    val month = date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    val year = date.year
    return "$day$suffix $month $year"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    initialDate: LocalDate = LocalDate.now(),
    onDateChange: (LocalDate) -> Unit = {}
) {
    var selectedDate by remember { mutableStateOf(initialDate) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = formatCalendarDate(selectedDate)) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            val newDate = selectedDate.minusDays(1)
                            selectedDate = newDate
                            onDateChange(newDate)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Day"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val newDate = selectedDate.plusDays(1)
                            selectedDate = newDate
                            onDateChange(newDate)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Day"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Calendar",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CalendarScreenPreview() {
    PrescriptionHubTheme {
        CalendarScreen(initialDate = LocalDate.of(2026, 8, 19))
    }
}
