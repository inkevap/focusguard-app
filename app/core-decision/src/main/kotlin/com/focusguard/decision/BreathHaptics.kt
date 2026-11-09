package com.focusguard.decision

/**
 * La vibración suave que marca cada cambio de fase del ejercicio de respiración, para poder seguirlo con los ojos cerrados: una pulsación corta al
 * inspirar, dos muy cortas al sostener y una larga al espirar.
 */
object BreathHaptics {

    /** Amplitud de las pulsaciones (de 1 a 255): suave, para que no moleste ni suene a aviso. */
    const val AMPLITUDE = 70

    /** Pausa, vibra, pausa, vibra…: los tramos pares son pausas y los impares, vibración (en milisegundos). */
    fun timings(phase: BreathPhase): LongArray = when (phase) {
        BreathPhase.INHALE -> longArrayOf(0, 70)
        BreathPhase.HOLD, BreathPhase.REST -> longArrayOf(0, 35, 80, 35)
        BreathPhase.EXHALE -> longArrayOf(0, 170)
    }

    /** La amplitud de cada tramo de [timings]: cero en las pausas. */
    fun amplitudes(phase: BreathPhase): IntArray = IntArray(timings(phase).size) { if (it % 2 == 0) 0 else AMPLITUDE }
}
