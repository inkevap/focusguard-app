package com.focusguard.decision

import com.focusguard.model.ProfileUsage

/**
 * Acumula el uso de un perfil. El servicio lo llama cada segundo con [tick].
 * Es una función pura: devuelve el nuevo estado y no guarda nada.
 */
object UsageAccumulator {

    /** Pasado este descanso sin uso, el contador de uso continuo vuelve a cero. */
    const val CONTINUOUS_GAP_MS = 5 * 60_000L

    /** Una apertura cuenta si pasaron al menos 30 s desde el último uso (G2). */
    const val OPEN_GAP_MS = 30_000L

    /** Tope de un solo paso, para no contar de golpe un hueco largo (apagado, suspensión). */
    const val MAX_STEP_MS = 5_000L

    fun tick(previous: ProfileUsage, nowMs: Long, inUse: Boolean, periodStartMs: Long): ProfileUsage {
        val base = if (previous.periodStartMs == periodStartMs) {
            previous
        } else {
            previous.copy(periodStartMs = periodStartMs, timeMs = 0, opens = 0)
        }
        if (!inUse) return base.copy(inUse = false)

        val idleMs = nowMs - base.lastActiveMs
        val continuousMs = if (base.lastActiveMs == 0L || idleMs >= CONTINUOUS_GAP_MS) 0 else base.continuousMs
        val isOpen = !base.inUse && (base.lastActiveMs == 0L || idleMs >= OPEN_GAP_MS)
        val elapsedMs = if (base.inUse) idleMs.coerceIn(0, MAX_STEP_MS) else 0

        return base.copy(
            timeMs = base.timeMs + elapsedMs,
            opens = base.opens + if (isOpen) 1 else 0,
            continuousMs = continuousMs + elapsedMs,
            lastActiveMs = nowMs,
            inUse = true,
        )
    }
}
