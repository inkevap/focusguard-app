package com.focusguard.ui.sleep

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.SleepPlan
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.TimeButton
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.formatMinutes
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.profiles.AppPickerDialog
import com.focusguard.ui.profiles.BottomAction
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

    if (running && end != null) Running(end, nowMs, onBack = onDone)
    else Chooser(viewModel, finished = end != null, nowMs = nowMs, onDone = onDone)
}

@Composable
private fun Running(end: Instant, nowMs: Long, onBack: () -> Unit) {
    val left = Duration.ofMillis((end.toEpochMilli() - nowMs).coerceAtLeast(0))
    val wake = end.atZone(ZoneId.systemDefault()).toLocalTime()
    FgScreen("Dormir", onBack = onBack) {
        item {
            FgHero(
                title = "Despertar a las ${formatMinutes(wake.hour * 60 + wake.minute)}",
                number = formatRemaining(left, alwaysHours = left.toHours() > 0),
                body = "Todo está bloqueado menos las llamadas, FocusGuard, la alarma y las apps que permitiste. Se desbloquea solo al despertar.",
            )
        }
    }
}

@Composable
private fun Chooser(viewModel: SleepViewModel, finished: Boolean, nowMs: Long, onDone: () -> Unit) {
    var wake by rememberSaveable { mutableStateOf(SleepPlan.DEFAULT_WAKE_MINUTE) }
    var extra by remember { mutableStateOf(emptySet<String>()) }
    var picking by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val minutes = viewModel.minutesUntil(wake, Instant.ofEpochMilli(nowMs))

    FgScreen(
        "Dormir", onBack = onDone,
        bottomBar = { BottomAction { PrimaryButton("Dormir hasta las ${formatMinutes(wake)}", { confirm = true }, Modifier.fillMaxWidth()) } },
    ) {
        if (finished) item { FgHero("Buenos días", body = "Ya puedes usar el teléfono con normalidad.") }
        item {
            FgHero(
                title = "Dentro de ${formatAmountOfTime(minutes.toLong())}",
                number = formatMinutes(wake),
                body = "¿A qué hora despiertas?",
            )
        }
        item {
            FgGroup {
                FgCell { ChipChoices(options = SleepPlan.WAKE_PRESETS.map { it to formatMinutes(it) }, selected = wake, onSelect = { wake = it }) }
                FgCell { TimeButton("Otra hora:", wake, SleepPlan.DEFAULT_WAKE_MINUTE, { wake = it }, Modifier.fillMaxWidth()) }
            }
        }
        item {
            FgGroup(
                header = "Qué sigue permitido",
                footer = "El reloj y la alarma, para que suene al despertar; las llamadas y las emergencias, que nunca se bloquean; y lo que elijas aquí, por ejemplo tu diario. No se puede quitar antes de la hora, ni con una aprobación.",
            ) {
                FgRow(
                    if (extra.isEmpty()) "Elegir otras apps" else "Otras apps permitidas",
                    value = if (extra.isEmpty()) null else extra.size.toString(),
                    onClick = { picking = true },
                )
            }
        }
        if (finished) item { FgGroup { FgActionRow("Volver", onDone) } }
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
