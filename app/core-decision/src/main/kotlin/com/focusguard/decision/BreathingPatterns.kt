package com.focusguard.decision

/**
 * Un ritmo de respiración en bucle: inspirar [inhaleMs], sostener [holdMs] con los pulmones llenos, espirar [exhaleMs] y sostener [restMs] con ellos
 * vacíos (solo la respiración «cuadrada»). Los tramos que valen cero se saltan.
 */
data class BreathingPattern(
    val id: String,
    val name: String,
    val description: String,
    val inhaleMs: Long,
    val holdMs: Long,
    val exhaleMs: Long,
    val restMs: Long = 0,
) {
    val cycleMs: Long = inhaleMs + holdMs + exhaleMs + restMs

    init {
        require(inhaleMs > 0 && exhaleMs > 0 && holdMs >= 0 && restMs >= 0) { "ritmo de respiración no válido" }
    }

    /** El instante [elapsedMs] desde que empezó. [BreathState.scale] va de 0,5 (vacíos) a 1 (llenos) para dibujar el círculo. */
    fun at(elapsedMs: Long): BreathState {
        val t = elapsedMs.coerceAtLeast(0) % cycleMs
        val afterInhale = inhaleMs
        val afterHold = afterInhale + holdMs
        val afterExhale = afterHold + exhaleMs
        return when {
            t < afterInhale -> BreathState(BreathPhase.INHALE, EMPTY + (1 - EMPTY) * t / inhaleMs, left(afterInhale - t))
            t < afterHold -> BreathState(BreathPhase.HOLD, 1f, left(afterHold - t))
            t < afterExhale -> BreathState(BreathPhase.EXHALE, 1f - (1 - EMPTY) * (t - afterHold) / exhaleMs, left(afterExhale - t))
            else -> BreathState(BreathPhase.REST, EMPTY, left(cycleMs - t))
        }
    }

    private fun left(ms: Long) = ((ms + 999) / 1000).toInt()

    private companion object {
        const val EMPTY = 0.5f
    }
}

/** Los ejercicios que se pueden elegir. El primero es el de siempre (el de «Impulso»). */
object BreathingPatterns {
    val CALM = BreathingPattern("calma", "Calma 4-4-6", "Inspira 4 segundos, sostén 4 y espira 6. El de siempre: alarga la salida del aire para bajar el pulso.", 4_000, 4_000, 6_000)
    val RELAX = BreathingPattern("relajar", "Relajar 4-7-8", "Inspira 4 segundos, sostén 7 y espira 8. Más lento: pensado para antes de dormir o cuando hay mucha ansiedad.", 4_000, 7_000, 8_000)
    val BOX = BreathingPattern("cuadrada", "Cuadrada 4-4-4-4", "Inspira 4 segundos, sostén 4, espira 4 y sostén 4 con los pulmones vacíos. Parejo y fácil de seguir.", 4_000, 4_000, 4_000, 4_000)
    val COHERENT = BreathingPattern("coherente", "Suave 5-5", "Inspira 5 segundos y espira 5, sin pausas. El más sencillo, para empezar.", 5_000, 0, 5_000)

    val ALL = listOf(CALM, RELAX, BOX, COHERENT)

    fun byId(id: String): BreathingPattern = ALL.firstOrNull { it.id == id } ?: CALM
}
