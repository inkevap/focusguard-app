package com.focusguard.ui.profiles

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.model.EndPolicy
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.StepperField
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.plural
import com.focusguard.decision.Pomodoro
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton

private val presetMinutes = listOf(15, 30, 60, 120)

@Composable
fun SessionScreen(viewModel: SessionViewModel, pomodoro: Boolean, onDone: () -> Unit) {
    var showAppPicker by remember { mutableStateOf(false) }
    var minutes by remember { mutableStateOf("30") }
    var focus by remember { mutableStateOf("25") }
    var rest by remember { mutableStateOf("5") }
    var sessions by remember { mutableStateOf("4") }
    var confirmFirm by remember { mutableStateOf(false) }

    val minutesValue = minutes.toIntOrNull()?.takeIf { it > 0 }
    val focusValue = focus.toIntOrNull() ?: 0
    val restValue = rest.toIntOrNull() ?: -1
    val sessionsValue = sessions.toIntOrNull() ?: 0
    val pomodoroValid = focusValue > 0 && restValue >= 0 && sessionsValue > 0

    Scaffold(
        topBar = {
            FgTopBar(if (pomodoro) "Enfoque" else "Bloquear ya", onBack = onDone)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    SectionTitle(if (pomodoro) "Cómo funciona" else "Cuánto tiempo")
                    if (pomodoro) {
                        Hint("Alternas tiempo de foco, donde se bloquea lo que elijas, con descansos, donde no.")
                        ChipChoices(
                            options = Pomodoro.PRESETS.map { (f, r) -> "$f/$r" to "$f/$r" },
                            selected = "$focusValue/$restValue",
                            onSelect = { choice -> choice.split('/').let { focus = it[0]; rest = it[1] } },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        StepperField("Minutos de foco", focus, { focus = it }, Modifier.padding(top = 8.dp), step = 5, minimum = 1, maximum = 240)
                        StepperField("Minutos de descanso", rest, { rest = it }, Modifier.padding(top = 8.dp), step = 5, minimum = 0, maximum = 120)
                        StepperField("Sesiones de foco", sessions, { sessions = it }, Modifier.padding(top = 8.dp), step = 1, minimum = 1, maximum = 20)
                        if (pomodoroValid) {
                            val total = Pomodoro.totalMinutes(focusValue, restValue, sessionsValue)
                            Hint("${plural(sessionsValue, "bloque", "bloques")} · ${formatAmountOfTime(total.toLong())} en total")
                        }
                    } else {
                        DurationChoice(minutes, { minutes = it }, presetMinutes)
                    }
                }
            }
            item { AppCard { SectionTitle("Qué hace"); ModePicker(viewModel.mode, viewModel::chooseMode) } }
            item {
                AppCard {
                    SectionTitle("Qué incluir")
                    SecondaryButton(
                        text = if (viewModel.apps.isEmpty()) "Elegir apps" else "Apps elegidas: ${viewModel.apps.size}",
                        onClick = { showAppPicker = true },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    WebRulesEditor(viewModel.webs, viewModel::addWeb, viewModel::removeWeb)
                }
            }
            if (!pomodoro) item { EndPolicyCard(viewModel) }
            viewModel.message?.let { problem ->
                item { Text(problem, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            }
            item {
                PrimaryButton(
                    text = if (pomodoro) "Empezar enfoque" else "Bloquear ahora",
                    onClick = {
                        viewModel.clearMessage()
                        if (pomodoro) viewModel.startPomodoro(focus.toInt(), rest.toInt(), sessions.toInt(), onDone)
                        // Sin vuelta atrás (o solo con condiciones): se pide confirmar antes de empezar.
                        else if (viewModel.endPolicy == EndPolicy.ANYTIME) viewModel.startQuickBlock(minutesValue ?: return@PrimaryButton, onDone)
                        else confirmFirm = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = if (pomodoro) pomodoroValid else minutesValue != null,
                )
            }
        }
    }

    if (confirmFirm) {
        val onlyTime = viewModel.endPolicy == EndPolicy.TIME_ONLY
        AlertDialog(
            onDismissRequest = { confirmFirm = false },
            title = { Text("¿Bloquear ${minutesValue?.let { formatAmountOfTime(it.toLong()) } ?: ""}?") },
            text = {
                Text(
                    if (onlyTime) "No podrás terminarlo antes, ni con una aprobación."
                    else "Solo terminará antes si se cumple una de las condiciones que elegiste. No se puede cambiar después.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmFirm = false; viewModel.startQuickBlock(minutesValue ?: return@TextButton, onDone) }) { Text("Bloquear") }
            },
            dismissButton = { TextButton(onClick = { confirmFirm = false }) { Text("Cancelar") } },
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            selected = viewModel.apps,
            onConfirm = { viewModel.chooseApps(it); showAppPicker = false },
            onDismiss = { showAppPicker = false },
        )
    }
}
