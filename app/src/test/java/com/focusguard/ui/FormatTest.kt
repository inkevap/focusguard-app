package com.focusguard.ui

import java.time.DayOfWeek
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FormatTest {

    @Test
    fun laCuentaAtrasMuestraHorasSoloSiHaceFalta() {
        assertEquals("0:09", formatRemaining(Duration.ofSeconds(9)))
        assertEquals("5:03", formatRemaining(Duration.ofSeconds(303)))
        assertEquals("1:05:03", formatRemaining(Duration.ofSeconds(3903)))
        assertEquals("0:05:03", formatRemaining(Duration.ofSeconds(303), alwaysHours = true))
        assertEquals("0:00", formatRemaining(Duration.ofSeconds(-4))) // una espera ya cumplida no se ve negativa
    }

    @Test
    fun lasHorasSeEscribenYSeLeenDeIdaYVuelta() {
        assertEquals("09:05", formatMinutes(9 * 60 + 5))
        assertEquals(9 * 60 + 5, parseMinutes("09:05"))
        assertEquals(9 * 60 + 5, parseMinutes("  9:05 "))
        assertEquals(24 * 60, parseMinutes("24:00"))
    }

    @Test
    fun lasHorasInvalidasNoSeAceptan() {
        assertNull(parseMinutes("24:01"))
        assertNull(parseMinutes("12:60"))
        assertNull(parseMinutes("1205"))
        assertNull(parseMinutes("ab:cd"))
        assertNull(parseMinutes(""))
    }

    @Test
    fun lasDuracionesSeLeenEnClaro() {
        assertEquals("45 min", formatAmountOfTime(45))
        assertEquals("1 h", formatAmountOfTime(60))
        assertEquals("1 h 14 min", formatAmountOfTime(74))
        assertEquals("2 h", formatAmountOfTime(120))
    }

    @Test
    fun elPluralSeAjustaAlNumero() {
        assertEquals("1 bloqueo", plural(1, "bloqueo", "bloqueos"))
        assertEquals("0 bloqueos", plural(0, "bloqueo", "bloqueos"))
        assertEquals("7 bloqueos", plural(7, "bloqueo", "bloqueos"))
    }

    @Test
    fun losDiasTienenTresTamanos() {
        assertEquals("Mié", Days.short(DayOfWeek.WEDNESDAY))
        assertEquals("X", Days.letter(DayOfWeek.WEDNESDAY))
        assertEquals("miércoles", Days.word(DayOfWeek.WEDNESDAY))
        assertEquals("Dom", Days.short(7))
    }
}
