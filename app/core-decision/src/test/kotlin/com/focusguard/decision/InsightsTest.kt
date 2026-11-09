package com.focusguard.decision

import com.focusguard.model.BlockEvent
import com.focusguard.model.BlockReason
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InsightsTest {

    private val min = 60_000L
    private fun span(from: Long, to: Long) = UseSpan(from * min, to * min)
    private fun block(atMin: Long) = BlockEvent(Instant.ofEpochMilli(atMin * min), 1, BlockReason.PROFILE_APP, "x")

    @Test
    fun losRatosCercanosSeUnen() {
        val merged = Insights.merge(listOf(span(0, 10), span(10, 20), span(21, 30), span(40, 50)))
        // 20→21 deja un minuto entero de hueco: ya son dos rachas distintas.
        assertEquals(listOf(span(0, 20), span(21, 30), span(40, 50)), merged)
        assertEquals(listOf(span(0, 30)), Insights.merge(listOf(span(0, 20), span(20, 30))))
    }

    @Test
    fun elUsoSeguidoYElDescansoMasLargos() {
        val spans = listOf(span(0, 30), span(60, 70), span(200, 215))
        val insight = Insights.day(spans, emptyList(), 0, 1_440 * min)
        assertEquals(30 * min, insight.longestUseMs)
        // Entre 70 y 200: la noche antes del primero y después del último no cuenta.
        assertEquals(130 * min, insight.longestRestMs)
    }

    @Test
    fun unDiaSinUsoNoTieneRachasNiDescanso() {
        assertEquals(DayInsight(0, 0, 0), Insights.day(emptyList(), emptyList(), 0, 1_440 * min))
        assertEquals(0, Insights.day(listOf(span(5, 9)), emptyList(), 0, 1_440 * min).longestRestMs)
    }

    @Test
    fun losBloqueosCercanosSonUnaSesion() {
        val blocks = listOf(block(10), block(12), block(19), block(40), block(200))
        assertEquals(3, Insights.blockSessions(blocks, 0, 1_440 * min))
        // Fuera del día no cuentan.
        assertEquals(1, Insights.blockSessions(blocks, 100 * min, 1_440 * min))
        assertEquals(0, Insights.blockSessions(emptyList(), 0, 1_440 * min))
    }

    @Test
    fun elCambioEsUnPorcentajeRedondeado() {
        assertEquals(Change(-25), Insights.change(75, 100))
        assertEquals(Change(50), Insights.change(150, 100))
        assertEquals(Change(0), Insights.change(100, 100))
        assertNull(Insights.change(100, 0).percent)
    }

    @Test
    fun laTendenciaCompara10PorCientoConLaMedia() {
        val history = listOf(100L, 100L, 100L)
        assertEquals(Trend.BETTER, Insights.trend(80, history))
        assertEquals(Trend.WORSE, Insights.trend(120, history))
        assertEquals(Trend.SAME, Insights.trend(95, history))
        assertEquals(Trend.SAME, Insights.trend(105, history))
    }

    @Test
    fun sinHistorialSuficienteNoHayTendencia() {
        assertEquals(Trend.NO_DATA, Insights.trend(50, listOf(100L, 100L)))
        assertEquals(Trend.NO_DATA, Insights.trend(50, listOf(0L, 0L, 0L)))
    }

    private val today = LocalDate.of(2026, 10, 28)

    private fun figures(offset: Int, usageMin: Long, unlocks: Int = 0, blocks: Int = 0) =
        DayFigures(today.minusDays(offset.toLong()), usageMin * min, unlocks, blocks)

    @Test
    fun elInformeSemanalCompara7DiasTerminadosCon7() {
        val days = (1..7).map { figures(it, 100, unlocks = 10, blocks = 2) } + (8..14).map { figures(it, 200, unlocks = 20, blocks = 5) }
        val report = Insights.weekly(days, today)
        assertEquals(WeekTotals(700 * min, 70, 14, 7), report.thisWeek)
        assertEquals(WeekTotals(1_400 * min, 140, 35, 7), report.lastWeek)
        assertEquals(Change(-50), report.usageChange)
    }

    @Test
    fun hoyNoEntraEnElInformeNiPuedeSerElMejorDia() {
        val days = listOf(figures(0, 5), figures(1, 100), figures(2, 120))
        val report = Insights.weekly(days, today)
        assertEquals(2, report.thisWeek.days)
        assertEquals(today.minusDays(1), report.bestDay?.day)
    }

    @Test
    fun sinDatosDeLaSemanaAnteriorNoHayCambio() {
        val days = (1..7).map { figures(it, 100) } + (8..9).map { figures(it, 100) }
        val report = Insights.weekly(days, today)
        assertEquals(2, report.lastWeek.days)
        assertNull(report.usageChange.percent)
        // Con tres días ya hay con qué comparar.
        assertEquals(Change(0), Insights.weekly((1..10).map { figures(it, 100) }, today).usageChange)
    }

    @Test
    fun losDiasSinUsoNiDesbloqueosNoCuentanComoDatos() {
        val days = listOf(figures(1, 0), figures(2, 50), figures(3, 0, unlocks = 4))
        val report = Insights.weekly(days, today)
        assertEquals(2, report.thisWeek.days)
        // El día con 0 min y solo desbloqueos cuenta como dato, pero no como «el de menos uso».
        assertEquals(today.minusDays(2), report.bestDay?.day)
        assertEquals(today.minusDays(2), report.worstDay?.day)
        assertNull(Insights.weekly(emptyList(), today).bestDay)
    }

    private val zone = java.time.ZoneOffset.UTC

    private fun at(day: LocalDate, hour: Int, minute: Int = 0) = day.atTime(hour, minute).toInstant(zone).toEpochMilli()

    /** Uso de [from] a [to] horas de [day] en una app. */
    private fun usage(day: LocalDate, fromHour: Int, toHour: Int) = listOf(
        UsageEvent(UsageEventKind.APP_RESUMED, "a", at(day, fromHour)),
        UsageEvent(UsageEventKind.APP_PAUSED, "a", at(day, toHour)),
    )

    @Test
    fun elResumenUsaLosEventosDeCadaDiaYLosBloqueos() {
        val now = today.atTime(10, 0).toInstant(zone)
        // Cada uno de los 5 días anteriores: de 8 a 9 (1 h). Hoy: de 8 a 8:30.
        val events = (1..5).flatMap { usage(today.minusDays(it.toLong()), 8, 9) } +
            listOf(UsageEvent(UsageEventKind.APP_RESUMED, "a", at(today, 8)), UsageEvent(UsageEventKind.APP_PAUSED, "a", at(today, 8, 30)))
        val data = Insights.overview(events.sortedBy { it.atMs }, mapOf(today.minusDays(1) to 3), emptyMap(), now, zone, emptySet(), unlocksSupported = true)
        assertEquals(30 * min, data.todayMs)
        assertEquals(Trend.BETTER, data.trend)
        assertEquals(Change(-50), data.dayChange)
        assertEquals(5, data.computed.size)
        assertEquals(3, data.computed.first { it.day == today.minusDays(1) }.blocks)
        assertEquals(5, data.report.thisWeek.days)
    }

    @Test
    fun lasCifrasGuardadasCompletanLosDiasQueYaNoTienenEventos() {
        val now = today.atTime(10, 0).toInstant(zone)
        val stored = (8..14).associate { it to figures(it, 200) }.mapKeys { today.minusDays(it.key.toLong()) }.mapValues { (_, v) -> v }
        val events = (1..7).flatMap { usage(today.minusDays(it.toLong()), 8, 9) }
        val data = Insights.overview(events.sortedBy { it.atMs }, emptyMap(), stored, now, zone, emptySet(), unlocksSupported = false)
        assertEquals(7, data.report.thisWeek.days)
        assertEquals(7, data.report.lastWeek.days)
        assertEquals(Change(-70), data.report.usageChange)
    }

    @Test
    fun sinEventosNoHayNadaQueGuardar() {
        val data = Insights.overview(emptyList(), emptyMap(), emptyMap(), today.atTime(10, 0).toInstant(zone), zone, emptySet(), unlocksSupported = true)
        assertEquals(emptyList(), data.computed)
        assertEquals(Trend.NO_DATA, data.trend)
        assertNull(data.dayChange.percent)
    }

    @Test
    fun elCorteDeLaMismaHoraRespetaElCambioDeHora() {
        // El 25 de octubre de 2026 en Madrid el día dura 25 horas: a las 10:00 del reloj siguen siendo 10 h sobre la medianoche local.
        val madrid = java.time.ZoneId.of("Europe/Madrid")
        val day = LocalDate.of(2026, 10, 25)
        val now = LocalDate.of(2026, 10, 26).atTime(10, 0).atZone(madrid).toInstant()
        val sameTime = day.atTime(10, 0).atZone(madrid).toInstant().toEpochMilli()
        val events = listOf(
            UsageEvent(UsageEventKind.APP_RESUMED, "a", day.atTime(9, 0).atZone(madrid).toInstant().toEpochMilli()),
            UsageEvent(UsageEventKind.APP_PAUSED, "a", sameTime + 30 * min),
        )
        val data = Insights.overview(events, emptyMap(), emptyMap(), now, madrid, emptySet(), unlocksSupported = true)
        assertEquals(90 * min, data.computed.first { it.day == day }.usageMs)
    }

    @Test
    fun unTotalParcialNoPisaElCompletoGuardado() {
        val day = today.minusDays(7)
        val stored = mapOf(day to figures(7, 200))
        val partial = listOf(figures(7, 40))
        assertEquals(200 * min, Insights.mergeFigures(stored, partial).single().usageMs)
        // Y uno mayor sí sustituye al guardado.
        assertEquals(300 * min, Insights.mergeFigures(stored, listOf(figures(7, 300))).single().usageMs)
        // Un día que solo está calculado se añade.
        assertEquals(2, Insights.mergeFigures(stored, listOf(figures(3, 50))).size)
    }

    @Test
    fun unDesbloqueoJustoALaMedianocheCuentaEnUnSoloDia() {
        val midnight = at(today, 0)
        val events = listOf(UsageEvent(UsageEventKind.UNLOCK, null, midnight))
        val previous = DayUsageCalculator.of(events, at(today.minusDays(1), 0), midnight, midnight)
        val current = DayUsageCalculator.of(events, midnight, at(today.plusDays(1), 0), midnight + min)
        assertEquals(0, previous.unlocks)
        assertEquals(1, current.unlocks)
    }

    @Test
    fun conUnSoloDiaConUsoNoHayMejorYPeorDiaDistintos() {
        val report = Insights.weekly(listOf(figures(2, 50)), today)
        assertEquals(report.bestDay, report.worstDay)
    }
}
