package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreathHapticsTest {

    @Test fun cadaFaseTieneSuPulsacionYLaAmplitudCuadra() {
        BreathPhase.entries.forEach { phase ->
            val t = BreathHaptics.timings(phase)
            val a = BreathHaptics.amplitudes(phase)
            assertEquals(t.size, a.size, phase.name)
            assertTrue(t.size % 2 == 0, "pares de pausa y vibración: ${phase.name}")
            // Las pausas no vibran y las pulsaciones sí, pero suaves.
            t.indices.forEach { i -> assertEquals(if (i % 2 == 0) 0 else BreathHaptics.AMPLITUDE, a[i]) }
            assertTrue(BreathHaptics.AMPLITUDE in 1..120, "suave")
        }
    }

    @Test fun espirarEsLaPulsacionMasLargaYSostenerSonDosCortas() {
        assertTrue(BreathHaptics.timings(BreathPhase.EXHALE).max() > BreathHaptics.timings(BreathPhase.INHALE).max())
        assertEquals(2, BreathHaptics.timings(BreathPhase.HOLD).count { it in 1..60 })
        assertTrue(BreathHaptics.timings(BreathPhase.REST).contentEquals(BreathHaptics.timings(BreathPhase.HOLD)))
    }
}
