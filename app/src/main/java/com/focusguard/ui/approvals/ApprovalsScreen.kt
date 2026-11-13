package com.focusguard.ui.approvals

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.focusguard.data.PartnerState
import com.focusguard.decision.Approvals
import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.ApprovalStatus
import com.focusguard.model.RemoteStatus
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRadioGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.HandTypedField
import com.focusguard.ui.components.HelpAction
import com.focusguard.ui.components.SecureWindowEffect
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatLocal
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.hint
import com.focusguard.ui.label
import com.focusguard.ui.profiles.GroupStack
import com.focusguard.ui.rememberTrustedNowMs
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

@Composable
fun ApprovalsScreen(viewModel: ApprovalsViewModel, onBack: () -> Unit, onHelp: () -> Unit = {}) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val partner by viewModel.partnerState.collectAsStateWithLifecycle()
    val nowMs = rememberTrustedNowMs()
    SecureWindowEffect()
    val walking = pending.any { it.mode == ApprovalMode.STEPS }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(walking, lifecycle) {
        if (!walking) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refreshSteps()
                delay(3_000)
            }
        }
    }

    // Las solicitudes al compañero se envían y se consultan al abrir la pantalla y cada pocos segundos mientras alguna espera.
    val waitingPartner = pending.any { it.mode == ApprovalMode.PARTNER && (it.remote == RemoteStatus.NOT_SENT || it.remote == RemoteStatus.WAITING) }
    LaunchedEffect(waitingPartner, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.syncPartner()
            while (waitingPartner) {
                delay(15_000)
                viewModel.syncPartner()
            }
        }
    }

    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    val now = Instant.ofEpochMilli(nowMs)
    // La solicitud abierta se guarda por su número: si se aplica o se cancela, desaparece y se vuelve a la lista.
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = pending.firstOrNull { it.id == openId }

    SubPageHost(
        page = open?.id,
        onBack = { openId = null },
        root = {
            FgScreen("Aprobaciones", onBack = onBack, snackbar = snackbar, actions = { HelpAction(onHelp) }) {
                item {
                    if (pending.isEmpty()) {
                        FgNote("No tienes ninguna solicitud pendiente. Cuando pidas apagar algo que necesita aprobación, aparecerá aquí.")
                    } else {
                        FgGroup(header = "Solicitudes pendientes (${pending.size})") {
                            pending.forEach { request ->
                                FgRow(
                                    Approvals.describe(request.actionKey),
                                    subtitle = statusText(viewModel.status(request, "", now), now, short = true),
                                    onClick = { openId = request.id },
                                )
                            }
                        }
                    }
                }
                item {
                    // «Compañero» solo se ofrece cuando hay uno que aceptó (o ya estaba elegido).
                    val choices = ApprovalMode.entries.filter { it != ApprovalMode.PARTNER || partner is PartnerState.Active || mode == ApprovalMode.PARTNER }
                    FgRadioGroup(
                        options = choices.map { Triple(it, it.label(), it.hint()) },
                        selected = mode,
                        onSelect = viewModel::changeMode,
                        header = "Cómo se aprueba apagar algo",
                        footer = "Apagar el bloqueo adulto, apagar o borrar un perfil o quitar cosas de una lista pide una aprobación. Añadir o endurecer es libre." +
                            if (partner !is PartnerState.Active) " Para que te apruebe un compañero, invítale desde Cuenta." else "",
                    )
                }
            }
        },
        sub = { id ->
            val request = pending.firstOrNull { it.id == id }
            if (request != null) RequestPage(viewModel, request, now, snackbar) { openId = null }
        },
    )
}

