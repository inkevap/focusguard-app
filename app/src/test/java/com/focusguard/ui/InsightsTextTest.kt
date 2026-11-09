package com.focusguard.ui

import com.focusguard.decision.Change
import com.focusguard.decision.Trend
import com.focusguard.ui.stats.InsightsText
import kotlin.test.Test
import kotlin.test.assertEquals

class InsightsTextTest {

    @Test
    fun diceElCambioConSuSigno() {
        assertEquals("12 % menos que ayer", InsightsText.change(Change(-12), "ayer"))
        assertEquals("5 % más que ayer", InsightsText.change(Change(5), "ayer"))
        assertEquals("Igual que ayer", InsightsText.change(Change(0), "ayer"))
        assertEquals("Sin datos de ayer", InsightsText.change(Change(null), "ayer"))
    }

    @Test
    fun cadaTendenciaTieneSuFrase() {
        assertEquals(4, Trend.entries.map(InsightsText::trend).toSet().size)
    }

    @Test
    fun pasaLosMinutosAHorasYMinutos() {
        assertEquals("1 h 30 min", InsightsText.minutes(90 * 60_000L))
        assertEquals("45 min", InsightsText.minutes(45 * 60_000L))
    }
}
