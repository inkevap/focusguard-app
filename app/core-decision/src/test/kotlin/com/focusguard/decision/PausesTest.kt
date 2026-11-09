package com.focusguard.decision

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PausesTest {
    @Test
    fun soloSeAceptanLasDuracionesOfrecidas() {
        assertTrue(Pauses.isValid(15))
        assertFalse(Pauses.isValid(0))
        assertFalse(Pauses.isValid(10_000))
    }

    @Test
    fun laPausaTerminaTrasLosMinutosElegidos() {
        val now = Instant.parse("2026-10-27T12:00:00Z")
        assertEquals(Instant.parse("2026-10-27T12:30:00Z"), Pauses.until(now, 30))
    }
}
