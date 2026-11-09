package com.focusguard.decision

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UrgesTest {

    @Test
    fun laCuentaAtrasEsDeVeinteMinutos() {
        assertEquals(20 * 60_000L, Urges.remainingMs(1_000, 1_000))
        assertEquals(19 * 60_000L, Urges.remainingMs(0, 60_000))
        assertEquals(0, Urges.remainingMs(0, 25 * 60_000L))
        // Un reloj que va hacia atrás no la alarga.
        assertEquals(Urges.DURATION_MS, Urges.remainingMs(10_000, 5_000))
    }

    @Test
    fun unImpulsoSinContestarSeAbandonaTrasUnaHora() {
        assertFalse(Urges.isStale(0, 60 * 60_000L))
        assertTrue(Urges.isStale(0, 60 * 60_000L + 1))
    }

    @Test
    fun losMensajesSeTurnanCadaMinutoYEmpiezanPorElDeLos20Minutos() {
        assertTrue("20 minutos" in Urges.messageAt(0))
        assertEquals(Urges.MESSAGES[1], Urges.messageAt(60_000))
        assertEquals(Urges.MESSAGES[0], Urges.messageAt(Urges.MESSAGE_EVERY_MS * Urges.MESSAGES.size))
        assertEquals(Urges.MESSAGES[0], Urges.messageAt(-5))
    }

    @Test
    fun hayActividadesParaDistraerse() {
        assertTrue(Urges.DISTRACTIONS.size >= 6)
        assertTrue(Urges.DISTRACTIONS.all { it.title.isNotBlank() && it.hint.isNotBlank() })
    }

    @Test
    fun laRespiracionEs4Sostener4Espirar6() {
        assertEquals(BreathState(BreathPhase.INHALE, 0.5f, 4), Breathing.at(0))
        assertEquals(BreathPhase.INHALE, Breathing.at(3_999).phase)
        assertEquals(BreathState(BreathPhase.HOLD, 1f, 4), Breathing.at(4_000))
        assertEquals(BreathPhase.EXHALE, Breathing.at(8_000).phase)
        assertEquals(6, Breathing.at(8_000).secondsLeft)
        // Al acabar el ciclo vuelve a empezar.
        assertEquals(Breathing.at(0), Breathing.at(Breathing.CYCLE_MS))
    }

    @Test
    fun elCirculoCreceAlInspirarYSeEncogeAlEspirar() {
        assertTrue(Breathing.at(2_000).scale in 0.7f..0.8f)
        assertEquals(1f, Breathing.at(6_000).scale)
        assertTrue(Breathing.at(11_000).scale in 0.7f..0.8f)
        assertTrue(Breathing.at(13_999).scale < 0.51f)
    }

    private val today = LocalDate.of(2026, 10, 28)

    @Test
    fun seLimpianLosTextos() {
        assertEquals("a b", Journal.cleanActivity("  a   b \n"))
        assertEquals(Journal.MAX_ACTIVITY_CHARS, Journal.cleanActivity("x".repeat(500)).length)
        assertEquals(Journal.MAX_ENTRY_CHARS, Journal.cleanEntry("y".repeat(5_000)).length)
    }

    @Test
    fun seAdmitenTresActividadesPorDiaDesdeHoy() {
        assertTrue(Journal.canPlan(today, today, 2, "Pasear"))
        assertFalse(Journal.canPlan(today, today, 3, "Pasear"))
        assertTrue(Journal.canPlan(today.plusDays(1), today, 0, "Pasear"))
        assertFalse(Journal.canPlan(today.minusDays(1), today, 0, "Pasear"))
        assertFalse(Journal.canPlan(today, today, 0, "   "))
    }

    @Test
    fun seMarcaComoHechaElMismoDia() {
        assertTrue(Journal.canComplete(today, today))
        assertFalse(Journal.canComplete(today.plusDays(1), today))
        assertFalse(Journal.canComplete(today.minusDays(1), today))
    }
}
