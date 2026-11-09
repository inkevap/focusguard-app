package com.focusguard.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.focusguard.data.TrustedClock
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * Hora de confianza en milisegundos, repintada cada [periodMs] mientras la pantalla está visible.
 * Con cuentas atrás basta un segundo; para estados que solo cambian en los límites de un horario, medio minuto.
 */
@Composable
fun rememberTrustedNowMs(periodMs: Long = 1_000L): Long {
    val clock: TrustedClock = koinInject()
    var nowMs by remember { mutableLongStateOf(clock.nowMs()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, periodMs) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMs = clock.nowMs()
                delay(periodMs)
            }
        }
    }
    return nowMs
}
