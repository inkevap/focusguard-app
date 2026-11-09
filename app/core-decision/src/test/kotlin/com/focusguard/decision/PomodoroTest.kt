package com.focusguard.decision

import com.focusguard.model.BlockMode
import com.focusguard.model.PomodoroPlan
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PomodoroTest {

    private val start = Instant.parse("2026-10-26T10:00:00Z")
    private val plan = PomodoroPlan(start, focusMinutes = 25, breakMinutes = 5, sessions = 4)
    private fun at(minutes: Long) = start.plusSeconds(minutes * 60)

    @Test
    fun duraLaSumaDeFocosYDescansosSinDescansoFinal() {
        assertEquals(at(4 * 25 + 3 * 5), Pomodoro.endsAt(plan)) // 115 min
    }

    @Test
    fun alternaFocoYDescanso() {
        assertEquals(PomodoroPhase.Focus(1, at(25)), Pomodoro.phaseAt(plan, at(0)))
        assertEquals(PomodoroPhase.Focus(1, at(25)), Pomodoro.phaseAt(plan, at(24)))
        assertEquals(PomodoroPhase.Break(1, at(30)), Pomodoro.phaseAt(plan, at(25)))
        assertEquals(PomodoroPhase.Focus(2, at(55)), Pomodoro.phaseAt(plan, at(30)))
        assertEquals(PomodoroPhase.Break(3, at(90)), Pomodoro.phaseAt(plan, at(86)))
    }

    @Test
    fun laUltimaSesionTerminaSinDescanso() {
        assertEquals(PomodoroPhase.Focus(4, at(115)), Pomodoro.phaseAt(plan, at(114)))
        assertEquals(PomodoroPhase.Finished, Pomodoro.phaseAt(plan, at(115)))
    }

    @Test
    fun antesDeEmpezarNoHayFase() {
        assertEquals(PomodoroPhase.NotStarted, Pomodoro.phaseAt(plan, at(-1)))
    }

    private val local = LocalDateTime.of(2026, 10, 26, 10, 0)

    @Test
    fun elPerfilPomodoroSoloEstaActivoEnElFoco() {
        val p = Profile(1, "p", kind = ProfileKind.POMODORO, pomodoro = plan)
        assertTrue(ProfileActivation.isActive(p, local, at(10)))
        assertFalse(ProfileActivation.isActive(p, local, at(27))) // descanso
        assertTrue(ProfileActivation.isActive(p, local, at(31)))
        assertFalse(ProfileActivation.isActive(p, local, at(200)))
    }

    @Test
    fun quickBlockSoloEstaActivoHastaSuHora() {
        val p = Profile(1, "q", kind = ProfileKind.QUICK_BLOCK, mode = BlockMode.ALLOW_ONLY, activeUntil = at(30))
        assertTrue(ProfileActivation.isActive(p, local, at(29)))
        assertFalse(ProfileActivation.isActive(p, local, at(30)))
    }

    @Test
    fun caducanLosPerfilesTemporales() {
        val quick = Profile(1, "q", kind = ProfileKind.QUICK_BLOCK, activeUntil = at(30))
        val pomo = Profile(2, "p", kind = ProfileKind.POMODORO, pomodoro = plan)
        assertFalse(ProfileActivation.isExpired(quick, at(10)))
        assertTrue(ProfileActivation.isExpired(quick, at(31)))
        assertFalse(ProfileActivation.isExpired(pomo, at(60)))
        assertTrue(ProfileActivation.isExpired(pomo, at(115)))
        assertFalse(ProfileActivation.isExpired(Profile(3, "n"), at(1000)))
    }

}
