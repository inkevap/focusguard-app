package com.focusguard.ui.habits

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.MoodPromptSettings
import com.focusguard.decision.NudgeSettings
import com.focusguard.decision.Reminder
import com.focusguard.decision.Reminders
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatMinutes
import com.focusguard.ui.parseTimeOfDay
import com.focusguard.ui.profiles.AppPickerDialog

@Composable
fun HabitsScreen(viewModel: HabitsViewModel, onBack: () -> Unit) {
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    val moodPrompt by viewModel.moodPrompt.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    Scaffold(
        topBar = {
            FgTopBar("Hábitos y recordatorios", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        HabitsContent(
            nudge, moodPrompt, reminders,
            onNudge = viewModel::setNudge, onMoodPrompt = viewModel::setMoodPrompt,
            onAdd = viewModel::addReminder, onToggle = viewModel::setReminderEnabled, onDelete = viewModel::deleteReminder,
            modifier = Modifier.padding(padding),
        )
    }
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
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NudgeCard(nudge, onNudge) }
        item { RemindersCard(reminders, onAdd, onToggle, onDelete) }
        item { MoodPromptCard(moodPrompt, onMoodPrompt) }
        item { Hint("Son avisos: no bloquean nada. Salen con la protección encendida y la app abierta o en segundo plano, y lo que escribes aquí no sale del teléfono.") }
    }
}

@Composable
private fun NudgeCard(settings: NudgeSettings, onChange: (NudgeSettings) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    AppCard {
        SectionTitle("Aviso de tiempo", FgIcons.Timer)
        SwitchRow(
            "Avisarme sin bloquear",
            "Cuando uses 15 minutos las apps que elijas dentro de una hora, un aviso. Después espera una hora antes de repetirlo.",
            settings.enabled,
            { onChange(settings.copy(enabled = it)) },
        )
        if (settings.enabled) {
            Hint(if (settings.apps.isEmpty()) "Aún no has elegido apps." else "Apps elegidas: ${settings.apps.size}.", Modifier.padding(top = 4.dp))
            SecondaryButton("Elegir apps", { picking = true }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
    if (picking) {
        AppPickerDialog(
            selected = settings.apps,
            onConfirm = { onChange(settings.copy(apps = it)); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun RemindersCard(reminders: List<Reminder>, onAdd: (Int, String, () -> Unit) -> Unit, onToggle: (Long, Boolean) -> Unit, onDelete: (Long) -> Unit) {
    var time by rememberSaveable { mutableStateOf("") }
    var text by rememberSaveable { mutableStateOf("") }
    val minute = parseTimeOfDay(time)
    AppCard {
        SectionTitle("Recordatorios", FgIcons.Bell)
        Hint("Un aviso a la misma hora todos los días: beber agua, salir a caminar, llamar a alguien…")
        reminders.forEach { reminder ->
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatMinutes(reminder.minuteOfDay), style = MaterialTheme.typography.titleMedium)
                Text(reminder.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = reminder.enabled,
                    onCheckedChange = { onToggle(reminder.id, it) },
                    modifier = Modifier.semantics { contentDescription = "Recordatorio de las ${formatMinutes(reminder.minuteOfDay)}" },
                )
                IconButton(onClick = { onDelete(reminder.id) }) { Icon(FgIcons.Delete, contentDescription = "Borrar el recordatorio de las ${formatMinutes(reminder.minuteOfDay)}") }
            }
        }
        if (reminders.size < Reminders.MAX) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = time, onValueChange = { time = it.take(5) }, label = { Text("Hora") }, placeholder = { Text("09:00") }, singleLine = true,
                    isError = time.isNotEmpty() && minute == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(Reminders.MAX_TEXT_CHARS) }, label = { Text("Qué") }, singleLine = true,
                    modifier = Modifier.weight(2f),
                )
            }
            SecondaryButton(
                "Añadir recordatorio",
                { minute?.let { onAdd(it, text) { time = ""; text = "" } } },
                Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = minute != null && Reminders.clean(text).isNotEmpty(),
            )
        } else {
            Hint("Ya tienes ${Reminders.MAX}, el máximo. Borra alguno para añadir otro.", Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun MoodPromptCard(settings: MoodPromptSettings, onChange: (MoodPromptSettings) -> Unit) {
    // Lo escrito se guarda con el botón: guardar con cada tecla válida («21:3» ya es 21:03) cambiaría el campo mientras se escribe.
    var time by rememberSaveable { mutableStateOf(formatMinutes(settings.minuteOfDay)) }
    val minute = parseTimeOfDay(time)
    AppCard {
        SectionTitle("Pregunta de ánimo", FgIcons.Leaf)
        SwitchRow(
            "Preguntarme cómo estoy",
            "Un aviso a la hora que elijas, si ese día aún no has apuntado cómo te sientes. Se apunta en el Diario.",
            settings.enabled,
            { onChange(settings.copy(enabled = it)) },
        )
        if (settings.enabled) {
            OutlinedTextField(
                value = time,
                onValueChange = { time = it.take(5) },
                label = { Text("Hora") }, singleLine = true, isError = minute == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SecondaryButton(
                "Guardar la hora",
                { minute?.let { onChange(settings.copy(minuteOfDay = it)) } },
                Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = minute != null && minute != settings.minuteOfDay,
            )
        }
    }
}
