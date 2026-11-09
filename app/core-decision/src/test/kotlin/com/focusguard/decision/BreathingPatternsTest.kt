package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BreathingPatternsTest {

    @Test fun elRitmoDeSiempreSigueIgualQueElDeImpulso() {
        // Lo que ya usa «Impulso» (Breathing) y el ritmo «Calma» dan lo mismo en cualquier instante.
        listOf(0L, 1_500L, 4_000L, 7_999L, 8_000L, 13_999L, 14_000L, 40_001L).forEach {
            assertEquals(Breathing.at(it), BreathingPatterns.CALM.at(it), "en $it ms")
        }
    }

    @Test fun laRespiracionCuadradaTieneLasCuatroFases() {
        val box = BreathingPatterns.BOX
        assertEquals(16_000, box.cycleMs)
        assertEquals(BreathPhase.INHALE, box.at(0).phase)
        assertEquals(BreathPhase.HOLD, box.at(4_000).phase)
        assertEquals(BreathPhase.EXHALE, box.at(8_000).phase)
        val rest = box.at(12_000)
        assertEquals(BreathPhase.REST, rest.phase)
        assertEquals(0.5f, rest.scale)
        assertEquals(4, rest.secondsLeft)
        assertEquals(box.at(0), box.at(16_000))
    }

    @Test fun sinSostenerSeSaltaLaFase() {
        val soft = BreathingPatterns.COHERENT
        assertEquals(BreathPhase.INHALE, soft.at(4_999).phase)
        assertEquals(BreathPhase.EXHALE, soft.at(5_000).phase)
        assertEquals(1f, soft.at(5_000).scale)
        assertEquals(BreathPhase.INHALE, soft.at(10_000).phase)
    }

    @Test fun elCirculoCreceAlInspirarYEncogeAlEspirar() {
        BreathingPatterns.ALL.forEach { p ->
            assertTrue(p.at(p.inhaleMs / 2).scale > 0.5f, p.id)
            assertEquals(1f, p.at(p.inhaleMs).scale, p.id)
            assertTrue(p.at(p.inhaleMs + p.holdMs + p.exhaleMs / 2).scale < 1f, p.id)
        }
    }

    @Test fun losIdentificadoresSonUnicosYUnDesconocidoVuelveAlRitmoDeSiempre() {
        assertEquals(BreathingPatterns.ALL.size, BreathingPatterns.ALL.map { it.id }.toSet().size)
        assertTrue(BreathingPatterns.ALL.all { it.name.isNotBlank() && it.description.isNotBlank() })
        assertEquals(BreathingPatterns.CALM, BreathingPatterns.byId("no existe"))
        assertEquals(BreathingPatterns.BOX, BreathingPatterns.byId("cuadrada"))
    }

    @Test fun unRitmoSinInspirarOEspirarNoSePermite() {
        assertFailsWith<IllegalArgumentException> { BreathingPattern("x", "x", "x", 0, 1, 1) }
        assertFailsWith<IllegalArgumentException> { BreathingPattern("x", "x", "x", 1, 1, 0) }
    }
}
