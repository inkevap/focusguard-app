package com.focusguard.ui.emergency

import com.focusguard.ui.icons.FgIcons
import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focusguard.decision.EmergencyAvailability
import com.focusguard.decision.EmergencyStep
import com.focusguard.decision.EmergencyUnlock
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.HandTypedField
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecureWindowEffect
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.formatLocal
import com.focusguard.ui.rememberTrustedNowMs

/** Desbloqueo de emergencia (H12): varios pasos para pararse a pensar, una frase a mano y una espera antes de conceder unos minutos. */
@Composable
fun EmergencyScreen(viewModel: EmergencyViewModel, onBack: () -> Unit) {
    SecureWindowEffect()
    Scaffold(
        topBar = {
            FgTopBar("Desbloqueo de emergencia", onBack = onBack)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val availability = viewModel.availability) {
                null -> Unit
                is EmergencyAvailability.Available -> item { StepCard(viewModel, availability.left, onBack) }
                is EmergencyAvailability.Unavailable -> item { Unavailable(availability, onBack) }
            }
        }
    }
}

@Composable
private fun Unavailable(state: EmergencyAvailability.Unavailable, onBack: () -> Unit) {
    AppCard {
        SectionTitle("Ahora no se puede")
        Hint(
            when (state) {
                EmergencyAvailability.ByStrictMode -> "El modo estricto está activo: no admite desbloqueos de emergencia."
                EmergencyAvailability.ByPanic -> "Tienes el bloqueo de pánico en marcha. Dura hasta que termine."
                is EmergencyAvailability.QuotaSpent -> "Ya usaste todos los de esta semana. Podrás pedir otro el ${formatLocal(state.again, withDay = true)}."
            },
        )
        PrimaryButton("Volver", onBack, Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

@Composable
private fun StepCard(viewModel: EmergencyViewModel, left: Int, onBack: () -> Unit) {
    val step = viewModel.step
    val context = LocalContext.current
    AppCard {
        if (step != EmergencyStep.DONE) {
            FgProgress(progress = { step.ordinal / EmergencyStep.DONE.ordinal.toFloat() }, modifier = Modifier.fillMaxWidth())
        }
        when (step) {
            EmergencyStep.INTRO -> {
                SectionTitle("Antes de desbloquear")
                Hint("Vas a tardar un minuto. Responde con calma: nadie lo lee y no se guarda. Te quedan $left de ${viewModel.perWeek} esta semana y cada uno dura ${viewModel.minutes} min.")
            }
            EmergencyStep.WHY -> Reflection("¿Por qué lo bloqueaste?", "Piensa en lo que te llevó a ponerlo.", viewModel)
            EmergencyStep.GAIN -> Reflection("¿Qué ganas si aguantas?", "Un poco más de tiempo, calma, algo que te importa…", viewModel)
            EmergencyStep.FEEL -> Reflection("¿Cómo te sentirás si lo desbloqueas ahora?", "Dentro de un rato, ¿estarás contento con esa decisión?", viewModel)
            EmergencyStep.PLEDGE -> {
                SectionTitle("Tu compromiso")
                Hint("Escribe a mano este texto, igual que aparece:")
                Text(EmergencyUnlock.COMMITMENT, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                HandTypedField(viewModel.text, viewModel::type, Modifier.padding(top = 8.dp))
            }
            EmergencyStep.WAIT -> Waiting(viewModel)
            EmergencyStep.DONE -> {
                SectionTitle("Desbloqueado")
                val until = viewModel.grantedUntil?.let { " (hasta las ${formatLocal(it)})" }.orEmpty()
                Hint("Tienes ${viewModel.minutes} minutos$until. Pasado ese tiempo, el bloqueo vuelve solo.")
            }
        }
        when (step) {
            EmergencyStep.WAIT -> Unit
            EmergencyStep.DONE -> PrimaryButton("Volver a mi app", { (context as? Activity)?.moveTaskToBack(true); onBack() }, Modifier.fillMaxWidth().padding(top = 12.dp))
            else -> PrimaryButton(
                if (step == EmergencyStep.INTRO) "Empezar" else "Siguiente",
                viewModel::advance,
                Modifier.fillMaxWidth().padding(top = 12.dp),
                enabled = viewModel.canAdvance,
            )
        }
    }
}

@Composable
private fun Reflection(title: String, hint: String, viewModel: EmergencyViewModel) {
    SectionTitle(title)
    Hint(hint)
    OutlinedTextField(
        value = viewModel.text,
        onValueChange = viewModel::type,
        label = { Text("Tu respuesta") },
        supportingText = { Text("Al menos ${EmergencyUnlock.MIN_ANSWER_CHARS} letras") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/** Solo este paso mira el reloj cada segundo; el resto de pasos no se repinta. */
@Composable
private fun Waiting(viewModel: EmergencyViewModel) {
    val nowMs = rememberTrustedNowMs()
    val seconds = viewModel.waitLeft(nowMs)
    LaunchedEffect(seconds) { if (seconds == 0) viewModel.finish(nowMs) }
    SectionTitle("Respira", FgIcons.Wind)
    Hint(if (seconds > 0) "Quedan $seconds s. Si ya no lo necesitas, solo cierra esta pantalla." else "Un momento…")
}
