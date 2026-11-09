package com.focusguard.decision

import com.focusguard.model.ScheduleInterval
import com.focusguard.model.ScheduleInterval.Companion.MINUTES_PER_DAY
import com.focusguard.model.ScheduleInterval.Companion.MINUTES_PER_WEEK
import java.time.LocalDateTime

object ScheduleEvaluator {

    /** Sin intervalos cuenta como siempre dentro. */
    fun isWithin(intervals: List<ScheduleInterval>, now: LocalDateTime): Boolean {
        if (intervals.isEmpty()) return true
        val minuteOfWeek = minuteOfWeek(now)
        return intervals.any { contains(it, minuteOfWeek) }
    }

    /** Minutos desde el lunes a las 00:00. */
    fun minuteOfWeek(now: LocalDateTime): Int = (now.dayOfWeek.value - 1) * MINUTES_PER_DAY + now.hour * 60 + now.minute

    /** Minutos que faltan para que empiece [interval]; 0 si empieza ahora. */
    fun minutesUntilStart(interval: ScheduleInterval, now: LocalDateTime): Int =
        Math.floorMod(interval.startOfWeek - minuteOfWeek(now), MINUTES_PER_WEEK)

    /** Minutos desde que terminó el último horario, o null si ahora hay uno en curso o no hay intervalos. */
    fun minutesSinceEnd(intervals: List<ScheduleInterval>, now: LocalDateTime): Int? {
        if (intervals.isEmpty() || isWithin(intervals, now)) return null
        val minuteOfWeek = minuteOfWeek(now)
        return intervals.minOf { Math.floorMod(minuteOfWeek - (it.startOfWeek + it.lengthMinutes), MINUTES_PER_WEEK) }
    }

    /** Minutos que quedan del horario en curso (el que termina más tarde si hay varios); null si ahora no hay ninguno. */
    fun minutesUntilEnd(intervals: List<ScheduleInterval>, now: LocalDateTime): Int? {
        val minuteOfWeek = minuteOfWeek(now)
        return intervals.filter { contains(it, minuteOfWeek) }
            .maxOfOrNull { it.lengthMinutes - Math.floorMod(minuteOfWeek - it.startOfWeek, MINUTES_PER_WEEK) }
    }

    private fun contains(interval: ScheduleInterval, minuteOfWeek: Int): Boolean =
        Math.floorMod(minuteOfWeek - interval.startOfWeek, MINUTES_PER_WEEK) < interval.lengthMinutes
}
