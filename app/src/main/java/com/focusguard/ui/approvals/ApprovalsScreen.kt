package com.focusguard.ui.approvals

import com.focusguard.ui.icons.FgIcons
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import com.focusguard.decision.Approvals
import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.ApprovalStatus
import com.focusguard.model.RemoteStatus
import com.focusguard.data.PartnerState
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.HelpAction
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.HandTypedField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.RadioGroup
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SecureWindowEffect
import com.focusguard.ui.formatLocal
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.hint
import com.focusguard.ui.label
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            FgTopBar("Aprobaciones", onBack = onBack, actions = { HelpAction(onHelp) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle(if (pending.isEmpty()) "Solicitudes pendientes" else "Solicitudes pendientes (${pending.size})", FgIcons.CheckCircle) }
            if (pending.isEmpty()) item { Hint("No tienes ninguna. Cuando pidas apagar algo que necesita aprobación, aparecerá aquí.") }
            items(pending, key = { it.id }) { request -> RequestCard(viewModel, request, Instant.ofEpochMilli(nowMs)) }
            item {
                AppCard {
                    SectionTitle("Cómo se aprueba apagar algo")
                    Hint("Apagar el bloqueo adulto, apagar o borrar un perfil o quitar cosas de una lista pide una aprobación. Añadir o endurecer es libre.")
                    // «Compañero» solo se ofrece cuando hay uno que aceptó (o ya estaba elegido).
                    val choices = ApprovalMode.entries.filter { it != ApprovalMode.PARTNER || partner is PartnerState.Active || mode == ApprovalMode.PARTNER }
                    RadioGroup(choices.map { Triple(it, it.label(), it.hint()) }, mode, viewModel::changeMode)
                    if (partner !is PartnerState.Active) Hint("Para que te apruebe un compañero, invítale desde Cuenta.")
                }
            }
        }
    }
}

@Composable
private fun RequestCard(viewModel: ApprovalsViewModel, request: ApprovalRequest, now: Instant) {
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

    AppCard {
        SectionTitle(Approvals.describe(request.actionKey))
        if (request.mode == ApprovalMode.PARTNER) {
            // Desde cuándo espera y hasta cuándo vale: una solicitud al compañero caduca a las 48 horas.
            Hint("Pedida el ${formatLocal(request.createdAt, withDay = true)} · caduca el ${formatLocal((request.sendAfter ?: request.createdAt).plus(Duration.ofHours(48)), withDay = true)}")
        }
        when (status) {
            ApprovalStatus.Ready -> Hint("Listo para aplicar.")
            is ApprovalStatus.WaitingUntil -> Hint("Disponible en ${formatRemaining(Duration.between(now, status.at), alwaysHours = true)}")
            is ApprovalStatus.HoldingBeforePartner -> Hint(
                "Primero toca esperar: a tu compañero se le enviará el correo dentro de ${formatRemaining(Duration.between(now, status.at), alwaysHours = true)}.",
            )
            is ApprovalStatus.WaitingPartner -> Hint(
                if (status.sent) "Esperando la respuesta de tu compañero. Le ha llegado un correo."
                else "Aún no se ha enviado a tu compañero. Se enviará en cuanto haya conexión.",
            )
            ApprovalStatus.PartnerRejected -> Hint("Tu compañero lo rechazó.")
            ApprovalStatus.PartnerExpired -> Hint("Caducó sin respuesta. Puedes pedirlo otra vez.")
            is ApprovalStatus.Walking -> {
                Hint("Pasos: ${status.done} de ${status.needed}")
                StepsPermission(viewModel)
            }
            ApprovalStatus.NeedPhrase -> {
                Hint("Escribe a mano este texto, igual que aparece:")
                Text(Approvals.phrase(request.phraseIndex), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (request.mode == ApprovalMode.LONG_PHRASE) {
            HandTypedField(typed, { typed = it }, Modifier.padding(top = 8.dp))
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                // Esperando al compañero no hay nada que aplicar: solo se puede cancelar (con confirmación).
                waiting -> SecondaryButton("Cancelar solicitud", { confirmCancel = true }, Modifier.weight(1f))
                status == ApprovalStatus.PartnerExpired || status == ApprovalStatus.PartnerRejected -> {
                    PrimaryButton("Pedir otra vez", { viewModel.askAgain(request) }, Modifier.weight(1f))
                    SecondaryButton("Quitar", { viewModel.cancel(request) }, Modifier.weight(1f))
                }
                else -> {
                    PrimaryButton("Aplicar", { viewModel.apply(request, typed) }, Modifier.weight(1f), enabled = status == ApprovalStatus.Ready)
                    SecondaryButton("Cancelar", { viewModel.cancel(request) }, Modifier.weight(1f))
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
        Text("Este teléfono no tiene contador de pasos. Elige otro modo.", color = MaterialTheme.colorScheme.error)
    } else if (needsPermission) {
        SecondaryButton("Permitir contar pasos", { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) })
    }
}
