package com.focusguard.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.Recovery
import com.focusguard.decision.RecoveryStep
import com.focusguard.decision.StepInput
import com.focusguard.decision.StepRecord
import com.focusguard.decision.StepStatus
import com.focusguard.decision.UrgeTrigger
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.BrandHero
import com.focusguard.ui.components.CheckRow
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.IconBadge
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.rememberTrustedNowMs
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.streak.StreakText
import java.time.LocalDate

class RecoveryActions(val complete: (step: Int, answer: String) -> Unit, val restart: () -> Unit, val openJournal: () -> Unit, val openAccount: () -> Unit)

@Composable
fun RecoveryScreen(viewModel: RecoveryViewModel, onBack: () -> Unit, onJournal: () -> Unit, onAccount: () -> Unit) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    // El día se vuelve a mirar cada minuto: pasada la medianoche, el paso siguiente se abre sin salir de la pantalla.
    val nowMs = rememberTrustedNowMs(60_000L)
    val today = remember(nowMs / 60_000L) { viewModel.today }
    Scaffold(topBar = { FgTopBar("Programa de recuperación", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        // Hasta leer el disco no se enseña nada: un programa vacío por un momento daría a entender que se perdió.
        progress?.let {
            RecoveryContent(it, today, RecoveryActions(viewModel::complete, viewModel::restart, onJournal, onAccount), Modifier.padding(padding))
        }
    }
}

@Composable
internal fun RecoveryContent(progress: Map<Int, StepRecord>, today: Long, actions: RecoveryActions, modifier: Modifier = Modifier) {
    val statuses = Recovery.statuses(progress, today)
    val done = Recovery.doneCount(progress)
    var confirmRestart by remember { mutableStateOf(false) }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("¿Empezar de nuevo?") },
            text = { Text("Se borran tus respuestas de los siete pasos de este teléfono. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmRestart = false; actions.restart() }) { Text("Empezar de nuevo") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancelar") } },
        )
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BrandHero(
                if (done == Recovery.STEPS.size) "Programa completado" else "Siete pasos, uno por día",
                "Para entender qué te lleva a abrir el teléfono y tener un plan para esos momentos. Lo que contestas se queda en este teléfono.",
                icon = FgIcons.Leaf,
            )
        }
        item {
            AppCard {
                Text("$done de ${Recovery.STEPS.size} pasos", style = MaterialTheme.typography.titleMedium)
                FgProgress({ done.toFloat() / Recovery.STEPS.size }, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        items(Recovery.STEPS, key = { it.id }) { step ->
            StepCard(step, statuses[step.id - 1], today, actions)
        }
        if (progress.isNotEmpty()) {
            item { SecondaryButton("Empezar de nuevo", { confirmRestart = true }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun StepCard(step: RecoveryStep, status: StepStatus, today: Long, actions: RecoveryActions) {
    val scheme = MaterialTheme.colorScheme
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when (status) {
                is StepStatus.Done -> IconBadge(FgIcons.Check, size = 36.dp, container = scheme.primary, content = scheme.onPrimary)
                StepStatus.Open -> IconBadge(FgIcons.Target, size = 36.dp)
                is StepStatus.Locked -> IconBadge(FgIcons.Lock, size = 36.dp, container = scheme.surfaceVariant, content = scheme.onSurfaceVariant)
            }
            Text(
                "${step.id}. ${step.title}",
                style = MaterialTheme.typography.titleMedium,
                color = if (status is StepStatus.Locked) scheme.onSurfaceVariant else scheme.onSurface,
            )
        }
        when (status) {
            is StepStatus.Done -> DoneBody(step, status.record)
            StepStatus.Open -> OpenBody(step, actions)
            is StepStatus.Locked -> {
                val openDay = status.openDay
                Hint(
                    when {
                        openDay == null -> "Se abre cuando acabes el paso anterior."
                        openDay <= today + 1 -> "Se abre mañana: así cada paso se asienta."
                        else -> "Se abre el ${StreakText.date(LocalDate.ofEpochDay(openDay))}."
                    },
                )
            }
        }
    }
}

@Composable
private fun DoneBody(step: RecoveryStep, record: StepRecord) {
    val summary = when (step.input) {
        StepInput.TRIGGERS -> Recovery.decodeChoices(record.answer).joinToString(", ") { name -> UrgeTrigger.entries.firstOrNull { it.name == name }?.label ?: name }
        StepInput.MOMENTS -> Recovery.decodeChoices(record.answer).joinToString(", ")
        StepInput.NOTE -> "${step.prompt ?: ""} ${record.answer}".trim()
        StepInput.NONE -> ""
    }
    if (summary.isNotEmpty()) Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
    Hint("Hecho el ${StreakText.date(LocalDate.ofEpochDay(record.doneDay))}.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenBody(step: RecoveryStep, actions: RecoveryActions) {
    // Con `rememberSaveable`: lo escrito sobrevive a girar el teléfono y a ir al diario o a la cuenta desde el paso y volver.
    var chosen by rememberSaveable(step.id) { mutableStateOf("") }
    var note by rememberSaveable(step.id) { mutableStateOf("") }
    val choices = Recovery.decodeChoices(chosen).toSet()
    Text(step.intro, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
    step.points.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
    when (step.input) {
        StepInput.TRIGGERS -> FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UrgeTrigger.entries.forEach { trigger ->
                FilterChip(
                    selected = trigger.name in choices,
                    onClick = { chosen = Recovery.encodeChoices(if (trigger.name in choices) choices - trigger.name else choices + trigger.name) },
                    label = { Text(trigger.label) },
                )
            }
        }
        StepInput.MOMENTS -> Recovery.MOMENTS.forEach { moment ->
            CheckRow(moment, null, moment in choices, { on -> chosen = Recovery.encodeChoices(if (on) choices + moment else choices - moment) })
        }
        StepInput.NOTE -> OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(Recovery.MAX_NOTE) },
            label = { Text(step.prompt ?: "Tu respuesta") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        StepInput.NONE -> Unit
    }
    val answer = if (step.input == StepInput.NOTE) note else chosen
    if (step.id == 4) SecondaryButton("Apuntarlo en el diario", actions.openJournal, Modifier.fillMaxWidth().padding(top = 8.dp))
    if (step.id == 5) SecondaryButton("Ver mi compañero", actions.openAccount, Modifier.fillMaxWidth().padding(top = 8.dp))
    PrimaryButton(
        if (step.input == StepInput.NONE) "Lo he leído" else "Hecho",
        { actions.complete(step.id, answer) },
        Modifier.fillMaxWidth().padding(top = 12.dp),
        enabled = Recovery.canComplete(step, Recovery.normalize(step, answer)),
    )
}
