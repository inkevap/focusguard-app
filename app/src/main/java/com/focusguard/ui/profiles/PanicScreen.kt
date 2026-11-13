package com.focusguard.ui.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.ui.components.BreathingPanel
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.rememberTrustedNowMs
import java.time.Duration

private val presetMinutes = listOf(15, 30, 60, 120, 240)

/**
 * Botón de pánico: bloqueo máximo durante el tiempo elegido, sin vuelta atrás. Mientras dura enseña la cuenta atrás y un ejercicio de respiración
 * opcional (FocusGuard no se bloquea, así que se puede usar con todo lo demás cerrado).
 */
@Composable
fun PanicScreen(viewModel: SessionViewModel, onDone: () -> Unit) {
    val until by viewModel.panicUntil.collectAsStateWithLifecycle()
    // La hora de confianza se compara cada segundo: un `delay` no cuenta el tiempo con el teléfono dormido.
    val nowMs = rememberTrustedNowMs(1_000L)
    val end = until
    val running = end != null && nowMs < end.toEpochMilli()
    FgScreen("Ayúdame", onBack = onDone) {
        if (running && end != null) {
            item {
                val left = Duration.ofMillis((end.toEpochMilli() - nowMs).coerceAtLeast(0))
                FgHero(
                    title = "Todo está bloqueado",
                    number = formatRemaining(left, alwaysHours = left.toHours() > 0),
                    body = "Quedan las llamadas, las emergencias y FocusGuard. Se desbloquea solo cuando acabe el tiempo: no se puede quitar antes.",
                )
            }
            item { BreathingPanel() }
        } else {
            item {
                FgHero(
                    title = "Qué pasa al activarlo",
                    body = "Se bloquea todo el teléfono menos las llamadas, las emergencias y FocusGuard. Hasta que pase el tiempo no se puede quitar, ni con aprobación. Mientras tanto tendrás un ejercicio de respiración opcional.",
                )
            }
            item {
                IrreversibleDurationForm(
                    presets = presetMinutes, defaultMinutes = 30, hint = "Elige cuánto tiempo quieres tener todo bloqueado.", buttonLabel = "Activar",
                    // Se queda en esta pantalla: pasa a enseñar la cuenta atrás y la respiración.
                    onStart = { viewModel.startPanic(it) {} },
                )
            }
        }
    }
}
