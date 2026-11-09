package com.focusguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusguard.decision.DurationScale
import com.focusguard.decision.TimeRanges
import com.focusguard.ui.Days
import com.focusguard.ui.formatMinutes
import java.time.DayOfWeek
import kotlin.math.roundToInt

/** Los siete días como círculos (L M X J V S D) que se tocan para marcar, con atajos para los grupos de siempre. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayCircles(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit, modifier: Modifier = Modifier) {
    val weekdays = DayOfWeek.entries.filter { it.value <= 5 }.toSet()
    val weekend = DayOfWeek.entries.toSet() - weekdays
    androidx.compose.foundation.layout.Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.entries.forEach { day ->
                DayCircle(day, day in days, { onChange(if (day in days) days - day else days + day) }, Modifier.weight(1f))
            }
        }
        FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onChange(weekdays) }, label = { Text("Laborables") })
            AssistChip(onClick = { onChange(weekend) }, label = { Text("Fines de semana") })
            AssistChip(onClick = { onChange(DayOfWeek.entries.toSet()) }, label = { Text("Todos") })
        }
    }
}

@Composable
private fun DayCircle(day: DayOfWeek, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surfaceVariant)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = Days.word(day) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Days.letter(day),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/** «Desde 09:00» / «Hasta 17:00»: un botón que abre el selector de hora de Material en vez de pedir que se teclee. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeButton(label: String, minute: Int?, fallbackMinute: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text("$label ${minute?.let(::formatMinutes) ?: "--:--"}")
    }
    if (open) {
        val initial = (minute ?: fallbackMinute).coerceIn(0, 24 * 60 - 1)
        val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        // En pantallas bajas (horizontal) el reloj no cabe: se ofrece el teclado numérico del selector.
        val compact = LocalConfiguration.current.screenHeightDp < 600
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { if (compact) TimeInput(state) else TimePicker(state) },
            confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute); open = false }) { Text("Aceptar") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        )
    }
}

/** Las 24 horas en una barra con el rango marcado; si cruza la medianoche se ven los dos tramos. */
@Composable
fun DayRangeBar(startMinute: Int?, endMinute: Int?, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).clearAndSetSemantics { }) {
            drawRect(track)
            if (startMinute != null && endMinute != null) {
                TimeRanges.segments(startMinute, endMinute).forEach { part ->
                    drawRect(fill, topLeft = Offset(size.width * part.start, 0f), size = Size(size.width * (part.endInclusive - part.start), size.height))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp).clearAndSetSemantics { }, horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { Text("$it h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (startMinute != null && endMinute != null) {
            Text(TimeRanges.describe(startMinute, endMinute), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Un número de minutos (o de veces) con «−» y «+»: se puede teclear, pero no hace falta. [step] es lo que suma o resta cada pulsación.
 * El texto sigue siendo el estado del llamador, como en [NumberField].
 */
@Composable
fun StepperField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 5,
    minimum: Int = 1,
    maximum: Int = 1440,
) {
    val current = value.toIntOrNull()
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { onChange(DurationScale.step(current, -1, step, minimum, maximum).toString()) },
            enabled = current == null || current > minimum,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Menos $step: $label" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        NumberField(label, value, onChange, Modifier.weight(1f), minimum = minimum)
        OutlinedButton(
            onClick = { onChange(DurationScale.step(current, 1, step, minimum, maximum).toString()) },
            enabled = current == null || current < maximum,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Más $step: $label" },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

/** Deslizador de duración con las marcas de [DurationScale]; ajusta [minutes] (texto) a la marca más cercana. */
@Composable
fun DurationSlider(minutes: String, onChange: (String) -> Unit, maxMinutes: Int, modifier: Modifier = Modifier) {
    val stops = remember(maxMinutes) { DurationScale.stops(maxMinutes) }
    val index = DurationScale.nearestIndex(stops, minutes.toIntOrNull() ?: stops.first())
    Slider(
        value = index.toFloat(),
        onValueChange = { onChange(stops[it.roundToInt().coerceIn(0, stops.lastIndex)].toString()) },
        valueRange = 0f..stops.lastIndex.toFloat(),
        modifier = modifier.semantics { contentDescription = "Duración" },
    )
}
