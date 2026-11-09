package com.focusguard.decision

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrgeReportTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 28) // miércoles

    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun row(day: LocalDate, hour: Int, outcome: UrgeOutcome = UrgeOutcome.PASSED, trigger: UrgeTrigger? = null) = UrgeRow(at(day, hour), outcome, trigger)

    @Test fun sinImpulsosElInformeEstaVacio() {
        val report = UrgeReports.build(emptyList(), today, zone)
        assertEquals(0, report.total)
        assertNull(report.passRate)
        assertNull(report.peakHour)
        assertNull(report.topTrigger)
        assertNull(UrgeReports.weeklyLabel(report))
        assertEquals(UrgeReports.WEEKS_SHOWN, report.weeks.size)
    }

    @Test fun cuentaComoAcabaronLosImpulsos() {
        val rows = listOf(
            row(today, 22), row(today, 22, UrgeOutcome.GAVE_IN), row(today.minusDays(1), 9, UrgeOutcome.ABANDONED), row(today.minusDays(2), 23),
        )
        val report = UrgeReports.build(rows, today, zone)
        assertEquals(4, report.total)
        assertEquals(2, report.passed)
        assertEquals(1, report.gaveIn)
        assertEquals(1, report.abandoned)
        assertEquals(0.5, report.passRate)
    }

    @Test fun losImpulsosEnCursoYLosDeFueraDelPeriodoNoCuentan() {
        val old = UrgeReports.sinceDay(today).minusDays(1)
        val rows = listOf(row(today, 10, UrgeOutcome.ACTIVE), row(old, 10), row(today.plusDays(1), 10), row(today, 11))
        assertEquals(1, UrgeReports.build(rows, today, zone).total)
    }

    @Test fun laHoraMasFrecuenteSoloSeDiceConSuficientesImpulsos() {
        val few = UrgeReports.build(listOf(row(today, 22), row(today, 22)), today, zone)
        assertNull(few.peakHour)
        assertNull(few.peakWeekday)
        val rows = listOf(row(today, 22), row(today.minusDays(1), 22), row(today.minusDays(2), 3), row(today.minusDays(3), 22))
        val report = UrgeReports.build(rows, today, zone)
        assertEquals(22, report.peakHour)
        assertEquals(3, report.byHour[22])
        assertEquals(1, report.byHour[3])
    }

    @Test fun enUnEmpateGanaLaHoraMasTemprana() {
        val rows = listOf(row(today, 20), row(today, 8), row(today, 20), row(today, 8), row(today, 14), row(today, 14))
        assertEquals(8, UrgeReports.build(rows, today, zone).peakHour)
    }

    @Test fun elDiaDeLaSemanaVaDeLunesADomingo() {
        val rows = listOf(row(today, 1), row(today.minusDays(7), 1), row(today.minusDays(14), 1), row(today.minusDays(1), 1))
        val report = UrgeReports.build(rows, today, zone)
        assertEquals(3, report.byWeekday[2]) // miércoles
        assertEquals(1, report.byWeekday[1])
        assertEquals(DayOfWeek.WEDNESDAY, report.peakWeekday)
    }

    @Test fun losDesencadenantesVanDeMasAMenosYLosSinContestarNoCuentan() {
        val rows = listOf(
            row(today, 1, trigger = UrgeTrigger.STRESS), row(today, 2, trigger = UrgeTrigger.BOREDOM), row(today, 3, trigger = UrgeTrigger.BOREDOM),
            row(today, 4, trigger = null), row(today, 5, trigger = UrgeTrigger.STRESS), row(today, 6, trigger = UrgeTrigger.BOREDOM),
        )
        val report = UrgeReports.build(rows, today, zone)
        assertEquals(listOf(UrgeTrigger.BOREDOM to 3, UrgeTrigger.STRESS to 2), report.triggers)
        assertEquals(UrgeTrigger.BOREDOM, report.topTrigger)
    }

    @Test fun laEvolucionVaPorSemanasDeLunesADomingo() {
        val monday = LocalDate.of(2026, 10, 26)
        val rows = listOf(row(monday, 1), row(monday.plusDays(2), 1, UrgeOutcome.GAVE_IN), row(monday.minusDays(1), 1), row(monday.minusDays(8), 1))
        val weeks = UrgeReports.build(rows, today, zone).weeks
        assertEquals(monday, weeks.last().start)
        assertEquals(UrgeWeek(monday, total = 2, passed = 1), weeks.last())
        assertEquals(UrgeWeek(monday.minusWeeks(1), 1, 1), weeks[weeks.size - 2])
        assertEquals(UrgeWeek(monday.minusWeeks(2), 1, 1), weeks[weeks.size - 3])
    }

    @Test fun laSemanaSeCuentaConLaHoraLocalNoLaDelServidor() {
        val madrid = ZoneId.of("Europe/Madrid")
        // 23:30 UTC del domingo ya es lunes en Madrid.
        val sundayNight = UrgeRow(LocalDate.of(2026, 10, 25).atTime(23, 30).toInstant(ZoneOffset.UTC).toEpochMilli(), UrgeOutcome.PASSED, null)
        val report = UrgeReports.build(listOf(sundayNight), today, madrid)
        assertEquals(1, report.weeks.last().total)
        assertEquals(0, UrgeReports.build(listOf(sundayNight), today, zone).weeks.last().total)
    }

    @Test fun laEtiquetaParaElCompaneroSoloLlevaTotalesYElPatronSiLoHay() {
        val rows = listOf(row(today, 22, trigger = UrgeTrigger.STRESS), row(today, 22, UrgeOutcome.GAVE_IN, UrgeTrigger.STRESS), row(today, 22, trigger = UrgeTrigger.BOREDOM))
        assertEquals("3,2,1,22,STRESS", UrgeReports.weeklyLabel(UrgeReports.build(rows, today, zone)))
        val few = UrgeReports.build(listOf(row(today, 22, trigger = UrgeTrigger.STRESS)), today, zone)
        assertEquals("1,1,0,,", UrgeReports.weeklyLabel(few))
    }

    @Test fun laUltimaSemanaCompletaEsLaAnteriorALaDeHoy() {
        assertEquals(LocalDate.of(2026, 10, 19), UrgeReports.lastFullWeek(today))
        assertEquals(LocalDate.of(2026, 10, 19), UrgeReports.lastFullWeek(LocalDate.of(2026, 10, 26)))
        assertTrue(UrgeReports.sinceDay(today).dayOfWeek == DayOfWeek.MONDAY)
    }

    @Test
    fun `una semana solo se cuenta una vez`() {
        val monday = LocalDate.of(2026, 10, 19)
        assertTrue(UrgeReports.due(null, monday))
        assertTrue(UrgeReports.due(monday.minusWeeks(1).toEpochDay(), monday))
        assertFalse(UrgeReports.due(monday.toEpochDay(), monday))
        assertFalse(UrgeReports.due(monday.plusWeeks(1).toEpochDay(), monday))
    }
}
