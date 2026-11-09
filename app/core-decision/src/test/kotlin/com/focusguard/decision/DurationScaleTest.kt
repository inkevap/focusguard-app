package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DurationScaleTest {

    @Test fun lasMarcasSonFinasAlPrincipioYGruesasDespues() {
        val stops = DurationScale.stops(1440)
        assertEquals(5, stops.first())
        assertEquals(1440, stops.last())
        assertTrue(25 in stops && 120 in stops && 135 in stops && 360 in stops && 390 in stops && 60 in stops)
        assertTrue(130 !in stops && 370 !in stops)
        assertEquals(stops.sorted(), stops)
        assertEquals(stops.distinct(), stops)
    }

    @Test fun elMaximoSiempreEstaAunqueNoCoincidaConUnaMarca() {
        val stops = DurationScale.stops(100)
        assertEquals(100, stops.last())
        assertTrue(stops.all { it <= 100 })
        assertEquals(listOf(5, 10, 15), DurationScale.stops(15))
        assertFailsWith<IllegalArgumentException> { DurationScale.stops(4) }
    }

    @Test fun laMarcaMasCercana() {
        val stops = DurationScale.stops(1440)
        assertEquals(30, stops[DurationScale.nearestIndex(stops, 30)])
        assertEquals(30, stops[DurationScale.nearestIndex(stops, 31)])
        assertEquals(5, stops[DurationScale.nearestIndex(stops, 1)])
        assertEquals(1440, stops[DurationScale.nearestIndex(stops, 5000)])
    }

    @Test fun masYMenosNoSalenDelRango() {
        assertEquals(35, DurationScale.step(30, 1, 5, 1, 1440))
        assertEquals(25, DurationScale.step(30, -1, 5, 1, 1440))
        assertEquals(1, DurationScale.step(3, -1, 5, 1, 1440))
        assertEquals(0, DurationScale.step(3, -1, 5, 0, 1440))
        assertEquals(1440, DurationScale.step(1438, 1, 5, 1, 1440))
        assertEquals(1, DurationScale.step(null, -1, 5, 1, 1440))
        assertEquals(6, DurationScale.step(null, 1, 5, 1, 1440))
    }
}
