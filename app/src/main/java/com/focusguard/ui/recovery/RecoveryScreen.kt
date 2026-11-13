package com.focusguard.ui.recovery

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.Recovery
import com.focusguard.decision.RecoveryStep
import com.focusguard.decision.StepInput
import com.focusguard.decision.StepRecord
import com.focusguard.decision.StepStatus
import com.focusguard.decision.UrgeTrigger
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgCheckRow
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.profiles.BottomAction
import com.focusguard.ui.rememberTrustedNowMs
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
    // Hasta leer el disco no se enseña nada: un programa vacío por un momento daría a entender que se perdió.
    val loaded = progress
    if (loaded == null) {
        FgScreen("Programa de recuperación", onBack = onBack, snackbar = snackbar) {}
    } else {
        RecoveryContent(loaded, today, RecoveryActions(viewModel::complete, viewModel::restart, onJournal, onAccount), onBack = onBack, snackbar = snackbar)
    }
}

@Composable
internal fun RecoveryContent(
    progress: Map<Int, StepRecord>,
    today: Long,
    actions: RecoveryActions,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    snackbar: SnackbarHostState? = null,
) {
    val statuses = Recovery.statuses(progress, today)
    val done = Recovery.doneCount(progress)
    var confirmRestart by remember { mutableStateOf(false) }
    // El paso abierto (su número); nulo es la lista.
    var stepPage by rememberSaveable { mutableStateOf<Int?>(null) }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("¿Empezar de nuevo?") },
            text = { Text("Se borran tus respuestas de los siete pasos de este teléfono. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmRestart = false; actions.restart() }) { Text("Empezar de nuevo") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancelar") } },
        )
    }
    SubPageHost(
        page = stepPage,
        onBack = { stepPage = null },
        root = {
            FgScreen("Programa de recuperación", modifier, onBack = onBack, snackbar = snackbar) {
                item {
                    FgHero(
                        title = if (done == Recovery.STEPS.size) "Programa completado" else "Siete pasos, uno por día",
                        body = "Para entender qué te lleva a abrir el teléfono y tener un plan para esos momentos. Lo que contestas se queda en este teléfono.",
                        pill = "$done de ${Recovery.STEPS.size} pasos",
                        extra = { FgProgress({ done.toFloat() / Recovery.STEPS.size }, Modifier.fillMaxWidth().padding(top = 8.dp)) },
                    )
                }
                item {
                    FgGroup {
                        Recovery.STEPS.forEach { step -> StepRow(step, statuses[step.id - 1], today, onOpen = { stepPage = step.id }) }
                    }
                }
                if (progress.isNotEmpty()) {
                    item { FgGroup { FgActionRow("Empezar de nuevo", { confirmRestart = true }, danger = true) } }
                }
            }
        },
        sub = { id ->
            val step = Recovery.STEPS.firstOrNull { it.id == id }
            if (step == null) {
                stepPage = null
            } else {
                StepPage(step, statuses[step.id - 1], actions, modifier, onBack = { stepPage = null }, snackbar = snackbar)
            }
        },
    )
}

@Composable
private fun StepRow(step: RecoveryStep, status: StepStatus, today: Long, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val title = "${step.id}. ${step.title}"
    when (status) {
        is StepStatus.Done -> FgRow(title, icon = FgIcons.Check, value = "Hecho", onClick = onOpen)
        StepStatus.Open -> FgRow(title, icon = FgIcons.Target, value = "Ahora", onClick = onOpen)
        is StepStatus.Locked -> {
            val openDay = status.openDay
            FgRow(
                title, icon = FgIcons.Lock, iconColor = scheme.outline, titleColor = scheme.onSurfaceVariant,
                subtitle = when {
                    openDay == null -> "Se abre cuando acabes el paso anterior."
                    openDay <= today + 1 -> "Se abre mañana: así cada paso se asienta."
                    else -> "Se abre el ${StreakText.date(LocalDate.ofEpochDay(openDay))}."
                },
            )
        }
    }
}

@Composable
private fun StepPage(step: RecoveryStep, status: StepStatus, actions: RecoveryActions, modifier: Modifier, onBack: () -> Unit, snackbar: SnackbarHostState?) {
    // Con `rememberSaveable`: lo escrito sobrevive a girar el teléfono y a ir al diario o a la cuenta desde el paso y volver.
    var chosen by rememberSaveable(step.id) { mutableStateOf("") }
    var note by rememberSaveable(step.id) { mutableStateOf("") }
    val choices = Recovery.decodeChoices(chosen).toSet()
    val answer = if (step.input == StepInput.NOTE) note else chosen
    val open = status == StepStatus.Open
    FgScreen(
        "Paso ${step.id}", modifier, onBack = onBack, snackbar = snackbar,
        bottomBar = {
            if (open) {
                BottomAction {
                    PrimaryButton(
                        if (step.input == StepInput.NONE) "Lo he leído" else "Hecho",
                        { actions.complete(step.id, answer); onBack() },
                        Modifier.fillMaxWidth(),
                        enabled = Recovery.canComplete(step, Recovery.normalize(step, answer)),
                    )
                }
            }
        },
    ) {
        item { FgHero(title = step.title, body = step.intro) }
        if (step.points.isNotEmpty()) item { FgGroup { step.points.forEach { FgRow("• $it") } } }
        if (status is StepStatus.Done) {
            item { DoneGroup(step, status.record) }
        }
        if (open) {
            when (step.input) {
                StepInput.TRIGGERS -> item {
                    FgGroup(header = "Tus disparadores") {
                        UrgeTrigger.entries.forEach { trigger ->
                            FgCheckRow(
                                trigger.label, trigger.name in choices,
                                { on -> chosen = Recovery.encodeChoices(if (on) choices + trigger.name else choices - trigger.name) },
                            )
                        }
                    }
                }
                StepInput.MOMENTS -> item {
                    FgGroup(header = "Tus momentos") {
                        Recovery.MOMENTS.forEach { moment ->
                            FgCheckRow(moment, moment in choices, { on -> chosen = Recovery.encodeChoices(if (on) choices + moment else choices - moment) })
                        }
                    }
                }
                StepInput.NOTE -> item {
                    FgGroup {
                        FgCell {
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it.take(Recovery.MAX_NOTE) },
                                label = { Text(step.prompt ?: "Tu respuesta") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                StepInput.NONE -> Unit
            }
            if (step.id == 4) item { FgGroup { FgActionRow("Apuntarlo en el diario", actions.openJournal) } }
            if (step.id == 5) item { FgGroup { FgActionRow("Ver mi compañero", actions.openAccount) } }
        }
    }
}

@Composable
private fun DoneGroup(step: RecoveryStep, record: StepRecord) {
    val summary = when (step.input) {
        StepInput.TRIGGERS -> Recovery.decodeChoices(record.answer).joinToString(", ") { name -> UrgeTrigger.entries.firstOrNull { it.name == name }?.label ?: name }
        StepInput.MOMENTS -> Recovery.decodeChoices(record.answer).joinToString(", ")
        StepInput.NOTE -> "${step.prompt ?: ""} ${record.answer}".trim()
        StepInput.NONE -> ""
    }
    FgGroup {
        if (summary.isNotEmpty()) FgRow("Tu respuesta", subtitle = summary)
        FgRow("Hecho el ${StreakText.date(LocalDate.ofEpochDay(record.doneDay))}.")
    }
}
