package com.focusguard.decision

import kotlin.math.abs

/** [offsetMs] se resta a la hora del teléfono para obtener la de confianza; es cero mientras nadie toque el reloj. */
data class ClockAnchor(
    val wallMs: Long,
    val elapsedMs: Long,
    val bootId: Long,
    val offsetMs: Long = 0,
) {
    val trustedMs: Long get() = wallMs - offsetMs
}

/**
 * Hora de confianza: con la hora automática activada se fía del teléfono; si no, compara la hora del teléfono con el reloj monótono (`elapsedRealtime`) entre dos
 * lecturas y descarta la diferencia, para que cambiar la fecha a mano no acorte una espera.
 *
 * Tras un reinicio el monótono vuelve a cero, así que un salto hacia delante justo antes de reiniciar no se detecta.
 */
object ClockGuard {

    /** Margen para sincronizaciones de red y pequeños desajustes. */
    const val TOLERANCE_MS = 2 * 60_000L

    fun step(previous: ClockAnchor?, wallNow: Long, elapsedNow: Long, bootId: Long, autoTime: Boolean = false): ClockAnchor {
        // Con la hora automática el teléfono no deja cambiarla a mano: los saltos son correcciones de la red.
        if (previous == null || autoTime) return ClockAnchor(wallNow, elapsedNow, bootId)

        // Si falta el contador de arranques, el monótono que retrocede también delata un reinicio.
        if (previous.bootId != bootId || elapsedNow < previous.elapsedMs) {
            // Reinicio: el monótono no sirve. Se acepta la hora del teléfono, pero la de confianza nunca retrocede.
            val offset = minOf(previous.offsetMs, wallNow - previous.trustedMs)
            return ClockAnchor(wallNow, elapsedNow, bootId, offset)
        }

        val wallDelta = wallNow - previous.wallMs
        val elapsedDelta = elapsedNow - previous.elapsedMs
        val jump = wallDelta - elapsedDelta
        val offset = if (abs(jump) > TOLERANCE_MS) previous.offsetMs + jump else previous.offsetMs
        return ClockAnchor(wallNow, elapsedNow, bootId, offset)
    }
}
