package media.laura.prescriptionhub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import media.laura.prescriptionhub.ui.theme.PrescriptionHubTheme

/**
 * Preset swatches offered by the color picker.
 */
val prescriptionColors: List<Color> = listOf(
    Color(0xFFE53935), // red
    Color(0xFFF4511E), // deep orange
    Color(0xFFFB8C00), // orange
    Color(0xFFFDD835), // yellow
    Color(0xFF7CB342), // light green
    Color(0xFF43A047), // green
    Color(0xFF00897B), // teal
    Color(0xFF00ACC1), // cyan
    Color(0xFF1E88E5), // blue
    Color(0xFF3949AB), // indigo
    Color(0xFF8E24AA), // purple
    Color(0xFFD81B60), // pink
    Color(0xFF6D4C41)  // brown
)

/** Accessibility labels for [prescriptionColors]. */
private val prescriptionColorNames: List<String> = listOf(
    "Red", "Deep orange", "Orange", "Yellow", "Light green", "Green", "Teal",
    "Cyan", "Blue", "Indigo", "Purple", "Pink", "Brown"
)

/** Converts a Compose [Color] into the 32-bit ARGB [Long] stored on a prescription. */
fun Color.toStoredLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

/** Converts a stored 32-bit ARGB [Long] back into a Compose [Color]. */
fun Long.toComposeColor(): Color = Color(toInt())

/** Picks black or white so a check mark stays readable on top of [background]. */
private fun checkColorOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color.Black else Color.White

/**
 * A wrapping row of circular color swatches + custom color.
 *
 * @param selected The currently selected color.
 * @param onSelect Invoked with the newly picked preset color.
 * @param onCustomClick Invoked when the trailing custom-color swatch is tapped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorSwatchPicker(
    selected: Color,
    onSelect: (Color) -> Unit,
    onCustomClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCustomSelected = prescriptionColors.none { it.toStoredLong() == selected.toStoredLong() }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        prescriptionColors.forEachIndexed { index, swatch ->
            Surface(
                onClick = { onSelect(swatch) },
                shape = CircleShape,
                color = swatch,
                modifier = Modifier
                    .size(40.dp)
                    .semantics { contentDescription = prescriptionColorNames[index] }
            ) {
                if (swatch.toStoredLong() == selected.toStoredLong()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = checkColorOn(swatch),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Surface(
            onClick = onCustomClick,
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Custom color" }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        if (isCustomSelected) {
                            Brush.linearGradient(listOf(selected, selected))
                        } else {
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047),
                                    Color(0xFF00ACC1), Color(0xFF3949AB), Color(0xFF8E24AA),
                                    Color(0xFFE53935)
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCustomSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = checkColorOn(selected),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Dialog for picking an arbitrary color via hue / saturation / brightness sliders.
 *
 * @param initial The color the sliders start from.
 * @param onConfirm Invoked with the picked color when the user confirms.
 * @param onDismiss Invoked when the dialog is cancelled.
 */
@Composable
fun CustomColorDialog(
    initial: Color,
    onConfirm: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) }
    }
    var hue by remember(initial) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initial) { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember(initial) { mutableFloatStateOf(initialHsv[2]) }

    val current = Color.hsv(hue, saturation, brightness)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Custom color") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = current,
                    modifier = Modifier
                        .size(72.dp)
                        .semantics { contentDescription = "Selected color preview" }
                ) {}

                GradientSlider(
                    label = "Hue",
                    value = hue,
                    valueRange = 0f..360f,
                    colors = List(7) { Color.hsv((it * 60f) % 360f, 1f, 1f) },
                    onValueChange = { hue = it }
                )
                GradientSlider(
                    label = "Saturation",
                    value = saturation,
                    valueRange = 0f..1f,
                    colors = listOf(
                        Color.hsv(hue, 0f, brightness),
                        Color.hsv(hue, 1f, brightness)
                    ),
                    onValueChange = { saturation = it }
                )
                GradientSlider(
                    label = "Brightness",
                    value = brightness,
                    valueRange = 0f..1f,
                    colors = listOf(
                        Color.hsv(hue, saturation, 0f),
                        Color.hsv(hue, saturation, 1f)
                    ),
                    onValueChange = { brightness = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) {
                Text(text = "Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

/**
 * A labeled slider whose track is painted with the gradient it spans.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    colors: List<Color>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(colors))
                )
            },
            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onSurface)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ColorSwatchPickerPreview() {
    PrescriptionHubTheme {
        ColorSwatchPicker(
            selected = prescriptionColors[5],
            onSelect = {},
            onCustomClick = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun CustomColorDialogPreview() {
    PrescriptionHubTheme {
        CustomColorDialog(
            initial = prescriptionColors[8],
            onConfirm = {},
            onDismiss = {}
        )
    }
}
