package com.focusguard.ui.habits

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.MoodPromptSettings
import com.focusguard.decision.NudgeSettings
import com.focusguard.decision.Reminder
import com.focusguard.decision.Reminders
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.FgTextAction
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatMinutes
import com.focusguard.ui.parseTimeOfDay
import com.focusguard.ui.profiles.AppPickerDialog

private const val TITLE = "Hábitos y recordatorios"

// Las subpantallas se guardan como texto: «add», «mood» o «r:<id del recordatorio>».
private const val PAGE_ADD = "add"
private const val PAGE_MOOD = "mood"
private const val PAGE_REMINDER = "r:"

@Composable
fun HabitsScreen(viewModel: HabitsViewModel, onBack: () -> Unit) {
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    val moodPrompt by viewModel.moodPrompt.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    HabitsContent(
        nudge, moodPrompt, reminders,
        onNudge = viewModel::setNudge, onMoodPrompt = viewModel::setMoodPrompt,
        onAdd = viewModel::addReminder, onToggle = viewModel::setReminderEnabled, onDelete = viewModel::deleteReminder,
        onBack = onBack, snackbar = snackbar,
    )
}

@Composable
internal fun HabitsContent(
    nudge: NudgeSettings,
    moodPrompt: MoodPromptSettings,
    reminders: List<Reminder>,
    onNudge: (NudgeSettings) -> Unit,
    onMoodPrompt: (MoodPromptSettings) -> Unit,
    onAdd: (Int, String, () -> Unit) -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    snackbar: SnackbarHostState? = null,
) {
    var page by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen(TITLE, modifier, onBack = onBack, snackbar = snackbar) {
                item {
                    FgGroup(header = "Aviso de tiempo", footer = "Cuando uses 15 minutos las apps que elijas dentro de una hora, un aviso. Después espera una hora antes de repetirlo.") {
                        FgSwitchRow("Avisarme sin bloquear", nudge.enabled, { onNudge(nudge.copy(enabled = it)) })
                        if (nudge.enabled) {
                            FgRow("Apps", value = if (nudge.apps.isEmpty()) "Ninguna" else nudge.apps.size.toString(), onClick = { picking = true })
                        }
                    }
                }
                item {
                    FgGroup(header = "Recordatorios", footer = "Un aviso a la misma hora todos los días: beber agua, salir a caminar, llamar a alguien…") {
                        reminders.forEach { reminder ->
                            FgRow(
                                reminder.text, subtitle = formatMinutes(reminder.minuteOfDay), value = if (reminder.enabled) null else "Apagado",
                                onClick = { page = PAGE_REMINDER + reminder.id },
                            )
                        }
                        if (reminders.size < Reminders.MAX) {
                            FgActionRow("Añadir recordatorio", { page = PAGE_ADD })
                        } else {
                            FgRow("Ya tienes ${Reminders.MAX}, el máximo. Borra alguno para añadir otro.")
                        }
                    }
                }
                item {
                    FgGroup(header = "Pregunta de ánimo", footer = "Un aviso a la hora que elijas, si ese día aún no has apuntado cómo te sientes. Se apunta en el Diario.") {
                        FgSwitchRow("Preguntarme cómo estoy", moodPrompt.enabled, { onMoodPrompt(moodPrompt.copy(enabled = it)) })
                        if (moodPrompt.enabled) {
                            FgRow("Hora", value = formatMinutes(moodPrompt.minuteOfDay), onClick = { page = PAGE_MOOD })
                        }
                    }
                }
                item { FgNote("Son avisos: no bloquean nada. Salen con la protección encendida y la app abierta o en segundo plano, y lo que escribes aquí no sale del teléfono.") }
            }
        },
        sub = { p ->
            when {
                p == PAGE_ADD -> AddReminderPage(onAdd, modifier, onBack = { page = null }, snackbar = snackbar)
                p == PAGE_MOOD -> MoodTimePage(moodPrompt, onMoodPrompt, modifier, onBack = { page = null }, snackbar = snackbar)
                p.startsWith(PAGE_REMINDER) -> {
                    val reminder = reminders.firstOrNull { it.id.toString() == p.removePrefix(PAGE_REMINDER) }
                    if (reminder == null) {
                        // Se borró (o aún no ha llegado): se vuelve a la lista.
                        LaunchedEffect(Unit) { page = null }
                        FgScreen("Recordatorio", modifier, onBack = { page = null }) {}
                    } else {
                        FgScreen("Recordatorio", modifier, onBack = { page = null }, snackbar = snackbar) {
                            item {
                                FgGroup(header = "${formatMinutes(reminder.minuteOfDay)} · ${reminder.text}") {
                                    FgSwitchRow("Recordatorio activo", reminder.enabled, { onToggle(reminder.id, it) })
                                }
                            }
                            item { FgGroup { FgActionRow("Borrar el recordatorio", { onDelete(reminder.id); page = null }, danger = true) } }
                        }
                    }
                }
                else -> LaunchedEffect(Unit) { page = null }
            }
        },
    )
    if (picking) {
        AppPickerDialog(
            selected = nudge.apps,
            onConfirm = { onNudge(nudge.copy(apps = it)); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun AddReminderPage(onAdd: (Int, String, () -> Unit) -> Unit, modifier: Modifier, onBack: () -> Unit, snackbar: SnackbarHostState?) {
    var time by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    val minute = parseTimeOfDay(time)
    FgScreen(
        "Nuevo recordatorio", modifier, onBack = onBack, snackbar = snackbar,
        actions = {
            FgTextAction("Añadir", { minute?.let { onAdd(it, text) { time = ""; text = ""; onBack() } } }, enabled = minute != null && Reminders.clean(text).isNotEmpty())
        },
    ) {
        item {
            FgGroup {
                FgCell {
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = time, onValueChange = { time = it.take(5) }, label = { Text("Hora") }, placeholder = { Text("09:00") }, singleLine = true,
                            isError = time.isNotEmpty() && minute == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.take(Reminders.MAX_TEXT_CHARS) }, label = { Text("Qué") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodTimePage(settings: MoodPromptSettings, onChange: (MoodPromptSettings) -> Unit, modifier: Modifier, onBack: () -> Unit, snackbar: SnackbarHostState?) {
    // Lo escrito se guarda con el botón: guardar con cada tecla válida («21:3» ya es 21:03) cambiaría el campo mientras se escribe.
    var time by rememberSaveable { mutableStateOf(formatMinutes(settings.minuteOfDay)) }
    val minute = parseTimeOfDay(time)
    FgScreen(
        "Hora de la pregunta", modifier, onBack = onBack, snackbar = snackbar,
        actions = { FgTextAction("Guardar", { minute?.let { onChange(settings.copy(minuteOfDay = it)) } }, enabled = minute != null && minute != settings.minuteOfDay) },
    ) {
        item {
            FgGroup {
                FgCell {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it.take(5) },
                        label = { Text("Hora") }, singleLine = true, isError = minute == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
