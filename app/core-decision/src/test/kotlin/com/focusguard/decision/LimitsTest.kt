package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockReason
import com.focusguard.model.ForegroundState
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.Profile
import com.focusguard.model.ProfileUsage
import com.focusguard.model.UsageLimit
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LimitsTest {

    private val zone = ZoneOffset.UTC
    private val now = LocalDateTime.of(2026, 10, 28, 12, 0) // miércoles
    private val nowMs = now.toInstant(zone).toEpochMilli()
    private fun startMs(period: LimitPeriod, dayStart: Int = 0) =
        Periods.start(period, now, dayStart).toInstant(zone).toEpochMilli()

    @Test
    fun diaLogicoEmpiezaALaHoraConfigurada() {
        val madrugada = LocalDateTime.of(2026, 10, 28, 2, 0)
        assertEquals(LocalDateTime.of(2026, 10, 27, 4, 0), Periods.start(LimitPeriod.DAILY, madrugada, 4 * 60))
        assertEquals(LocalDateTime.of(2026, 10, 28, 4, 0), Periods.start(LimitPeriod.DAILY, now, 4 * 60))
    }

    @Test
    fun semanaEmpiezaElLunesYHoraEnLaHora() {
        assertEquals(LocalDateTime.of(2026, 10, 26, 0, 0), Periods.start(LimitPeriod.WEEKLY, now))
        assertEquals(LocalDateTime.of(2026, 11, 2, 0, 0), Periods.nextReset(LimitPeriod.WEEKLY, now))
        assertEquals(LocalDateTime.of(2026, 10, 28, 13, 0), Periods.nextReset(LimitPeriod.HOURLY, now))
    }

    @Test
    fun tiempoSeAgotaAlLlegarAlMaximo() {
        val limit = UsageLimit(LimitKind.TIME, 30)
        val usage = ProfileUsage(periodStartMs = startMs(LimitPeriod.DAILY), timeMs = 29 * 60_000L)
        assertFalse(LimitEvaluator.isExhausted(limit, usage, nowMs, now, zone = zone))
        assertTrue(LimitEvaluator.isExhausted(limit, usage.copy(timeMs = 30 * 60_000L), nowMs, now, zone = zone))
    }

    @Test
    fun aperturasSeAgotanAlSuperarElMaximo() {
        val limit = UsageLimit(LimitKind.OPENS, 3)
        val usage = ProfileUsage(periodStartMs = startMs(LimitPeriod.DAILY), opens = 3)
        assertFalse(LimitEvaluator.isExhausted(limit, usage, nowMs, now, zone = zone))
        assertTrue(LimitEvaluator.isExhausted(limit, usage.copy(opens = 4), nowMs, now, zone = zone))
    }

    @Test
    fun usoDeUnPeriodoAnteriorNoCuenta() {
        val limit = UsageLimit(LimitKind.TIME, 30)
        val ayer = ProfileUsage(periodStartMs = startMs(LimitPeriod.DAILY) - 86_400_000L, timeMs = 999 * 60_000L)
        assertFalse(LimitEvaluator.isExhausted(limit, ayer, nowMs, now, zone = zone))
        assertFalse(LimitEvaluator.isExhausted(limit, null, nowMs, now, zone = zone))
    }

    @Test
    fun usoContinuoSeLibraConUnDescanso() {
        val limit = UsageLimit(LimitKind.CONTINUOUS, 20)
        val usage = ProfileUsage(continuousMs = 20 * 60_000L, lastActiveMs = nowMs - 10_000L, inUse = true)
        assertTrue(LimitEvaluator.isExhausted(limit, usage, nowMs, now, zone = zone))
        val descansado = usage.copy(lastActiveMs = nowMs - UsageAccumulator.CONTINUOUS_GAP_MS)
        assertFalse(LimitEvaluator.isExhausted(limit, descansado, nowMs, now, zone = zone))
    }

    @Test
    fun acumuladorCuentaTiempoYAperturas() {
        val period = startMs(LimitPeriod.DAILY)
        var u = ProfileUsage()
        u = UsageAccumulator.tick(u, 1_000, inUse = true, periodStartMs = period)
        assertEquals(1, u.opens)
        u = UsageAccumulator.tick(u, 2_000, inUse = true, periodStartMs = period)
        u = UsageAccumulator.tick(u, 3_000, inUse = true, periodStartMs = period)
        assertEquals(2_000, u.timeMs)
        assertEquals(1, u.opens)
        // Sale 10 s y vuelve: menos de 30 s, no es una apertura nueva.
        u = UsageAccumulator.tick(u, 4_000, inUse = false, periodStartMs = period)
        u = UsageAccumulator.tick(u, 13_000, inUse = true, periodStartMs = period)
        assertEquals(1, u.opens)
        // Sale 40 s: apertura nueva.
        u = UsageAccumulator.tick(u, 14_000, inUse = false, periodStartMs = period)
        u = UsageAccumulator.tick(u, 60_000, inUse = true, periodStartMs = period)
        assertEquals(2, u.opens)
    }

    @Test
    fun acumuladorReiniciaAlCambiarDePeriodoYNoCuentaHuecosLargos() {
        val period = startMs(LimitPeriod.DAILY)
        var u = UsageAccumulator.tick(ProfileUsage(), 1_000, true, period)
        u = UsageAccumulator.tick(u, 2_000, true, period)
        u = UsageAccumulator.tick(u, 2_000 + 3_600_000L, true, period) // hueco de una hora
        assertEquals(1_000 + 5_000, u.timeMs)
        val siguiente = UsageAccumulator.tick(u, 3_700_000L, true, period + 86_400_000L)
        assertEquals(0, siguiente.opens) // seguía en uso: no es una apertura nueva
        assertEquals(5_000, siguiente.timeMs) // el contador de este periodo empieza en cero, más un paso topado
    }

    @Test
    fun perfilConLimiteSoloBloqueaCuandoSeAgota() {
        val decider = BlockDecider(neverBlock = emptySet(), zone = zone)
        val profile = Profile(1, "p", apps = setOf("com.x"), limit = UsageLimit(LimitKind.TIME, 10))
        val instant = Instant.ofEpochMilli(nowMs)
        val state = ForegroundState("com.x")
        val under = mapOf(1L to ProfileUsage(periodStartMs = startMs(LimitPeriod.DAILY), timeMs = 5 * 60_000L))
        val over = mapOf(1L to ProfileUsage(periodStartMs = startMs(LimitPeriod.DAILY), timeMs = 10 * 60_000L))
        assertEquals(BlockDecision.Allow, decider.decide(state, listOf(profile), instant, under))
        assertEquals(BlockDecision.Allow, decider.decide(state, listOf(profile), instant))
        val block = decider.decide(state, listOf(profile), instant, over) as BlockDecision.Block
        assertEquals(BlockDecision.Block(BlockReason.LIMIT_REACHED, 1, "com.x"), block.copy(availableAt = null))
        assertEquals(Periods.nextReset(LimitPeriod.DAILY, now).atZone(zone).toInstant(), block.availableAt)
    }
}

