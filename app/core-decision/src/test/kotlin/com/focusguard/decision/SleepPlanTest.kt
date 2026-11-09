package com.focusguard.decision

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SleepPlanTest {

    private val utc = ZoneOffset.UTC
    private fun at(text: String) = Instant.parse(text)

    @Test fun siLaHoraAunNoHaLlegadoHoySeDespiertaHoy() {
        assertEquals(at("2026-10-29T07:00:00Z"), SleepPlan.wakeAt(at("2026-10-29T05:30:00Z"), 7 * 60, utc))
    }

    @Test fun siLaHoraYaPasoSeDespiertaMañana() {
        assertEquals(at("2026-10-30T07:00:00Z"), SleepPlan.wakeAt(at("2026-10-29T22:15:00Z"), 7 * 60, utc))
    }

    @Test fun laHoraExactaCuentaComoPasadaYNuncaDaUnBloqueoVacio() {
        assertEquals(at("2026-10-30T07:00:00Z"), SleepPlan.wakeAt(at("2026-10-29T07:00:00Z"), 7 * 60, utc))
    }

    @Test fun losMinutosQueFaltanSeRedondeanHaciaArriba() {
        assertEquals(8 * 60 + 12, SleepPlan.minutesUntil(at("2026-10-28T22:48:00Z"), 7 * 60, utc))
        assertEquals(1, SleepPlan.minutesUntil(at("2026-10-29T06:59:01Z"), 7 * 60, utc))
    }

    @Test fun usaElRelojDelTelefonoYNoElUtc() {
        val guatemala = ZoneId.of("America/Guatemala") // UTC-6, sin horario de verano
        // Son las 22:00 allí (04:00 UTC del día siguiente): despertar a las 7:00 locales es a las 13:00 UTC.
        assertEquals(at("2026-10-30T13:00:00Z"), SleepPlan.wakeAt(at("2026-10-30T04:00:00Z"), 7 * 60, guatemala))
    }

    @Test fun nuncaDuraMasDeUnDiaAunqueElRelojSeAtrase() {
        // El día que atrasan el reloj una hora hay 25 horas entre dos «las 7:00»: se corta en 24 h.
        val madrid = ZoneId.of("Europe/Madrid")
        val now = at("2026-10-25T05:30:00Z") // 07:30 locales (todavía con horario de verano, UTC+2)
        val wake = SleepPlan.wakeAt(now, 7 * 60, madrid)
        assertTrue(java.time.Duration.between(now, wake).toHours() <= 24)
    }

    @Test fun lasHorasOfrecidasSonValidasYLaDeSiempreEstaEntreEllas() {
        assertTrue(SleepPlan.DEFAULT_WAKE_MINUTE in SleepPlan.WAKE_PRESETS)
        assertTrue(SleepPlan.WAKE_PRESETS.all { it in 0 until 24 * 60 })
    }

    @Test fun unaHoraFueraDeRangoSeRechaza() {
        assertFailsWith<IllegalArgumentException> { SleepPlan.wakeAt(at("2026-10-29T05:30:00Z"), 24 * 60, utc) }
        assertFailsWith<IllegalArgumentException> { SleepPlan.wakeAt(at("2026-10-29T05:30:00Z"), -1, utc) }
    }
}