/** Lo que se sabe de la solicitud según su estado. [short] es la versión de una línea para la lista. */
private fun statusText(status: ApprovalStatus, now: Instant, short: Boolean = false): String = when (status) {
    ApprovalStatus.Ready -> "Listo para aplicar."
    is ApprovalStatus.WaitingUntil -> "Disponible en ${formatRemaining(Duration.between(now, status.at), alwaysHours = true)}"
    is ApprovalStatus.HoldingBeforePartner ->
        if (short) "Se enviará a tu compañero en ${formatRemaining(Duration.between(now, status.at), alwaysHours = true)}"
        else "Primero toca esperar: a tu compañero se le enviará el correo dentro de ${formatRemaining(Duration.between(now, status.at), alwaysHours = true)}."
    is ApprovalStatus.WaitingPartner ->
        if (status.sent) "Esperando la respuesta de tu compañero." + if (short) "" else " Le ha llegado un correo."
        else "Aún no se ha enviado a tu compañero." + if (short) "" else " Se enviará en cuanto haya conexión."
    ApprovalStatus.PartnerRejected -> "Tu compañero lo rechazó."
    ApprovalStatus.PartnerExpired -> "Caducó sin respuesta. Puedes pedirlo otra vez."
    is ApprovalStatus.Walking -> "Pasos: ${status.done} de ${status.needed}"
    ApprovalStatus.NeedPhrase -> if (short) "Falta escribir el texto" else "Escribe a mano este texto, igual que aparece:"
}

@Composable
private fun RequestPage(
    viewModel: ApprovalsViewModel,
    request: ApprovalRequest,
    now: Instant,
    snackbar: androidx.compose.material3.SnackbarHostState,
    onBack: () -> Unit,
) {
    var typed by remember(request.id) { mutableStateOf("") }
    var confirmCancel by remember(request.id) { mutableStateOf(false) }
    val status = viewModel.status(request, typed, now)
    val waiting = status is ApprovalStatus.WaitingPartner || status is ApprovalStatus.HoldingBeforePartner

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("¿Cancelar esta solicitud?") },
            text = { Text("Tu compañero ya no podrá aprobarla (el enlace de su correo dejará de valer). Si aún quieres el cambio, tendrás que pedirlo otra vez.") },
            confirmButton = { TextButton(onClick = { confirmCancel = false; viewModel.cancel(request) }) { Text("Cancelar solicitud") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Mantenerla") } },
        )
    }

    FgScreen(Approvals.describe(request.actionKey), onBack = onBack, snackbar = snackbar) {
        item {
            GroupStack {
                if (request.mode == ApprovalMode.PARTNER) {
                    // Desde cuándo espera y hasta cuándo vale: una solicitud al compañero caduca a las 48 horas.
                    FgNote("Pedida el ${formatLocal(request.createdAt, withDay = true)} · caduca el ${formatLocal((request.sendAfter ?: request.createdAt).plus(Duration.ofHours(48)), withDay = true)}")
                }
                FgNote(statusText(status, now))
                if (status is ApprovalStatus.Walking) StepsPermission(viewModel)
                if (status == ApprovalStatus.NeedPhrase) {
                    FgGroup { FgCell { Text(Approvals.phrase(request.phraseIndex), style = MaterialTheme.typography.bodyLarge) } }
                }
                if (request.mode == ApprovalMode.LONG_PHRASE) {
                    FgGroup { FgCell { HandTypedField(typed, { typed = it }) } }
                }
                FgGroup {
                    when {
                        // Esperando al compañero no hay nada que aplicar: solo se puede cancelar (con confirmación).
                        waiting -> FgActionRow("Cancelar solicitud", { confirmCancel = true }, danger = true)
                        status == ApprovalStatus.PartnerExpired || status == ApprovalStatus.PartnerRejected -> {
                            FgActionRow("Pedir otra vez", { viewModel.askAgain(request) })
                            FgActionRow("Quitar", { viewModel.cancel(request) }, danger = true)
                        }
                        else -> {
                            FgActionRow("Aplicar", { viewModel.apply(request, typed) }, enabled = status == ApprovalStatus.Ready)
                            FgActionRow("Cancelar", { viewModel.cancel(request) }, danger = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepsPermission(viewModel: ApprovalsViewModel) {
    val context = LocalContext.current
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshSteps() }
    if (!viewModel.stepsAvailable) {
        Text("Este teléfono no tiene contador de pasos. Elige otro modo.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    } else if (needsPermission) {
        FgGroup { FgActionRow("Permitir contar pasos", { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }) }
    }
}
