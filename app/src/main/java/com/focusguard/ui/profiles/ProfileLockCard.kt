package com.focusguard.ui.profiles

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.model.ExitMethod
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgCheckRow
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.PinField
import com.focusguard.ui.components.StepperField
import com.focusguard.ui.rememberTrustedNowMs
import com.focusguard.ui.strict.requirementText
import java.time.Instant

/**
 * Candado de este perfil: mientras está puesto no se apaga, pausa, edita ni borra, con o sin modo estricto. Se quita cumpliendo los métodos
 * que se eligieron al ponerlo.
 */
@Composable
internal fun ProfileLockCard(profileId: Long, viewModel: ProfileLockViewModel) {
    val locks by viewModel.locks.collectAsStateWithLifecycle()
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val lock = locks[profileId]
    GroupStack {
        if (lock == null) {
            LockEditor(profileId, viewModel)
        } else {
            FgGroup(
                header = "Candado puesto",
                footer = "No podrás apagar, pausar, editar ni borrar este perfil hasta quitarlo.",
            ) {
                if (requests[profileId] == null) {
                    FgRow("Para quitarlo tendrás que cumplir", subtitle = lock.exitMethods.joinToString(", ") { methodName(it) })
                    FgActionRow("Pedir quitar el candado", { viewModel.requestUnlock(profileId) })
                }
            }
            if (requests[profileId] != null) UnlockProgress(profileId, viewModel, needsPin = lock.exitMethods.any { it is ExitMethod.Pin })
        }
    }
}

/** El resumen del candado para la fila de la ficha. */
@Composable
internal fun profileLockValue(profileId: Long, viewModel: ProfileLockViewModel): String {
    val locks by viewModel.locks.collectAsStateWithLifecycle()
    return if (locks[profileId] == null) "Sin candado" else "Puesto"
}

@Composable
private fun LockEditor(profileId: Long, viewModel: ProfileLockViewModel) {
    var timerOn by remember { mutableStateOf(false) }
    var timerMinutes by remember { mutableStateOf("30") }
    var cooldownOn by remember { mutableStateOf(false) }
    var cooldownMinutes by remember { mutableStateOf("30") }
    var chargerOn by remember { mutableStateOf(false) }
    var pinOn by remember { mutableStateOf(false) }
    var partnerOn by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FgGroup(
        header = "Para quitarlo tendrás que",
        footer = "Protege solo este perfil, sin activar el modo estricto. Tendrás que cumplir todos los métodos que marques para quitarlo.",
    ) {
        FgCheckRow("Esperar un tiempo", timerOn, { timerOn = it })
        if (timerOn) FgCell { StepperField("Minutos de espera", timerMinutes, { timerMinutes = it }, step = 5) }
        FgCheckRow("Pedirlo y esperar", cooldownOn, { cooldownOn = it }, subtitle = "Se concede tras la espera y vale 10 minutos.")
        if (cooldownOn) FgCell { StepperField("Minutos hasta concederlo", cooldownMinutes, { cooldownMinutes = it }, step = 5) }
        FgCheckRow("Cargador conectado", chargerOn, { chargerOn = it })
        FgCheckRow("Escribir un PIN", pinOn, { pinOn = it })
        FgCheckRow(
            "Que mi compañero lo apruebe", partnerOn, { partnerOn = it },
            subtitle = "Le llega un correo con el botón de aprobar. Necesitas un compañero vinculado.",
        )
        if (pinOn && !viewModel.hasPin) FgCell { PinField("PIN de 4 a 8 dígitos", newPin, { newPin = it }) }
    }
    error?.let { ErrorNote(it) }
    FgGroup {
        FgActionRow("Poner el candado", onClick = {
            val methods = buildList<ExitMethod> {
                if (timerOn) add(ExitMethod.Timer((timerMinutes.toIntOrNull() ?: 0).coerceAtLeast(1)))
                if (cooldownOn) add(ExitMethod.Cooldown((cooldownMinutes.toIntOrNull() ?: 0).coerceAtLeast(1)))
                if (chargerOn) add(ExitMethod.Charger)
                if (pinOn) add(ExitMethod.Pin)
                if (partnerOn) add(ExitMethod.Partner)
            }
            error = viewModel.lock(profileId, methods, newPin)
            if (error == null) newPin = ""
        })
    }
}

@Composable
private fun UnlockProgress(profileId: Long, viewModel: ProfileLockViewModel, needsPin: Boolean) {
    val nowMs = rememberTrustedNowMs()
    var pin by remember { mutableStateOf("") }
    val status = viewModel.status(profileId, pin, Instant.ofEpochMilli(nowMs))
    FgGroup(header = "Para quitar el candado") {
        status?.pending?.forEach { FgRow(requirementText(it, Instant.ofEpochMilli(nowMs))) }
        if (needsPin) FgCell { PinField("PIN", pin, { pin = it }) }
        FgActionRow("Quitar el candado", { viewModel.confirmUnlock(profileId, pin) }, enabled = status?.satisfied == true)
        FgActionRow("Cancelar", { viewModel.cancelRequest(profileId) })
    }
    if (status?.satisfied == true) FgNote("Todo cumplido. Ya puedes quitar el candado.", Modifier.padding(horizontal = 16.dp))
}

private fun methodName(method: ExitMethod) = when (method) {
    is ExitMethod.Timer -> "esperar ${method.minutes} min"
    is ExitMethod.Cooldown -> "pedirlo y esperar ${method.minutes} min"
    ExitMethod.Charger -> "cargador conectado"
    ExitMethod.Pin -> "PIN"
    ExitMethod.Partner -> "aprobación del compañero"
    ExitMethod.FollowSchedule -> "seguir el horario"
}
