package com.focusguard.decision

import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleEvaluatorTest {

    private val instant = Instant.parse("2026-01-01T00:00:00Z")

    // 2026-10-26 es lunes.
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 10, 25 + day, hour, minute)

    private fun active(profile: Profile, now: LocalDateTime, at: Instant = instant) =
        ProfileActivation.isActive(profile, now, at)

    @Test
    fun sinIntervalosEstaSiempreActivo() {
        assertTrue(active(Profile(1, "p"), at(3, 12)))
    }

    @Test
    fun deshabilitadoNuncaEstaActivo() {
        assertFalse(active(Profile(1, "p", enabled = false), at(1, 12)))
    }

    @Test
    fun intervaloNormalIncluyeElInicioYExcluyeElFin() {
        val p = Profile(1, "p", intervals = listOf(ScheduleInterval(DayOfWeek.MONDAY, 9 * 60, 17 * 60)))
        assertTrue(active(p, at(1, 9)))
        assertTrue(active(p, at(1, 16, 59)))
        assertFalse(active(p, at(1, 17)))
        assertFalse(active(p, at(1, 8, 59)))
        assertFalse(active(p, at(2, 12)))
    }

    @Test
    fun intervaloQueCruzaLaMedianocheSigueAlDiaSiguiente() {
        val p = Profile(1, "p", intervals = listOf(ScheduleInterval(DayOfWeek.FRIDAY, 22 * 60, 6 * 60)))
        assertTrue(active(p, at(5, 23)))
        assertTrue(active(p, at(6, 5, 59))) // sábado de madrugada
        assertFalse(active(p, at(6, 6)))
        assertFalse(active(p, at(5, 21, 59)))
    }

    @Test
    fun domingoPorLaNocheCruzaAlLunes() {
        val p = Profile(1, "p", intervals = listOf(ScheduleInterval(DayOfWeek.SUNDAY, 23 * 60, 2 * 60)))
        assertTrue(active(p, at(1, 1))) // lunes 01:00
        assertFalse(active(p, at(1, 2)))
    }

    @Test
    fun inicioIgualAFinDuraUnDiaEntero() {
        val p = Profile(1, "p", intervals = listOf(ScheduleInterval(DayOfWeek.MONDAY, 8 * 60, 8 * 60)))
        assertTrue(active(p, at(1, 8)))
        assertTrue(active(p, at(2, 7, 59)))
        assertFalse(active(p, at(2, 8)))
    }

    @Test
    fun pausaVigenteDesactivaYAlTerminarVuelve() {
        val p = Profile(1, "p", pausedUntil = Instant.parse("2026-01-01T00:10:00Z"))
        assertFalse(active(p, at(1, 12), Instant.parse("2026-01-01T00:09:59Z")))
        assertTrue(active(p, at(1, 12), Instant.parse("2026-01-01T00:10:00Z")))
    }
}
