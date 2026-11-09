package com.focusguard.ui.profiles

import com.focusguard.model.EndConditions
import com.focusguard.model.EndPolicy
import com.focusguard.model.GeoFence
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EndSummaryTest {

    private val now = Instant.parse("2026-10-29T10:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun block(policy: EndPolicy, ends: EndConditions = EndConditions()) =
        Profile(1, "Bloquear ya", kind = ProfileKind.QUICK_BLOCK, activeUntil = now.plusSeconds(7200), endPolicy = policy, endConditions = ends)

    @Test fun laSalidaDeSiempreNoDiceNada() {
        assertNull(endSummary(block(EndPolicy.ANYTIME), now, zone))
    }

    @Test fun soloElTiempoLoDiceClaro() {
        assertEquals("Solo termina al acabar el tiempo", endSummary(block(EndPolicy.TIME_ONLY), now, zone))
    }

    @Test fun lasCondicionesSeEnumeranConUnaO() {
        val one = block(EndPolicy.CONDITIONS, EndConditions(charging = true))
        assertEquals("Termina antes si conectas el cargador", endSummary(one, now, zone))
        val many = block(EndPolicy.CONDITIONS, EndConditions(place = GeoFence(14.0, -90.0), charging = true, wifiNames = setOf("Casa")))
        assertEquals("Termina antes si llegas a un lugar, conectas el cargador o te conectas a Casa", endSummary(many, now, zone))
    }

    @Test fun laEsperaDiceCuandoSePodraPedirTerminarlo() {
        val ends = EndConditions(charging = true, waitUntil = now.plusSeconds(1800))
        assertEquals("Termina antes si conectas el cargador; podrás pedir terminarlo a las 10:30", endSummary(block(EndPolicy.CONDITIONS, ends), now, zone))
        assertEquals(
            "Termina antes si conectas el cargador; ya puedes pedir terminarlo",
            endSummary(block(EndPolicy.CONDITIONS, ends), now.plusSeconds(1800), zone),
        )
        val onlyWait = block(EndPolicy.CONDITIONS, EndConditions(waitUntil = now.plusSeconds(1800)))
        assertEquals("Podrás pedir terminarlo a las 10:30", endSummary(onlyWait, now, zone))
    }
}
