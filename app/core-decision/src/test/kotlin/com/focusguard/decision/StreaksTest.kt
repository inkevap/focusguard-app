package com.focusguard.decision

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreaksTest {

    private val today = LocalDate.of(2026, 10, 28)
    private val hour = 60 * 60_000L

    private fun good(day: LocalDate, ms: Long = 2 * hour) = DayRecord(day, protectedMs = ms)

    /** Racha de [days] días buenos que acaba en [end]. */
    private fun run(end: LocalDate, days: Int, ms: Long = 2 * hour) =
        (0 until days).map { good(end.minusDays(it.toLong()), ms) }.associateBy { it.day }

    private fun state(records: Map<LocalDate, DayRecord>, config: StreakConfig = StreakConfig()) = Streaks.state(records, today, config)

    @Test
    fun unDiaSeLograConUnaHoraDeBloqueo() {
        assertNull(Streaks.lossReason(good(today, hour)))
        assertEquals(LossReason.TOO_LITTLE, Streaks.lossReason(good(today, hour - 1)))
        assertEquals(LossReason.TOO_LITTLE, Streaks.lossReason(null))
    }

    @Test
    fun unaPausaLargaOMuchasPausasPierdenElDia() {
        assertNull(Streaks.lossReason(DayRecord(today, 2 * hour, pauses = 3, longestPauseMinutes = 15)))
        assertEquals(LossReason.LONG_PAUSE, Streaks.lossReason(DayRecord(today, 2 * hour, pauses = 1, longestPauseMinutes = 30)))
        assertEquals(LossReason.MANY_PAUSES, Streaks.lossReason(DayRecord(today, 2 * hour, pauses = 4, longestPauseMinutes = 5)))
    }

    @Test
    fun laRachaCuentaLosDiasSeguidosHastaHoy() {
        val s = state(run(today, 5))
        assertEquals(5, s.current)
        assertEquals(5, s.best)
        assertEquals(DayOutcome.Achieved, s.today)
    }

    @Test
    fun hoySinLlegarALaHoraNoRompeLaRacha() {
        val records = run(today.minusDays(1), 4) + (today to good(today, 10 * 60_000L))
        val s = state(records)
        assertEquals(4, s.current)
        assertEquals(DayOutcome.InProgress, s.today)
        assertEquals(10 * 60_000L, s.todayProtectedMs)
    }

    @Test
    fun unaPausaLargaHoyPierdeElDiaEnElActo() {
        val records = run(today.minusDays(1), 4) + (today to DayRecord(today, 2 * hour, pauses = 1, longestPauseMinutes = 60))
        val s = state(records)
        assertEquals(0, s.current)
        assertEquals(DayOutcome.Lost(LossReason.LONG_PAUSE), s.today)
        assertEquals(LostDay(today, LossReason.LONG_PAUSE), s.lastLoss)
    }

    @Test
    fun unDiaPerdidoRompeLaRachaPeroNoLaMejor() {
        // 6 días buenos, dos perdidos (hace 3 y 2 días) y 2 buenos desde entonces.
        val records = run(today.minusDays(4), 6) + run(today, 2)
        val s = state(records)
        assertEquals(2, s.current)
        assertEquals(6, s.best)
        assertEquals(LostDay(today.minusDays(2), LossReason.TOO_LITTLE), s.lastLoss)
    }

    @Test
    fun unDiaSinDatosCuentaComoPerdido() {
        val s = state(run(today, 2) + run(today.minusDays(3), 2))
        assertEquals(2, s.current)
        assertEquals(DayOutcome.Lost(LossReason.TOO_LITTLE), Streaks.outcome(today.minusDays(2), today, run(today, 2) + run(today.minusDays(3), 2), StreakConfig()))
    }

    @Test
    fun reiniciarEmpiezaDeCeroPeroConservaLaMejor() {
        val records = run(today, 10)
        val s = state(records, StreakConfig(startDay = today.minusDays(2)))
        assertEquals(3, s.current)
        assertEquals(10, s.best)
        assertEquals(DayOutcome.NotCounted, Streaks.outcome(today.minusDays(3), today, records, StreakConfig(startDay = today.minusDays(2))))
    }

    @Test
    fun sinDatosNoHayRacha() {
        val s = state(emptyMap())
        assertEquals(0, s.current)
        assertEquals(0, s.best)
        assertNull(s.lastLoss)
        assertEquals(DayOutcome.NotCounted, s.today)
    }

    @Test
    fun unDiaRestauradoCuentaComoLogrado() {
        val lost = today.minusDays(1)
        val records = run(today.minusDays(2), 5) + run(today, 1)
        assertEquals(1, state(records).current)
        val s = state(records, StreakConfig(forgiven = setOf(lost)))
        assertEquals(DayOutcome.Forgiven, Streaks.outcome(lost, today, records, StreakConfig(forgiven = setOf(lost))))
        assertEquals(7, s.current)
    }

    @Test
    fun soloSePuedeRestaurarUnDiaRecienteConUnaRachaDetras() {
        val lost = today.minusDays(1)
        val records = run(today.minusDays(2), 5) + run(today, 1)
        assertEquals(Restorable(lost, 5), Streaks.restorable(records, today, StreakConfig()))

        // Rachas cortas no merecen restauración.
        assertNull(Streaks.restorable(run(today.minusDays(2), 2) + run(today, 1), today, StreakConfig()))
        // Pasados dos días ya no.
        assertNull(Streaks.restorable(run(today.minusDays(5), 5) + run(today, 1), today, StreakConfig()))
    }

    @Test
    fun noSeRestauraDosVecesEnCatorceDias() {
        val records = run(today.minusDays(2), 5) + run(today, 1)
        val config = StreakConfig(forgiven = setOf(today.minusDays(10)))
        assertNull(Streaks.restorable(records, today, config))
        val oldConfig = StreakConfig(forgiven = setOf(today.minusDays(30)))
        assertEquals(today.minusDays(1), Streaks.restorable(records, today, oldConfig)?.day)
    }

    @Test
    fun losHitosSonLosDeLaEspecificacion() {
        assertEquals(listOf(1, 3, 7, 14, 30, 50, 100), Streaks.MILESTONES)
        assertEquals(3, Streaks.nextMilestone(1))
        assertEquals(7, Streaks.nextMilestone(3))
        assertNull(Streaks.nextMilestone(100))
        assertTrue(14 in Streaks.MILESTONES)
    }

    @Test
    fun elDiaLogicoRespetaElInicioDelDia() {
        val at2am = LocalDateTime.of(2026, 10, 28, 2, 0)
        assertEquals(LocalDate.of(2026, 10, 28), Streaks.dayOf(at2am, 0))
        assertEquals(LocalDate.of(2026, 10, 27), Streaks.dayOf(at2am, 4 * 60))
    }

    @Test
    fun avisaDelRiesgoAlCaerLaTardeSiAunQuedaTiempo() {
        val records = run(today.minusDays(1), 4) + (today to good(today, 40 * 60_000L))
        val s = state(records)
        val evening = LocalDateTime.of(2026, 10, 28, 20, 30)
        val notices = Streaks.notices(s, today, evening)
        assertEquals(1, notices.size)
        assertEquals(NoticeKind.STREAK_AT_RISK, notices[0].kind)
        assertEquals(20, notices[0].minutes)
        assertEquals(4, notices[0].count)
        assertEquals("streakrisk:$today", notices[0].key)
        // Por la mañana, no; y sin tiempo para llegar a la hora, tampoco.
        assertTrue(Streaks.notices(s, today, LocalDateTime.of(2026, 10, 28, 9, 0)).isEmpty())
        val late = state(run(today.minusDays(1), 4) + (today to good(today, 1_000L)))
        assertTrue(Streaks.notices(late, today, LocalDateTime.of(2026, 10, 28, 23, 30)).isEmpty())
    }

    @Test
    fun avisaAlLlegarAUnHito() {
        val s = state(run(today, 7))
        val notices = Streaks.notices(s, today, LocalDateTime.of(2026, 10, 28, 10, 0))
        assertEquals(listOf(NoticeKind.STREAK_MILESTONE), notices.map { it.kind })
        assertEquals(7, notices[0].count)
        assertEquals("streakmilestone:7:$today", notices[0].key)
        assertTrue(Streaks.notices(state(run(today, 8)), today, LocalDateTime.of(2026, 10, 28, 10, 0)).isEmpty())
    }

    @Test
    fun elAcumuladorSumaSoloMientrasProtege() {
        val acc = ProtectedTimeAccumulator(maxGapMs = 5_000)
        acc.tick(0, true, today)
        acc.tick(1_000, true, today)
        acc.tick(2_000, false, today)
        acc.tick(3_000, true, today)
        assertEquals(mapOf(today to 2_000L), acc.drain())
        assertTrue(acc.drain().isEmpty())
    }

    @Test
    fun unHuecoLargoSoloCuentaElTopeYElRelojHaciaAtrasNada() {
        val acc = ProtectedTimeAccumulator(maxGapMs = 5_000)
        acc.tick(0, true, today)
        acc.tick(600_000, true, today)
        acc.tick(500_000, true, today)
        assertEquals(mapOf(today to 5_000L), acc.drain())
    }

    @Test
    fun laPrimeraLecturaNoSuma() {
        val acc = ProtectedTimeAccumulator()
        acc.tick(10_000, true, today)
        assertTrue(acc.drain().isEmpty())
    }

    @Test
    fun losAvisosDeRachaEntranPorNoticesDueSoloSiHayEstado() {
        val s = state(run(today, 7))
        val local = LocalDateTime.of(2026, 10, 28, 10, 0)
        val zone = java.time.ZoneOffset.UTC
        val now = local.toInstant(zone)
        val due = Notices.due(emptyList(), emptyMap(), now, local, NoticeSettings(), zone = zone, streak = s)
        assertEquals(listOf(NoticeKind.STREAK_MILESTONE), due.map { it.kind })
        assertTrue(Notices.due(emptyList(), emptyMap(), now, local, NoticeSettings(), zone = zone).isEmpty())
    }

    @Test
    fun elCalendarioUsaLaConfiguracionResuelta() {
        val records = run(today, 3)
        val resolved = Streaks.resolve(records, StreakConfig())
        assertEquals(today.minusDays(2), resolved.startDay)
        assertEquals(3, Streaks.current(records, today, StreakConfig()))
        assertEquals(DayOutcome.NotCounted, Streaks.outcome(today.minusDays(3), today, records, resolved))
    }

    @Test
    fun elAvisoDeRiesgoUsaLaHoraDelDiaLogico() {
        val records = run(today.minusDays(1), 4) + (today to good(today, 40 * 60_000L))
        val s = state(records)
        // Con el día empezando a las 22:00, a las 18:30 del reloj ya van 20 h y media del día.
        val notices = Streaks.notices(s, today, LocalDateTime.of(2026, 10, 28, 18, 30), dayStartMinute = 22 * 60)
        assertEquals(listOf(NoticeKind.STREAK_AT_RISK), notices.map { it.kind })
        // Recién empezado el día (22:30 del reloj), todavía no.
        assertTrue(Streaks.notices(s, today, LocalDateTime.of(2026, 10, 28, 22, 30), dayStartMinute = 22 * 60).isEmpty())
    }

    @Test
    fun elAcumuladorDevuelveLoQueNoSePudoGuardar() {
        val acc = ProtectedTimeAccumulator()
        acc.tick(0, true, today)
        acc.tick(1_000, true, today)
        val drained = acc.drain()
        acc.putBack(drained)
        acc.tick(2_000, true, today)
        assertEquals(mapOf(today to 2_000L), acc.drain())
    }
}
