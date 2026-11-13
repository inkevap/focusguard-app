package com.focusguard.ui.emergency

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focusguard.decision.EmergencyAvailability
import com.focusguard.decision.EmergencyStep
import com.focusguard.decision.EmergencyUnlock
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.HandTypedField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecureWindowEffect
import com.focusguard.ui.formatLocal
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.profiles.BottomAction
import com.focusguard.ui.rememberTrustedNowMs

/** Desbloqueo de emergencia (H12): varios pasos para pararse a pensar, una frase a mano y una espera antes de conceder unos minutos. */
@Composable
fun EmergencyScreen(viewModel: EmergencyViewModel, onBack: () -> Unit) {
    SecureWindowEffect()
    val context = LocalContext.current
    val availability = viewModel.availability
    val step = viewModel.step
    FgScreen(
        "Desbloqueo de emergencia", onBack = onBack,
        bottomBar = {
            when {
                availability is EmergencyAvailability.Unavailable -> BottomAction { PrimaryButton("Volver", onBack, Modifier.fillMaxWidth()) }
                availability is EmergencyAvailability.Available && step == EmergencyStep.DONE ->
                    BottomAction { PrimaryButton("Volver a mi app", { (context as? Activity)?.moveTaskToBack(true); onBack() }, Modifier.fillMaxWidth()) }
                availability is EmergencyAvailability.Available && step != EmergencyStep.WAIT -> BottomAction {
                    PrimaryButton(
                        if (step == EmergencyStep.INTRO) "Empezar" else "Siguiente",
                        viewModel::advance,
                        Modifier.fillMaxWidth(),
                        enabled = viewModel.canAdvance,
                    )
                }
            }
        },
    ) {
        when (availability) {
            null -> Unit
            is EmergencyAvailability.Available -> item { StepContent(viewModel, availability.left) }
            is EmergencyAvailability.Unavailable -> item { Unavailable(availability) }
        }
    }
}

/** El título de un paso y su explicación, sin tarjeta. */
@Composable
private fun StepTitle(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Unavailable(state: EmergencyAvailability.Unavailable) {
    StepTitle(
        "Ahora no se puede",
        when (state) {
            EmergencyAvailability.ByStrictMode -> "El modo estricto está activo: no admite desbloqueos de emergencia."
            EmergencyAvailability.ByPanic -> "Tienes el bloqueo de pánico en marcha. Dura hasta que termine."
            is EmergencyAvailability.QuotaSpent -> "Ya usaste todos los de esta semana. Podrás pedir otro el ${formatLocal(state.again, withDay = true)}."
        },
    )
}

@Composable
private fun StepContent(viewModel: EmergencyViewModel, left: Int) {
    val step = viewModel.step
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (step != EmergencyStep.DONE) {
            FgProgress(progress = { step.ordinal / EmergencyStep.DONE.ordinal.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
        when (step) {
            EmergencyStep.INTRO -> StepTitle(
                "Antes de desbloquear",
                "Vas a tardar un minuto. Responde con calma: nadie lo lee y no se guarda. Te quedan $left de ${viewModel.perWeek} esta semana y cada uno dura ${viewModel.minutes} min.",
            )
            EmergencyStep.WHY -> Reflection("¿Por qué lo bloqueaste?", "Piensa en lo que te llevó a ponerlo.", viewModel)
            EmergencyStep.GAIN -> Reflection("¿Qué ganas si aguantas?", "Un poco más de tiempo, calma, algo que te importa…", viewModel)
            EmergencyStep.FEEL -> Reflection("¿Cómo te sentirás si lo desbloqueas ahora?", "Dentro de un rato, ¿estarás contento con esa decisión?", viewModel)
            EmergencyStep.PLEDGE -> {
                StepTitle("Tu compromiso", "Escribe a mano este texto, igual que aparece:")
                FgGroup {
                    FgCell {
                        Text(EmergencyUnlock.COMMITMENT, style = MaterialTheme.typography.bodyLarge)
                        HandTypedField(viewModel.text, viewModel::type, Modifier.padding(top = 4.dp))
                    }
                }
            }
            EmergencyStep.WAIT -> Waiting(viewModel)
            EmergencyStep.DONE -> {
                val until = viewModel.grantedUntil?.let { " (hasta las ${formatLocal(it)})" }.orEmpty()
                StepTitle("Desbloqueado", "Tienes ${viewModel.minutes} minutos$until. Pasado ese tiempo, el bloqueo vuelve solo.")
            }
        }
    }
}

@Composable
private fun Reflection(title: String, hint: String, viewModel: EmergencyViewModel) {
    StepTitle(title, hint)
    FgGroup {
        FgCell {
            OutlinedTextField(
                value = viewModel.text,
                onValueChange = viewModel::type,
                label = { Text("Tu respuesta") },
                supportingText = { Text("Al menos ${EmergencyUnlock.MIN_ANSWER_CHARS} letras") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Solo este paso mira el reloj cada segundo; el resto de pasos no se repinta. */
@Composable
private fun Waiting(viewModel: EmergencyViewModel) {
    val nowMs = rememberTrustedNowMs()
    val seconds = viewModel.waitLeft(nowMs)
    LaunchedEffect(seconds) { if (seconds == 0) viewModel.finish(nowMs) }
    Icon(FgIcons.Wind, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp).padding(start = 4.dp))
    StepTitle("Respira", if (seconds > 0) "Quedan $seconds s. Si ya no lo necesitas, solo cierra esta pantalla." else "Un momento…")
}
