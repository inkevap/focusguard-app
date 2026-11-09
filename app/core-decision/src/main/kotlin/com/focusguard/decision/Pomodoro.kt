package com.focusguard.decision

import com.focusguard.model.PomodoroPlan
import java.time.Duration
import java.time.Instant

sealed interface PomodoroPhase {
    data object NotStarted : PomodoroPhase

    /** [session] cuenta desde 1. */
    data class Focus(val session: Int, val endsAt: Instant) : PomodoroPhase

    data class Break(val session: Int, val endsAt: Instant) : PomodoroPhase

    data object Finished : PomodoroPhase
}

object Pomodoro {

    /** Lo que dura un Pomodoro entero: los focos y los descansos entre ellos (no hay descanso tras el último). */
    fun totalMinutes(focusMinutes: Int, breakMinutes: Int, sessions: Int): Int = sessions * focusMinutes + (sessions - 1).coerceAtLeast(0) * breakMinutes

    /** Los ritmos habituales: foco y descanso en minutos. */
    val PRESETS = listOf(25 to 5, 50 to 10, 90 to 20)

    fun endsAt(plan: PomodoroPlan): Instant {
        return plan.startedAt.plus(Duration.ofMinutes(totalMinutes(plan.focusMinutes, plan.breakMinutes, plan.sessions).toLong()))
    }

    fun phaseAt(plan: PomodoroPlan, now: Instant): PomodoroPhase {
        if (now.isBefore(plan.startedAt)) return PomodoroPhase.NotStarted
        if (!now.isBefore(endsAt(plan))) return PomodoroPhase.Finished

        val elapsedMinutes = Duration.between(plan.startedAt, now).toMillis() / 60_000.0
        val cycle = (plan.focusMinutes + plan.breakMinutes).toDouble()
        val index = (elapsedMinutes / cycle).toInt() // sesión en curso, desde 0
        val intoCycle = elapsedMinutes - index * cycle
        val cycleStart = plan.startedAt.plus(Duration.ofMinutes(index * (plan.focusMinutes + plan.breakMinutes).toLong()))

        return if (intoCycle < plan.focusMinutes) {
            PomodoroPhase.Focus(index + 1, cycleStart.plus(Duration.ofMinutes(plan.focusMinutes.toLong())))
        } else {
            PomodoroPhase.Break(index + 1, cycleStart.plus(Duration.ofMinutes(cycle.toLong())))
        }
    }
}
