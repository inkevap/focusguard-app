package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals

class DayUsageTest {

    private val start = 1_000_000L
    private val end = start + 86_400_000L
    private fun resumed(pkg: String, at: Long) = UsageEvent(UsageEventKind.APP_RESUMED, pkg, at)
    private fun paused(pkg: String, at: Long) = UsageEvent(UsageEventKind.APP_PAUSED, pkg, at)

    @Test
    fun sumaTiempoYAperturasPorApp() {
        val events = listOf(
            resumed("a", start + 1_000), paused("a", start + 61_000),
            resumed("b", start + 61_000), paused("b", start + 91_000),
            resumed("a", start + 200_000), paused("a", start + 260_000),
        )
        val day = DayUsageCalculator.of(events, start, end, nowMs = end)
        assertEquals(listOf(AppDayUsage("a", 120_000, 2), AppDayUsage("b", 30_000, 1)), day.apps)
    }

    @Test
    fun cambiarDeActividadDentroDeLaMismaAppNoEsUnaAperturaNueva() {
        val events = listOf(
            resumed("a", start + 1_000), paused("a", start + 10_000),
            resumed("a", start + 10_500), paused("a", start + 20_000),
        )
        val app = DayUsageCalculator.of(events, start, end, nowMs = end).apps.single()
        assertEquals(1, app.opens)
        assertEquals(18_500, app.timeMs)
    }

    @Test
    fun loQueSigueAbiertoCuentaHastaAhoraYLoDeAntesDeMedianocheSeRecorta() {
        val events = listOf(resumed("a", start - 60_000), paused("a", start + 30_000), resumed("b", start + 100_000))
        val day = DayUsageCalculator.of(events, start, end, nowMs = start + 160_000)
        assertEquals(listOf(AppDayUsage("b", 60_000, 1), AppDayUsage("a", 30_000, 0)), day.apps)
    }

    @Test
    fun apagarLaPantallaCierraLasAppsYSeCuentanLosDesbloqueos() {
        val events = listOf(
            resumed("a", start + 1_000), UsageEvent(UsageEventKind.SCREEN_OFF, null, start + 11_000),
            UsageEvent(UsageEventKind.UNLOCK, null, start + 50_000), UsageEvent(UsageEventKind.UNLOCK, null, start + 90_000),
        )
        val day = DayUsageCalculator.of(events, start, end, nowMs = end)
        assertEquals(10_000, day.apps.single().timeMs)
        assertEquals(2, day.unlocks)
    }

    @Test
    fun lasAppsIgnoradasNoCuentan() {
        val events = listOf(resumed("me", start + 1), paused("me", start + 9_000))
        assertEquals(emptyList(), DayUsageCalculator.of(events, start, end, nowMs = end, ignored = setOf("me")).apps)
    }
}

class HostOrNullTest {
    @Test
    fun soloLasDireccionesDevuelvenDominio() {
        assertEquals("reddit.com", UrlNormalizer.hostOrNull("https://www.reddit.com/r/a?utm_source=x"))
        assertEquals("a.b.com", UrlNormalizer.hostOrNull("a.b.com:8080/ruta"))
        assertEquals(null, UrlNormalizer.hostOrNull("hola mundo"))
        assertEquals(null, UrlNormalizer.hostOrNull("tiempo"))
        assertEquals(null, UrlNormalizer.hostOrNull(null))
    }
}

class WebUsageCounterTest {

    private val all = StatsSettings(webs = true, incognito = true)
    private fun web(host: String?) = WebObservation.Web(host)

    @Test
    fun cuentaVisitasAlCambiarDeWebYTiempoMientrasSeQueda() {
        val c = WebUsageCounter()
        c.record(web("a.com"), all, 1_000)
        c.record(web("a.com"), all, 2_000)
        c.record(web("a.com"), all, 3_000)
        c.record(web("b.com"), all, 4_000)
        c.record(web("a.com"), all, 5_000)
        val out = c.drain()
        assertEquals(WebUsage(visits = 2, millis = 2_000), out[WebKey("a.com", false)])
        assertEquals(WebUsage(visits = 1, millis = 0), out[WebKey("b.com", false)])
        assertEquals(emptyMap(), c.drain())
    }

    @Test
    fun enIncognitoNoSeGuardaElDominio() {
        val c = WebUsageCounter()
        c.record(WebObservation.Incognito, all, 1_000)
        c.record(WebObservation.Incognito, all, 2_000)
        val out = c.drain()
        assertEquals(setOf(WebKey("", true)), out.keys)
        assertEquals(WebUsage(visits = 1, millis = 1_000), out[WebKey("", true)])
    }

    @Test
    fun cadaInterruptorActivaSoloSuEstadistica() {
        val c = WebUsageCounter()
        c.record(web("a.com"), StatsSettings(incognito = true), 1_000)
        c.record(WebObservation.Incognito, StatsSettings(webs = true), 2_000)
        assertEquals(emptyMap(), c.drain())
        c.record(WebObservation.Incognito, StatsSettings(incognito = true), 3_000)
        c.record(web("a.com"), StatsSettings(webs = true), 4_000)
        assertEquals(setOf(WebKey("", true), WebKey("a.com", false)), c.drain().keys)
    }

    @Test
    fun perderElNavegadorTerminaLaVisitaYUnHuecoLargoNoSeCuentaDeGolpe() {
        val c = WebUsageCounter()
        c.record(web("a.com"), all, 1_000)
        c.record(WebObservation.None, all, 2_000)
        c.record(web("a.com"), all, 3_000)
        c.record(web("a.com"), all, 3_000 + 3_600_000)
        c.record(web(null), all, 3_000 + 3_601_000)
        assertEquals(WebUsage(visits = 2, millis = 5_000), c.drain()[WebKey("a.com", false)])
    }
}
