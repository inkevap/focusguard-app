package com.focusguard.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.DurationSlider
import com.focusguard.ui.components.StepperField
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.formatLocal
import com.focusguard.ui.rememberTrustedNowMs
import java.time.Instant

/**
 * Duración en minutos: atajos, un deslizador, «− +» para afinar de 5 en 5 y la hora a la que terminaría («Hasta las 18:42»).
 * Lo usan Bloquear ya, el botón de pánico y el temporizador.
 */
@Composable
internal fun DurationChoice(minutes: String, onChange: (String) -> Unit, presets: List<Int>, maxMinutes: Int = MAX_LOCK_MINUTES) =
    DurationChoiceContent(minutes, onChange, presets, maxMinutes, rememberTrustedNowMs(periodMs = 30_000L))

/** Lo mismo con la hora actual ya resuelta, para poder pintarlo sin reloj de la app. */
@Composable
internal fun DurationChoiceContent(minutes: String, onChange: (String) -> Unit, presets: List<Int>, maxMinutes: Int, nowMs: Long) {
    val options = remember(presets) { presets.map { it.toString() to formatAmountOfTime(it.toLong()) } }
    val value = minutes.toIntOrNull()?.takeIf { it in 1..maxMinutes }
    Column {
        ChipChoices(options = options, selected = minutes, onSelect = onChange, modifier = Modifier.padding(top = 8.dp))
        if (value != null) {
            Text(formatAmountOfTime(value.toLong()), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
            Hint("Hasta las ${formatLocal(Instant.ofEpochMilli(nowMs).plusSeconds(value * 60L))}")
        }
        DurationSlider(minutes, onChange, maxMinutes)
        StepperField("Minutos", minutes, onChange, Modifier.padding(top = 4.dp), step = 5, minimum = 1, maximum = maxMinutes)
    }
}

/** El tope de un bloqueo que no se puede deshacer. */
internal const val MAX_LOCK_MINUTES = 24 * 60

/**
 * Elegir cuánto dura un bloqueo que no se puede deshacer (Ayúdame): atajos, otro valor, y un botón que pide confirmar
 * antes de empezar. [onStart] recibe los minutos (entre 1 y [MAX_LOCK_MINUTES]).
 */
@Composable
internal fun IrreversibleDurationForm(presets: List<Int>, defaultMinutes: Int, hint: String, buttonLabel: String, onStart: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf(defaultMinutes.toString()) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val value = minutes.toIntOrNull()?.takeIf { it in 1..MAX_LOCK_MINUTES }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppCard {
            SectionTitle("Cuánto tiempo")
            Hint(hint)
            DurationChoice(minutes, { minutes = it }, presets)
            if (value == null && minutes.isNotBlank()) Hint("Entre 1 minuto y ${formatAmountOfTime(MAX_LOCK_MINUTES.toLong())}.")
        }
        PrimaryButton(buttonLabel, { confirm = true }, Modifier.fillMaxWidth(), enabled = value != null)
    }
    if (confirm && value != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Bloquear todo durante ${formatAmountOfTime(value.toLong())}?") },
            text = { Text("No podrás deshacerlo hasta que termine el tiempo.") },
            confirmButton = { TextButton(onClick = { confirm = false; onStart(value) }) { Text(buttonLabel) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
