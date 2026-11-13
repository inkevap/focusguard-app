package com.focusguard.ui.profiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.decision.Pomodoro
import com.focusguard.model.BlockMode
import com.focusguard.model.EndPolicy
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.StepperField
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.plural

private val presetMinutes = listOf(15, 30, 60, 120)

/** Las subpantallas de Bloquear ya y de Enfoque. */
private enum class SessionPage(val title: String) {
    DURATION("Duración"),
    MODE("Qué hace"),
    APPS("Apps"),
    WEBS("Webs"),
    END("Cómo terminar"),
}

@Composable
fun SessionScreen(viewModel: SessionViewModel, pomodoro: Boolean, onDone: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<SessionPage?>(null) }
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
    val title = if (pomodoro) "Enfoque" else "Bloquear ya"

    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen(
                title,
                onBack = onDone,
                bottomBar = {
                    BottomAction {
                        viewModel.message?.let { ErrorNote(it, Modifier.padding(bottom = 4.dp)) }
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
                },
            ) {
                if (pomodoro) {
                    item {
                        FgGroup(header = "Cómo funciona", footer = "Alternas tiempo de foco, donde se bloquea lo que elijas, con descansos, donde no.") {
                            FgCell {
                                ChipChoices(
                                    options = Pomodoro.PRESETS.map { (f, r) -> "$f/$r" to "$f/$r" },
                                    selected = "$focusValue/$restValue",
                                    onSelect = { choice -> choice.split('/').let { focus = it[0]; rest = it[1] } },
                                )
                            }
                            FgCell { StepperField("Minutos de foco", focus, { focus = it }, step = 5, minimum = 1, maximum = 240) }
                            FgCell { StepperField("Minutos de descanso", rest, { rest = it }, step = 5, minimum = 0, maximum = 120) }
                            FgCell { StepperField("Sesiones de foco", sessions, { sessions = it }, step = 1, minimum = 1, maximum = 20) }
                        }
                    }
                    if (pomodoroValid) {
                        item {
                            val total = Pomodoro.totalMinutes(focusValue, restValue, sessionsValue)
                            FgNote("${plural(sessionsValue, "bloque", "bloques")} · ${formatAmountOfTime(total.toLong())} en total", Modifier.padding(horizontal = 16.dp))
                        }
                    }
                } else {
                    item {
                        FgGroup(header = "Cuánto tiempo") {
                            FgCell {
                                ChipChoices(
                                    options = presetMinutes.map { it.toString() to formatAmountOfTime(it.toLong()) },
                                    selected = minutes,
                                    onSelect = { minutes = it },
                                )
                            }
                            FgRow(
                                "Otra duración",
                                value = minutesValue?.let { formatAmountOfTime(it.toLong()) } ?: "Sin elegir",
                                onClick = { page = SessionPage.DURATION },
                            )
                        }
                    }
                }
                item {
                    FgGroup(header = "Qué") {
                        FgRow("Qué hace", value = if (viewModel.mode == BlockMode.BLOCK) "Bloquear" else "Permitir solo", onClick = { page = SessionPage.MODE })
                        FgRow("Apps", value = if (viewModel.apps.isEmpty()) "Ninguna" else viewModel.apps.size.toString(), onClick = { page = SessionPage.APPS })
                        FgRow("Webs", value = if (viewModel.webs.isEmpty()) "Ninguna" else viewModel.webs.size.toString(), onClick = { page = SessionPage.WEBS })
                    }
                }
                if (!pomodoro) {
                    item {
                        FgGroup {
                            FgRow("Cómo se puede terminar", value = endPolicyLabel(viewModel.endPolicy), onClick = { page = SessionPage.END })
                        }
                    }
                }
            }
        },
        sub = { current ->
            FgScreen(current.title, onBack = { page = null }) {
                item {
                    when (current) {
                        SessionPage.DURATION -> GroupStack {
                            FgGroup(footer = "Elige un atajo, desliza o afina de 5 en 5.") {
                                FgCell { DurationChoice(minutes, { minutes = it }, presetMinutes) }
                            }
                        }
                        SessionPage.MODE -> GroupStack { ModePicker(viewModel.mode, viewModel::chooseMode) }
                        SessionPage.APPS -> SelectedAppsGroups(viewModel.apps, viewModel::chooseApps, { showAppPicker = true })
                        SessionPage.WEBS -> WebRulesEditor(viewModel.webs, viewModel::addWeb, viewModel::removeWeb)
                        SessionPage.END -> EndPolicyCard(viewModel)
                    }
                }
            }
        },
    )

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
