package com.focusguard.decision

import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.Profile
import com.focusguard.model.ProfileUsage
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfilePreviewTest {

    private val zone = ZoneOffset.UTC
    private val local = LocalDateTime.of(2026, 10, 28, 12, 0) // miércoles
    private val now = local.toInstant(zone)
    private val weekdays = (1..5).map { ScheduleInterval(DayOfWeek.of(it), 9 * 60, 17 * 60) }

    @Test
    fun estadoSegunApagadoPausaYHorario() {
        val p = Profile(1, "p", intervals = weekdays)
        assertEquals(ProfileStatus.Off, ProfilePreview.status(p.copy(enabled = false), now, local))
        val until = now.plusSeconds(600)
        assertEquals(ProfileStatus.Paused(until), ProfilePreview.status(p.copy(pausedUntil = until), now, local))
        assertEquals(ProfileStatus.Always, ProfilePreview.status(Profile(1, "p"), now, local))
        assertEquals(ProfileStatus.ActiveNow(5 * 60), ProfilePreview.status(p, now, local))
    }

    @Test
    fun lasCondicionesDeRedOCargadorCambianElEstado() {
        val ctx = com.focusguard.model.DeviceContext(wifiName = "Otra")
        val p = Profile(1, "p", intervals = weekdays, wifiNames = setOf("Casa"))
        assertEquals(ProfileStatus.WaitingConditions, ProfilePreview.status(p, now, local, ctx))
        assertEquals(ProfileStatus.ActiveNow(5 * 60), ProfilePreview.status(p, now, local, ctx.copy(wifiName = "Casa")))
        val night = LocalDateTime.of(2026, 10, 28, 20, 0)
        val any = p.copy(conditionMode = com.focusguard.model.ConditionMode.ANY)
        assertEquals(ProfileStatus.ActiveByConditions, ProfilePreview.status(any, night.toInstant(zone), night, ctx.copy(wifiName = "Casa")))
        assertEquals(ProfileStatus.StartsIn(13 * 60), ProfilePreview.status(any, night.toInstant(zone), night, ctx))
    }

    @Test
    fun faltaParaQueEmpieceElSiguienteHorario() {
        val p = Profile(1, "p", intervals = weekdays)
        val night = LocalDateTime.of(2026, 10, 28, 18, 0)
        assertEquals(ProfileStatus.StartsIn(15 * 60), ProfilePreview.status(p, night.toInstant(zone), night))
        val friday = LocalDateTime.of(2026, 10, 30, 18, 0)
        assertEquals(ProfileStatus.StartsIn(63 * 60), ProfilePreview.status(p, friday.toInstant(zone), friday))
    }

    @Test
    fun horarioQueCruzaLaMedianocheCuentaHastaElDiaSiguiente() {
        val night = listOf(ScheduleInterval(DayOfWeek.WEDNESDAY, 22 * 60, 7 * 60))
        val at = LocalDateTime.of(2026, 10, 29, 1, 0)
        assertEquals(6 * 60, ScheduleEvaluator.minutesUntilEnd(night, at))
        assertNull(ScheduleEvaluator.minutesUntilEnd(night, local))
    }

    @Test
    fun elUsoContinuoSoloValeSinDescanso() {
        val limit = UsageLimit(LimitKind.CONTINUOUS, 20)
        val nowMs = now.toEpochMilli()
        val usage = ProfileUsage(continuousMs = 25 * 60_000L, lastActiveMs = nowMs - UsageAccumulator.CONTINUOUS_GAP_MS + 1)
        assertEquals(true, LimitEvaluator.isCurrent(limit, usage, nowMs, local, zone = zone))
        assertEquals(false, LimitEvaluator.isCurrent(limit, usage.copy(lastActiveMs = nowMs - UsageAccumulator.CONTINUOUS_GAP_MS), nowMs, local, zone = zone))
        assertEquals(25, ProfilePreview.limitProgress(limit, usage, nowMs, local, zone = zone).used)
        assertEquals(0, ProfilePreview.limitProgress(limit, usage.copy(lastActiveMs = 0), nowMs, local, zone = zone).used)
    }

    @Test
    fun progresoDelLimiteSoloCuentaElPeriodoVigente() {
        val limit = UsageLimit(LimitKind.TIME, 30, LimitPeriod.DAILY)
        val start = Periods.startMs(LimitPeriod.DAILY, local, 0, zone)
        val nowMs = now.toEpochMilli()
        val usage = ProfileUsage(periodStartMs = start, timeMs = 12 * 60_000L)
        assertEquals(LimitProgress(LimitKind.TIME, 12, 30, exhausted = false), ProfilePreview.limitProgress(limit, usage, nowMs, local, zone = zone))
        assertEquals(LimitProgress(LimitKind.TIME, 0, 30, exhausted = false), ProfilePreview.limitProgress(limit, usage.copy(periodStartMs = start - 1), nowMs, local, zone = zone))
        assertEquals(LimitProgress(LimitKind.TIME, 0, 30, exhausted = false), ProfilePreview.limitProgress(limit, null, nowMs, local, zone = zone))
        assertEquals(true, ProfilePreview.limitProgress(limit, usage.copy(timeMs = 31 * 60_000L), nowMs, local, zone = zone).exhausted)
    }
}

class BlockMeTest {

    @Test
    fun creaUnTramoPorDiaYUnLimiteDeEmergenciaSiSePide() {
        val t = BlockMe.template(setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY), 22 * 60, 7 * 60, 10)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), t.intervals.map { it.day })
        assertEquals(UsageLimit(LimitKind.TIME, 10, LimitPeriod.DAILY), t.limit)
        assertNull(BlockMe.template(setOf(DayOfWeek.MONDAY), 0, 60, 0).limit)
    }
}
