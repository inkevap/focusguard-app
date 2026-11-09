package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecoveryTest {
    private fun done(vararg pairs: Pair<Int, Long>) = pairs.associate { (step, day) -> step to StepRecord(day, "x") }

    @Test fun haySietePasosEnOrdenYConIdentificadoresSeguidos() {
        assertEquals((1..7).toList(), Recovery.STEPS.map { it.id })
        assertTrue(Recovery.STEPS.all { it.title.isNotBlank() && it.intro.isNotBlank() })
    }

    @Test fun alEmpezarSoloEstaAbiertoElPrimero() {
        val statuses = Recovery.statuses(emptyMap(), today = 100)
        assertEquals(StepStatus.Open, statuses[0])
        assertTrue(statuses.drop(1).all { it is StepStatus.Locked })
    }

    @Test fun elSiguienteSeAbreAlDiaSiguiente() {
        val sameDay = Recovery.statuses(done(1 to 100), today = 100)
        assertIs<StepStatus.Done>(sameDay[0])
        assertEquals(StepStatus.Locked(101), sameDay[1])
        assertEquals(StepStatus.Locked(null), sameDay[2])
        val nextDay = Recovery.statuses(done(1 to 100), today = 101)
        assertEquals(StepStatus.Open, nextDay[1])
        assertEquals(StepStatus.Locked(null), nextDay[2])
    }

    @Test fun losDiasSinHacerNadaNoAbrenMasPasos() {
        val statuses = Recovery.statuses(done(1 to 100), today = 150)
        assertEquals(StepStatus.Open, statuses[1])
        assertTrue(statuses.drop(2).all { it == StepStatus.Locked(null) })
    }

    @Test fun conTodoHechoNoQuedaNadaAbierto() {
        val all = Recovery.STEPS.associate { it.id to StepRecord(100L + it.id, "x") }
        assertTrue(Recovery.statuses(all, today = 200).all { it is StepStatus.Done })
        assertEquals(7, Recovery.doneCount(all))
    }

    @Test fun losPasosDeElegirNecesitanAlgoElegido() {
        val triggers = Recovery.STEPS[1]
        assertFalse(Recovery.canComplete(triggers, ""))
        assertFalse(Recovery.canComplete(triggers, "||"))
        assertTrue(Recovery.canComplete(triggers, Recovery.encodeChoices(listOf("BOREDOM"))))
        assertFalse(Recovery.canComplete(Recovery.STEPS[2], ""))
    }

    @Test fun losPasosDeEscribirNecesitanUnaFraseYLosDeLeerNoPidenNada() {
        val plan = Recovery.STEPS[3]
        assertFalse(Recovery.canComplete(plan, "   "))
        assertFalse(Recovery.canComplete(plan, "ab"))
        assertTrue(Recovery.canComplete(plan, "salir a caminar"))
        assertTrue(Recovery.canComplete(Recovery.STEPS[0], ""))
    }

    @Test fun lasEleccionesSeGuardanYSeLeenIgual() {
        val choices = listOf("BOREDOM", "STRESS")
        assertEquals(choices, Recovery.decodeChoices(Recovery.encodeChoices(choices)))
        assertEquals(emptyList(), Recovery.decodeChoices(""))
    }

    @Test fun loEscritoVaEnUnaLineaYConTope() {
        assertEquals("a b c", Recovery.cleanNote("  a\n b\t\tc  "))
        assertEquals(Recovery.MAX_NOTE, Recovery.cleanNote("x".repeat(2000)).length)
    }

    @Test fun todosLosMomentosYDisparadoresTienenTexto() {
        assertTrue(Recovery.MOMENTS.all { it.isNotBlank() && '|' !in it })
        assertTrue(UrgeTrigger.entries.all { '|' !in it.name })
    }

    @Test fun loQueSeGuardaSoloPuedeSerDeLasOpcionesDelPaso() {
        val triggers = Recovery.STEPS[1]
        assertEquals("BOREDOM|STRESS", Recovery.normalize(triggers, "BOREDOM|inventado|STRESS|BOREDOM"))
        assertEquals("", Recovery.normalize(triggers, "inventado"))
        assertFalse(Recovery.canComplete(triggers, Recovery.normalize(triggers, "inventado")))
        val moments = Recovery.STEPS[2]
        assertEquals(Recovery.MOMENTS[1], Recovery.normalize(moments, "${Recovery.MOMENTS[1]}|otro momento"))
    }

    @Test fun loEscritoSeLimpiaYUnPasoDeLeerNoGuardaNada() {
        assertEquals("salir a caminar", Recovery.normalize(Recovery.STEPS[3], "  salir\n a   caminar "))
        assertEquals("", Recovery.normalize(Recovery.STEPS[0], "lo que sea"))
        assertEquals(Recovery.MAX_NOTE, Recovery.normalize(Recovery.STEPS[3], "x".repeat(5000)).length)
    }
}
