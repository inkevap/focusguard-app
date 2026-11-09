package com.focusguard.ui.sleep

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.SleepPlan
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.TimeButton
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.formatMinutes
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.profiles.AppPickerDialog
import com.focusguard.ui.rememberTrustedNowMs
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Los paquetes de las apps de reloj y alarma instaladas (las que atienden «ver alarmas» y «poner una alarma»). Necesita `<queries>` en el manifiesto. */
internal fun clockPackages(context: Context): Set<String> {
    val pm = context.packageManager
    return listOf(AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM)
        .flatMap { pm.queryIntentActivities(Intent(it), 0) }
        .mapTo(linkedSetOf()) { it.activityInfo.packageName }
        .minus(context.packageName)
}

@Composable
fun SleepScreen(viewModel: SleepViewModel, onDone: () -> Unit) {
    val until by viewModel.activeUntil.collectAsStateWithLifecycle()
    val end = until
    // La hora de confianza se compara cada segundo: un `delay` no cuenta el tiempo con el teléfono dormido y dejaría un 0:00 clavado.
    val nowMs = rememberTrustedNowMs(1_000L)
    val running = end != null && nowMs < end.toEpochMilli()

    Scaffold(topBar = { FgTopBar("Dormir", onBack = onDone) }) { padding ->
        if (running && end != null) Running(end, nowMs, Modifier.padding(padding))
        else Chooser(viewModel, finished = end != null, nowMs = nowMs, onDone = onDone, modifier = Modifier.padding(padding))
    }
}

@Composable
private fun Running(end: Instant, nowMs: Long, modifier: Modifier = Modifier) {
    val left = Duration.ofMillis((end.toEpochMilli() - nowMs).coerceAtLeast(0))
    val wake = end.atZone(ZoneId.systemDefault()).toLocalTime()
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Despertar a las ${formatMinutes(wake.hour * 60 + wake.minute)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatRemaining(left, alwaysHours = left.toHours() > 0), style = MaterialTheme.typography.displayLarge, modifier = Modifier.padding(top = 12.dp))
        Text(
            "Todo está bloqueado menos las llamadas, FocusGuard, la alarma y las apps que permitiste. Se desbloquea solo al despertar.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun Chooser(viewModel: SleepViewModel, finished: Boolean, nowMs: Long, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var wake by rememberSaveable { mutableStateOf(SleepPlan.DEFAULT_WAKE_MINUTE) }
    var extra by remember { mutableStateOf(emptySet<String>()) }
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val minutes = viewModel.minutesUntil(wake, Instant.ofEpochMilli(nowMs))

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (finished) item { AppCard { SectionTitle("Buenos días"); Hint("Ya puedes usar el teléfono con normalidad.") } }
        item {
            AppCard {
                SectionTitle("¿A qué hora despiertas?")
                Text(formatMinutes(wake), style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 8.dp))
                Hint("Dentro de ${formatAmountOfTime(minutes.toLong())}")
                ChipChoices(
                    options = SleepPlan.WAKE_PRESETS.map { it to formatMinutes(it) },
                    selected = wake,
                    onSelect = { wake = it },
                    modifier = Modifier.padding(top = 12.dp),
                )
                TimeButton("Otra hora:", wake, SleepPlan.DEFAULT_WAKE_MINUTE, { wake = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        item {
            AppCard {
                SectionTitle("Qué sigue permitido")
                Hint("El reloj y la alarma, para que suene al despertar; las llamadas y las emergencias, que nunca se bloquean; y lo que elijas aquí, por ejemplo tu diario.")
                SecondaryButton(
                    text = if (extra.isEmpty()) "Elegir otras apps" else "Otras apps permitidas: ${extra.size}",
                    onClick = { picking = true },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        item { Hint("No se puede quitar antes de la hora, ni con una aprobación.") }
        item { PrimaryButton("Dormir hasta las ${formatMinutes(wake)}", { confirm = true }, Modifier.fillMaxWidth()) }
        if (finished) item { SecondaryButton("Volver", onDone, Modifier.fillMaxWidth()) }
    }

    if (picking) AppPickerDialog(selected = extra, onConfirm = { extra = it; picking = false }, onDismiss = { picking = false })
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Dormir hasta las ${formatMinutes(wake)}?") },
            text = { Text("El teléfono quedará bloqueado ${formatAmountOfTime(minutes.toLong())}, menos la alarma y lo que permitiste. No podrás deshacerlo hasta entonces.") },
            confirmButton = { TextButton(onClick = { confirm = false; viewModel.start(wake, extra) }) { Text("Dormir") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
