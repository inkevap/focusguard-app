package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals

class ClockGuardTest {

    private val start = ClockAnchor(wallMs = 1_000_000_000L, elapsedMs = 5_000L, bootId = 1)

    @Test
    fun laPrimeraLecturaFijaLaReferenciaSinDesfase() {
        val anchor = ClockGuard.step(null, wallNow = 10_000, elapsedNow = 500, bootId = 7)
        assertEquals(ClockAnchor(10_000, 500, 7, 0), anchor)
        assertEquals(10_000, anchor.trustedMs)
    }

    @Test
    fun elPasoNormalDelTiempoNoCreaDesfase() {
        val next = ClockGuard.step(start, start.wallMs + 60_000, start.elapsedMs + 60_000, 1)
        assertEquals(0, next.offsetMs)
        assertEquals(start.wallMs + 60_000, next.trustedMs)
    }

    @Test
    fun unSaltoHaciaDelanteDeTresDiasNoSeAcredita() {
        val threeDays = 3 * 24 * 3_600_000L
        val next = ClockGuard.step(start, start.wallMs + 60_000 + threeDays, start.elapsedMs + 60_000, 1)
        // La hora de confianza solo avanzó el minuto que de verdad pasó.
        assertEquals(start.trustedMs + 60_000, next.trustedMs)
    }

    @Test
    fun unSaltoHaciaAtrasTampocoRompeLaHoraDeConfianza() {
        val next = ClockGuard.step(start, start.wallMs - 3_600_000L + 30_000, start.elapsedMs + 30_000, 1)
        assertEquals(start.trustedMs + 30_000, next.trustedMs)
    }

    @Test
    fun losSaltosSeAcumulanYSeguiranDescontados() {
        var anchor = start
        val hour = 3_600_000L
        repeat(3) {
            anchor = ClockGuard.step(anchor, anchor.wallMs + hour + 1_000, anchor.elapsedMs + 1_000, 1)
        }
        assertEquals(start.trustedMs + 3_000, anchor.trustedMs)
        // Y un paso normal posterior sigue contando desde la hora de confianza.
        val later = ClockGuard.step(anchor, anchor.wallMs + 10_000, anchor.elapsedMs + 10_000, 1)
        assertEquals(start.trustedMs + 13_000, later.trustedMs)
    }

    @Test
    fun lasDiferenciasPequenasSeToleran() {
        val next = ClockGuard.step(start, start.wallMs + 60_000 + 90_000, start.elapsedMs + 60_000, 1)
        assertEquals(0, next.offsetMs)
    }

    @Test
    fun conHoraAutomaticaSeFiaDelTelefonoYReiniciaElDesfase() {
        val manipulated = start.copy(offsetMs = 3 * 24 * 3_600_000L)
        // El usuario vuelve a activar la hora automática: la red devuelve la hora real y el desfase se borra.
        val next = ClockGuard.step(manipulated, wallNow = manipulated.wallMs + 5 * 60_000L, elapsedNow = manipulated.elapsedMs + 5_000, bootId = 1, autoTime = true)
        assertEquals(0, next.offsetMs)
        assertEquals(manipulated.wallMs + 5 * 60_000L, next.trustedMs)
    }

    @Test
    fun unaCorreccionDeRedGrandeConHoraAutomaticaSeAcepta() {
        val next = ClockGuard.step(start, start.wallMs + 600_000L, start.elapsedMs + 1_000, 1, autoTime = true)
        assertEquals(start.wallMs + 600_000L, next.trustedMs)
    }

    @Test
    fun elMonotonoQueRetrocedeEsUnReinicioAunqueElContadorDeArranquesNoCambie() {
        val longUptime = start.copy(elapsedMs = 90_000_000L)
        val next = ClockGuard.step(longUptime, wallNow = longUptime.wallMs + 60_000L, elapsedNow = 3_000L, bootId = 1)
        assertEquals(longUptime.trustedMs + 60_000L, next.trustedMs) // se acepta la hora del teléfono, sin salto raro
    }

    @Test
    fun trasUnReinicioSeAceptaLaHoraDelTelefonoSiNoRetrocede() {
        val next = ClockGuard.step(start, start.wallMs + 7_200_000L, elapsedNow = 3_000, bootId = 2)
        assertEquals(start.wallMs + 7_200_000L, next.trustedMs)
        assertEquals(2, next.bootId)
    }

    @Test
    fun trasUnReinicioLaHoraDeConfianzaNuncaRetrocede() {
        val next = ClockGuard.step(start, start.wallMs - 86_400_000L, elapsedNow = 3_000, bootId = 2)
        assertEquals(start.trustedMs, next.trustedMs)
        val later = ClockGuard.step(next, next.wallMs + 5_000, next.elapsedMs + 5_000, 2)
        assertEquals(start.trustedMs + 5_000, later.trustedMs)
    }
}
