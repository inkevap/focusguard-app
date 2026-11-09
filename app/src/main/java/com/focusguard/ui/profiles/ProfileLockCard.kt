package com.focusguard.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.model.ExitMethod
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.CheckRow
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PinField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
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
    AppCard {
        SectionTitle("Candado del perfil")
        if (lock == null) {
            LockEditor(profileId, viewModel)
            return@AppCard
        }
        Hint("Puesto. No podrás apagar, pausar, editar ni borrar este perfil hasta quitarlo.")
        if (requests[profileId] == null) {
            Hint("Para quitarlo tendrás que cumplir: ${lock.exitMethods.joinToString(", ") { methodName(it) }}.")
            SecondaryButton("Pedir quitar el candado", { viewModel.requestUnlock(profileId) }, Modifier.padding(top = 8.dp))
            return@AppCard
        }
        UnlockProgress(profileId, viewModel, needsPin = lock.exitMethods.any { it is ExitMethod.Pin })
    }
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

    Hint("Protege solo este perfil, sin activar el modo estricto. Tendrás que cumplir todos los métodos que marques para quitarlo.")
    CheckRow("Esperar un tiempo", null, timerOn, { timerOn = it })
    if (timerOn) StepperField("Minutos de espera", timerMinutes, { timerMinutes = it }, Modifier.padding(start = 48.dp, top = 4.dp), step = 5)
    CheckRow("Pedirlo y esperar", "Se concede tras la espera y vale 10 minutos.", cooldownOn, { cooldownOn = it })
    if (cooldownOn) StepperField("Minutos hasta concederlo", cooldownMinutes, { cooldownMinutes = it }, Modifier.padding(start = 48.dp, top = 4.dp), step = 5)
    CheckRow("Cargador conectado", null, chargerOn, { chargerOn = it })
    CheckRow("Escribir un PIN", null, pinOn, { pinOn = it })
    CheckRow("Que mi compañero lo apruebe", "Le llega un correo con el botón de aprobar. Necesitas un compañero vinculado.", partnerOn, { partnerOn = it })
    if (pinOn && !viewModel.hasPin) PinField("PIN de 4 a 8 dígitos", newPin, { newPin = it }, Modifier.padding(top = 8.dp))
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    PrimaryButton(
        text = "Poner el candado",
        onClick = {
            val methods = buildList<ExitMethod> {
                if (timerOn) add(ExitMethod.Timer((timerMinutes.toIntOrNull() ?: 0).coerceAtLeast(1)))
                if (cooldownOn) add(ExitMethod.Cooldown((cooldownMinutes.toIntOrNull() ?: 0).coerceAtLeast(1)))
                if (chargerOn) add(ExitMethod.Charger)
                if (pinOn) add(ExitMethod.Pin)
                if (partnerOn) add(ExitMethod.Partner)
            }
            error = viewModel.lock(profileId, methods, newPin)
            if (error == null) newPin = ""
        },
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun UnlockProgress(profileId: Long, viewModel: ProfileLockViewModel, needsPin: Boolean) {
    val nowMs = rememberTrustedNowMs()
    var pin by remember { mutableStateOf("") }
    val status = viewModel.status(profileId, pin, Instant.ofEpochMilli(nowMs))
    status?.pending?.forEach {
        Text("• ${requirementText(it, Instant.ofEpochMilli(nowMs))}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
    }
    if (status?.satisfied == true) Text("Todo cumplido. Ya puedes quitar el candado.", modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge)
    if (needsPin) PinField("PIN", pin, { pin = it }, Modifier.padding(top = 12.dp))
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PrimaryButton("Quitar el candado", { viewModel.confirmUnlock(profileId, pin) }, Modifier.weight(1f), enabled = status?.satisfied == true)
        SecondaryButton("Cancelar", { viewModel.cancelRequest(profileId) }, Modifier.weight(1f))
    }
}

private fun methodName(method: ExitMethod) = when (method) {
    is ExitMethod.Timer -> "esperar ${method.minutes} min"
    is ExitMethod.Cooldown -> "pedirlo y esperar ${method.minutes} min"
    ExitMethod.Charger -> "cargador conectado"
    ExitMethod.Pin -> "PIN"
    ExitMethod.Partner -> "aprobación del compañero"
    ExitMethod.FollowSchedule -> "seguir el horario"
}
