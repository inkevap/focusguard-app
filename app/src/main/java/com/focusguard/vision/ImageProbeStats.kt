package com.focusguard.vision

import com.focusguard.decision.CaptureRisk
import com.focusguard.decision.ImageLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Lo que ha pasado desde que se encendió el registro de imágenes (solo en memoria; no se guarda nada y no lleva ninguna app ni imagen).
 * [buckets] cuenta las puntuaciones de «explícito» en diez tramos de 0,1 (el primero es de 0 a 0,1): sirven para ajustar los umbrales.
 */
data class ImageProbeSnapshot(
    val attempts: Int = 0,
    val scored: Int = 0,
    val skipped: Int = 0,
    val unchanged: Int = 0,
    val failed: Int = 0,
    val doubtful: Int = 0,
    /** Capturas explícitas y capturas sugerentes (estas no bloquean: solo hacen mirar más de cerca). */
    val flagged: Int = 0,
    val suggestive: Int = 0,
    /** Cuántas veces se bloqueó algo y cuántas se ofreció bloquearlo con una notificación. */
    val blocked: Int = 0,
    val offered: Int = 0,
    val captureMs: Long = 0,
    val scoreMs: Long = 0,
    val maxScoreMs: Long = 0,
    val lastError: Int? = null,
    val modelMissing: Boolean = false,
    val unsupported: Boolean = false,
    /** NudeNet no está en esta versión: puntúa solo GantMan, que es menos preciso. */
    val nudenetMissing: Boolean = false,
    /** Lo que queda de vigilancia (0 si no hay). */
    val vigilanceLeftMs: Long = 0,
    /** El modo dinámico: nivel de alerta ahora (0 a 3), incidentes que cuentan en el periodo y la sensibilidad que se aplica (1 a 5). */
    val adaptiveLevel: Int = 0,
    val adaptiveIncidents: Int = 0,
    val effectiveSensitivity: Int = 0,
    /** El riesgo y el tiempo entre capturas que se aplican ahora a lo que hay en pantalla; solo para enseñarlo. */
    val rhythmRisk: CaptureRisk? = null,
    val rhythmMs: Long = 0,
    val buckets: List<Int> = List(BUCKETS) { 0 },
) {
    val averageCaptureMs: Long get() = if (scored + failed == 0) 0 else captureMs / (scored + failed)
    val averageScoreMs: Long get() = if (scored == 0) 0 else scoreMs / scored

    companion object {
        const val BUCKETS = 10
    }
}

class ImageProbeStats {

    private val _snapshot = MutableStateFlow(ImageProbeSnapshot())
    val snapshot: StateFlow<ImageProbeSnapshot> = _snapshot.asStateFlow()

    fun reset() { _snapshot.value = ImageProbeSnapshot() }

    fun skipped() = _snapshot.update { it.copy(attempts = it.attempts + 1, skipped = it.skipped + 1) }

    fun unchanged(captureMs: Long) = _snapshot.update { it.copy(attempts = it.attempts + 1, unchanged = it.unchanged + 1, captureMs = it.captureMs + captureMs) }

    fun failed(captureMs: Long, error: Int?) =
        _snapshot.update { it.copy(attempts = it.attempts + 1, failed = it.failed + 1, captureMs = it.captureMs + captureMs, lastError = error ?: it.lastError) }

    fun scored(captureMs: Long, scoreMs: Long, probability: Double, doubtful: Boolean, level: ImageLevel) = _snapshot.update {
        val bucket = (probability * ImageProbeSnapshot.BUCKETS).toInt().coerceIn(0, ImageProbeSnapshot.BUCKETS - 1)
        it.copy(
            attempts = it.attempts + 1,
            scored = it.scored + 1,
            doubtful = it.doubtful + if (doubtful) 1 else 0,
            flagged = it.flagged + if (level == ImageLevel.EXPLICIT) 1 else 0,
            suggestive = it.suggestive + if (level == ImageLevel.SUGGESTIVE) 1 else 0,
            captureMs = it.captureMs + captureMs,
            scoreMs = it.scoreMs + scoreMs,
            maxScoreMs = maxOf(it.maxScoreMs, scoreMs),
            buckets = it.buckets.mapIndexed { i, n -> if (i == bucket) n + 1 else n },
        )
    }

    fun rhythm(risk: CaptureRisk, ms: Long, vigilanceLeftMs: Long) = _snapshot.update {
        // Los segundos de vigilancia cambian cada vuelta: se redondean al minuto para no repintar la tarjeta sin necesidad.
        val left = (vigilanceLeftMs + 59_999) / 60_000 * 60_000
        if (it.rhythmRisk == risk && it.rhythmMs == ms && it.vigilanceLeftMs == left) it else it.copy(rhythmRisk = risk, rhythmMs = ms, vigilanceLeftMs = left)
    }

    fun blocked() = _snapshot.update { it.copy(blocked = it.blocked + 1) }

    fun offered() = _snapshot.update { it.copy(offered = it.offered + 1) }

    fun adaptive(level: Int, incidents: Int, effectiveSensitivity: Int) = _snapshot.update {
        if (it.adaptiveLevel == level && it.adaptiveIncidents == incidents && it.effectiveSensitivity == effectiveSensitivity) it
        else it.copy(adaptiveLevel = level, adaptiveIncidents = incidents, effectiveSensitivity = effectiveSensitivity)
    }

    fun modelMissing() = _snapshot.update { it.copy(modelMissing = true) }

    fun nudenetMissing() = _snapshot.update { it.copy(nudenetMissing = true) }

    fun unsupported() = _snapshot.update { it.copy(unsupported = true) }
}
