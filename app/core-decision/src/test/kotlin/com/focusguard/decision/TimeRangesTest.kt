package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeRangesTest {

    @Test fun unRangoDelMismoDiaEsUnTramo() {
        val segments = TimeRanges.segments(9 * 60, 17 * 60)
        assertEquals(1, segments.size)
        assertEquals(0.375f, segments[0].start)
        assertEquals(17f / 24, segments[0].endInclusive)
        assertFalse(TimeRanges.crossesMidnight(9 * 60, 17 * 60))
    }

    @Test fun elQueCruzaLaMedianocheSonDosTramos() {
        val segments = TimeRanges.segments(23 * 60, 7 * 60)
        assertEquals(2, segments.size)
        assertEquals(23f / 24, segments[0].start)
        assertEquals(1f, segments[0].endInclusive)
        assertEquals(0f, segments[1].start)
        assertEquals(7f / 24, segments[1].endInclusive)
        assertTrue(TimeRanges.crossesMidnight(23 * 60, 7 * 60))
    }

    @Test fun hastaLasCeroSoloCubreElPrimerTramo() {
        assertEquals(1, TimeRanges.segments(22 * 60, 0).size)
        assertEquals(1, TimeRanges.segments(22 * 60, 24 * 60).size)
    }

    @Test fun lasMismasHorasSonElDiaEntero() {
        assertEquals(24 * 60, TimeRanges.lengthMinutes(8 * 60, 8 * 60))
    }

    @Test fun lasDescripcionesDicenCuantoDuranYSiSiguenAlDiaSiguiente() {
        assertEquals("De 09:00 a 17:00 · 8 h", TimeRanges.describe(9 * 60, 17 * 60))
        assertEquals("De 23:00 a 07:00 · 8 h · sigue al día siguiente", TimeRanges.describe(23 * 60, 7 * 60))
        assertEquals("De 09:00 a 09:45 · 45 min", TimeRanges.describe(9 * 60, 9 * 60 + 45))
        assertEquals("De 09:00 a 10:30 · 1 h 30 min", TimeRanges.describe(9 * 60, 10 * 60 + 30))
    }
}
