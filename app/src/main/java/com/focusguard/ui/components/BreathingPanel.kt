package com.focusguard.ui.components

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focusguard.data.BreathingPrefs
import com.focusguard.decision.BreathHaptics
import com.focusguard.decision.BreathPhase
import com.focusguard.decision.BreathingPattern
import com.focusguard.decision.BreathingPatterns
import com.focusguard.ui.icons.FgIcons
import org.koin.compose.koinInject

/**
 * Ejercicio de respiración **opcional**: se elige un ritmo (se recuerda el último), se empieza y se para cuando se quiere, y puede marcar cada fase
 * con una vibración suave o un sonido suave. Con [startRunning] arranca solo (en «Impulso», donde se viene a esto). Es una tarjeta suelta: hoy la usan
 * Ayúdame, donde FocusGuard no se bloquea nunca y se puede usar con todo lo demás cerrado, e Impulso.
 */
@Composable
fun BreathingPanel(modifier: Modifier = Modifier, startRunning: Boolean = false) {
    val prefs: BreathingPrefs = koinInject()
    val context = LocalContext.current
    var patternId by remember { mutableStateOf(prefs.patternId) }
    var vibrate by remember { mutableStateOf(prefs.vibrate) }
    var sound by remember { mutableStateOf(prefs.sound) }
    var running by rememberSaveable { mutableStateOf(startRunning) }
    val pattern = BreathingPatterns.byId(patternId)
    val clock = rememberFrameClock(pattern.id, running)

    // Cada cambio de fase (y el primer inspira) se marca con lo que el usuario haya activado.
    val currentVibrate by rememberUpdatedState(vibrate)
    val currentSound by rememberUpdatedState(sound)
    LaunchedEffect(pattern.id, running) {
        if (!running) return@LaunchedEffect
        snapshotFlow { pattern.at(clock.value).phase }.collect { phase ->
            if (currentVibrate) vibrateFor(context, phase)
            if (currentSound) softTick()
        }
    }

    BreathingPanelContent(
        pattern = pattern,
        onPattern = { patternId = it; prefs.patternId = it; running = false },
        running = running,
        onRunning = { running = it },
        vibrate = vibrate,
        onVibrate = { vibrate = it; prefs.vibrate = it },
        sound = sound,
        onSound = { sound = it; prefs.sound = it },
        elapsedMs = { clock.value },
        modifier = modifier,
    )
}

/** Lo que se ve, sin estado propio: lo usan [BreathingPanel] y las capturas de pantalla. [elapsedMs] es el tiempo desde que empezó el ejercicio. */
@Composable
fun BreathingPanelContent(
    pattern: BreathingPattern,
    onPattern: (String) -> Unit,
    running: Boolean,
    onRunning: (Boolean) -> Unit,
    vibrate: Boolean,
    onVibrate: (Boolean) -> Unit,
    sound: Boolean,
    onSound: (Boolean) -> Unit,
    elapsedMs: () -> Long,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier) {
        SectionTitle("Respirar conmigo (opcional)", FgIcons.Wind)
        Hint("Un ejercicio para dejar pasar el impulso. Elige un ritmo y para cuando quieras; se recuerda el último que usaste.")
        ChipChoices(
            options = BreathingPatterns.ALL.map { it.id to it.name },
            selected = pattern.id,
            onSelect = onPattern,
            modifier = Modifier.padding(top = 8.dp),
        )
        Hint(pattern.description)
        SwitchRow("Vibración suave", "Una pulsación distinta al inspirar, sostener y espirar: se puede seguir con los ojos cerrados.", vibrate, onVibrate)
        SwitchRow("Sonido suave", "Un toque muy bajo en cada cambio. Suena aunque el teléfono esté en silencio.", sound, onSound)
        if (running) BreathCircle(pattern, elapsedMs)
        if (running) {
            SecondaryButton("Parar", { onRunning(false) }, Modifier.fillMaxWidth().padding(top = 8.dp))
        } else {
            PrimaryButton("Empezar", { onRunning(true) }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun BreathCircle(pattern: BreathingPattern, elapsedMs: () -> Long) {
    // La fase y los segundos cambian una vez por segundo; la escala, en cada fotograma, y solo se lee al dibujar.
    val label by remember(pattern, elapsedMs) { derivedStateOf { pattern.at(elapsedMs()).let { "${it.phase.label}\n${it.secondsLeft}" } } }
    val color = MaterialTheme.colorScheme.primaryContainer
    Box(
        Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp).semantics { contentDescription = "Ejercicio de respiración" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color, radius = size.minDimension / 2 * pattern.at(elapsedMs()).scale, center = Offset(size.width / 2, size.height / 2))
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
    }
}

/** Milisegundos desde que empezó, al ritmo de los fotogramas; solo corre con [running] y se reinicia al cambiar de ritmo o al volver a empezar. */
@Composable
private fun rememberFrameClock(key: Any, running: Boolean): State<Long> {
    val elapsed = remember(key, running) { mutableLongStateOf(0L) }
    LaunchedEffect(key, running) {
        if (!running) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) elapsed.longValue = withFrameMillis { it } - start
    }
    return elapsed
}

@Suppress("DEPRECATION")
private fun vibrateFor(context: Context, phase: BreathPhase) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
    if (!vibrator.hasVibrator()) return
    val effect = if (vibrator.hasAmplitudeControl()) {
        VibrationEffect.createWaveform(BreathHaptics.timings(phase), BreathHaptics.amplitudes(phase), -1)
    } else {
        VibrationEffect.createWaveform(BreathHaptics.timings(phase), -1)
    }
    vibrator.vibrate(effect)
}

/** Un toque breve y bajo; el generador se suelta al acabar. */
private fun softTick() {
    runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_MUSIC, SOFT_VOLUME)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP, TICK_MS)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, TICK_MS + 150L)
    }
}

private const val SOFT_VOLUME = 25
private const val TICK_MS = 90
