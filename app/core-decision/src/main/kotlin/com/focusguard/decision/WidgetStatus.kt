package com.focusguard.decision

import com.focusguard.model.DeviceContext
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.model.ScheduleInterval
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Lo que enseñan los widgets de la pantalla de inicio (F4): el bloqueo sin salida en curso, cuántos perfiles están activos, el siguiente
 * horario y los días de la semana con algún horario. Se calcula solo con horarios y pausas: Wi-Fi, cargador y lugar no se consultan desde
 * un widget, así que esos perfiles se cuentan como activos si lo está su horario.
 */
data class WidgetStatus(
    /** Pánico o temporizador en curso. */
    val lock: Lock?,
    val activeTitles: List<String>,
    val nextStart: Upcoming?,
    /** Días con algún horario de un perfil encendido; un perfil sin horario cuenta todos los días. */
    val activeDays: Set<DayOfWeek>,
    /** Cuándo cambia algo que el widget enseña, para volver a pintarlo entonces. */
    val refreshAt: Instant?,
) {
    data class Lock(val title: String, val until: Instant)

    data class Upcoming(val title: String, val at: Instant)

    val activeCount: Int get() = activeTitles.size

    companion object {
        /** Wi-Fi, cargador y lugar no se consultan desde un widget: se dan por cumplidos y manda el horario. */
        private val DEVICE_CONDITIONS_MET = DeviceContext(charging = true, locationAvailable = false)

        fun of(profiles: List<Profile>, now: Instant, zone: ZoneId): WidgetStatus {
            val local = now.atZone(zone).toLocalDateTime()
            val thisMinute = local.truncatedTo(ChronoUnit.MINUTES)
            fun inMinutes(minutes: Int): Instant = thisMinute.plusMinutes(minutes.toLong()).atZone(zone).toInstant()
            val lock = ProfileActivation.activePanic(profiles, now)?.let { p -> p.activeUntil?.let { Lock(p.title, it) } }
            val regular = profiles.filter { it.enabled && it.kind != ProfileKind.PANIC && !ProfileActivation.isExpired(it, now) }
            // Un perfil con límite de uso solo bloquea al agotarlo y el widget no sabe cuánto se ha usado: no se cuenta como activo.
            val active = regular.filter { it.limit == null && ProfileActivation.isActive(it, local, now, DEVICE_CONDITIONS_MET) }
            val scheduled = regular.filter { it.kind == ProfileKind.NORMAL && it.intervals.isNotEmpty() }

            val nextStart = scheduled
                .filter { it.pausedUntil?.let(now::isBefore) != true && !ScheduleEvaluator.isWithin(it.intervals, local) }
                .mapNotNull { profile ->
                    val minutes = profile.intervals.minOf { ScheduleEvaluator.minutesUntilStart(it, local) }
                    Upcoming(profile.title, inMinutes(minutes))
                }
                .minByOrNull { it.at }

            val days = regular.filter { it.kind == ProfileKind.NORMAL }.flatMapTo(mutableSetOf()) { daysOf(it) }

            val endsOfActive = scheduled.filter { it in active }.mapNotNull { profile ->
                ScheduleEvaluator.minutesUntilEnd(profile.intervals, local)?.let(::inMinutes)
            }
            val others = regular.flatMap { listOfNotNull(it.activeUntil, it.pausedUntil, pomodoroChange(it, now)) }
            // Con un bloqueo sin salida en curso se repinta cada minuto: lo que queda se ve al minuto y, al acabar, no se queda un texto viejo.
            val refreshAt = (listOfNotNull(lock?.until, lock?.let { inMinutes(1) }, nextStart?.at) + endsOfActive + others).filter { it.isAfter(now) }.minOrNull()

            return WidgetStatus(lock, active.map { it.title }, nextStart, days, refreshAt)
        }

        /** Cuándo cambia de fase un Pomodoro (o empieza). */
        private fun pomodoroChange(profile: Profile, now: Instant): Instant? = profile.pomodoro?.let { plan ->
            when (val phase = Pomodoro.phaseAt(plan, now)) {
                is PomodoroPhase.Focus -> phase.endsAt
                is PomodoroPhase.Break -> phase.endsAt
                PomodoroPhase.NotStarted -> plan.startedAt
                PomodoroPhase.Finished -> null
            }
        }

        private fun daysOf(profile: Profile): Set<DayOfWeek> {
            if (profile.intervals.isEmpty()) return DayOfWeek.values().toSet()
            return profile.intervals.flatMapTo(mutableSetOf()) { interval ->
                val first = interval.startOfWeek / ScheduleInterval.MINUTES_PER_DAY
                val last = (interval.startOfWeek + interval.lengthMinutes - 1) / ScheduleInterval.MINUTES_PER_DAY
                (first..last).map { DayOfWeek.of(it % 7 + 1) }
            }
        }
    }
}
