package com.focusguard.decision

import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.ScheduleInterval.Companion.MINUTES_PER_DAY
import com.focusguard.model.ScheduleInterval.Companion.MINUTES_PER_WEEK
import java.time.DayOfWeek

/** Dos perfiles del mismo modo cuyos horarios coinciden. Las horas son del día [day]; [endMinute] ya es la hora del reloj. */
data class ScheduleConflict(
    val otherId: Long,
    val otherTitle: String,
    val day: DayOfWeek,
    val startMinute: Int,
    val endMinute: Int,
)

/** Detección de horarios en conflicto (E12). Solo avisa: los bloqueos de ambos perfiles se suman. */
object ScheduleConflicts {

    /** Un conflicto por cada otro perfil, el primero de la semana. Un perfil sin horario no choca con nadie (aquí no es «siempre activo»). */
    fun of(profile: Profile, others: List<Profile>): List<ScheduleConflict> {
        val mine = profile.intervals.flatMap(::segments)
        return others.mapNotNull { other ->
            if (other.id == profile.id || other.mode != profile.mode) return@mapNotNull null
            val theirs = other.intervals.flatMap(::segments)
            mine.flatMap { a -> theirs.mapNotNull { b -> overlap(a, b) } }
                .minByOrNull { it.first }
                ?.let { (start, end) -> conflict(other, start, end) }
        }
    }

    private fun conflict(other: Profile, start: Int, end: Int): ScheduleConflict {
        val startOfDay = start % MINUTES_PER_DAY
        return ScheduleConflict(other.id, other.title, DayOfWeek.of(start / MINUTES_PER_DAY + 1), startOfDay, (startOfDay + end - start) % MINUTES_PER_DAY)
    }

    /** Tramos dentro de la semana (de 0 a 10080 minutos); uno que cruza el domingo se parte en dos. */
    private fun segments(interval: ScheduleInterval): List<Pair<Int, Int>> {
        val start = interval.startOfWeek
        val end = start + interval.lengthMinutes
        return if (end <= MINUTES_PER_WEEK) listOf(start to end) else listOf(start to MINUTES_PER_WEEK, 0 to end - MINUTES_PER_WEEK)
    }

    private fun overlap(a: Pair<Int, Int>, b: Pair<Int, Int>): Pair<Int, Int>? {
        val start = maxOf(a.first, b.first)
        val end = minOf(a.second, b.second)
        return if (start < end) start to end else null
    }
}