class UsageTargetsTest {

    private val never = setOf("com.launcher")

    @Test
    fun enModoBloquearCuentaAppsYWebsDelPerfil() {
        val p = Profile(1, "p", apps = setOf("com.x"), webs = listOf(com.focusguard.model.WebRule("reddit.com")))
        assertTrue(UsageTargets.isUsing(p, ForegroundState("com.x"), never))
        assertTrue(UsageTargets.isUsing(p, ForegroundState("com.chrome", "reddit.com/r/a", isBrowser = true), never))
        assertFalse(UsageTargets.isUsing(p, ForegroundState("com.y"), never))
    }

    @Test
    fun elUsoSoloCuentaMientrasElPerfilEstaActivo() {
        val inside = LocalDateTime.of(2026, 10, 28, 12, 0)
        val outside = LocalDateTime.of(2026, 10, 28, 20, 0)
        val schedule = (1..5).map { com.focusguard.model.ScheduleInterval(java.time.DayOfWeek.of(it), 9 * 60, 17 * 60) }
        val p = Profile(1, "p", apps = setOf("com.x"), intervals = schedule)
        val state = ForegroundState("com.x")
        fun counts(profile: Profile, at: LocalDateTime) = UsageTargets.counts(
            profile, state, never, CategoryTable.EMPTY, at, at.toInstant(java.time.ZoneOffset.UTC), { com.focusguard.model.DeviceContext() },
        )
        assertTrue(counts(p, inside))
        assertFalse(counts(p, outside))
        assertFalse(counts(p.copy(enabled = false), inside))
        assertFalse(counts(p.copy(pausedUntil = inside.toInstant(java.time.ZoneOffset.UTC).plusSeconds(60)), inside))
    }

    @Test
    fun enModoPermitirCuentaLoQueQuedaFueraDeLaListaYNuncaLasExcepciones() {
        val p = Profile(1, "p", mode = com.focusguard.model.BlockMode.ALLOW_ONLY, apps = setOf("com.notes"))
        assertFalse(UsageTargets.isUsing(p, ForegroundState("com.notes"), never))
        assertTrue(UsageTargets.isUsing(p, ForegroundState("com.other"), never))
        assertFalse(UsageTargets.isUsing(p, ForegroundState("com.launcher"), never))
    }
}
