package com.focusguard.ui.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.decision.BlockMe
import com.focusguard.decision.ConsciousEdit
import com.focusguard.decision.ScheduleConflict
import com.focusguard.decision.ScheduleTemplate
import com.focusguard.decision.ScheduleTemplates
import com.focusguard.ui.Days
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.Hint
import com.focusguard.ui.formatMinutes
import com.focusguard.ui.parseMinutes
import java.time.DayOfWeek
import kotlinx.coroutines.delay

/** Lista de plantillas (E14). Al elegir una se rellenan nombre y horario o límite; las apps las elige el usuario. */
@Composable
internal fun TemplatePickerDialog(onPick: (ScheduleTemplate) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Empezar con una plantilla") },
        text = {
            LazyColumn {
                items(ScheduleTemplates.all, key = { it.id }) { template ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onPick(template) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(template.emoji, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(end = 12.dp))
                        Column {
                            Text(template.title, style = MaterialTheme.typography.titleMedium)
                            Text(template.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Aviso (E12) de que otro perfil del mismo modo coincide en el tiempo. Los bloqueos se suman, así que no impide guardar. */
@Composable
internal fun ConflictsCard(conflicts: List<ScheduleConflict>, modifier: Modifier = Modifier) {
    GroupStack(modifier) {
        FgGroup(
            header = "Horarios que coinciden",
            footer = "Dos perfiles del mismo modo están activos a la vez. No pasa nada: se bloquea la suma de los dos. Si no es lo que quieres, cambia uno de los horarios.",
        ) {
            conflicts.forEach {
                FgRow("«${it.otherTitle}»", subtitle = "${Days.word(it.day)} de ${formatMinutes(it.startMinute)} a ${formatMinutes(it.endMinute)}")
            }
        }
    }
}

/** Edición consciente (E16): una pausa antes de tocar un horario que ya funcionaba. */
@Composable
internal fun ConsciousEditDialog(onContinue: () -> Unit, onCancel: () -> Unit) {
    var seconds by rememberSaveable { mutableIntStateOf(ConsciousEdit.WAIT_SECONDS) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1_000)
            seconds--
        }
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("¿Seguro que quieres cambiar este horario?") },
        text = { Text("Lo pusiste para ayudarte. Respira un momento y piensa si el cambio sigue yendo contigo.") },
        confirmButton = {
            TextButton(onClick = onContinue, enabled = seconds == 0) {
                Text(if (seconds == 0) "Continuar" else "Continuar ($seconds)")
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Mejor no") } },
    )
}

/** Horario sencillo (E18): días, desde y hasta, y los minutos de emergencia al día. */
@Composable
internal fun BlockMeDialog(onConfirm: (ScheduleTemplate) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(DayOfWeek.entries.toSet()) }
    var start by remember { mutableStateOf("22:00") }
    var end by remember { mutableStateOf("07:00") }
    var emergency by remember { mutableIntStateOf(0) }
    val startMinute = remember(start) { parseMinutes(start)?.takeIf { it < 24 * 60 } }
    val endMinute = remember(end) { parseMinutes(end) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block Me") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Hint("Elige los días y las horas. Si quieres, deja unos minutos de emergencia al día.")
                DayChips(days) { days = it }
                TimeRangeFields(start, end, { start = it }, { end = it }, startMinute != null, endMinute != null)
                Text("Tiempo de emergencia al día", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                ChipChoices(
                    options = BlockMe.EMERGENCY_OPTIONS.map { it to if (it == 0) "Ninguno" else "$it min" },
                    selected = emergency,
                    onSelect = { emergency = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(BlockMe.template(days, startMinute!!, endMinute!!, emergency)) },
                enabled = days.isNotEmpty() && startMinute != null && endMinute != null,
            ) { Text("Usar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
