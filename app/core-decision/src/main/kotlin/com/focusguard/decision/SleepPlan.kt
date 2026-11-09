package com.focusguard.decision

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * «Dormir»: un bloqueo que dura hasta una **hora del día** (la de despertar) en vez de un número de minutos. La hora se cuenta en el reloj del
 * teléfono, así que un cambio de hora de verano se respeta; como mucho dura un día.
 */
object SleepPlan {

    /** Horas de despertar que se ofrecen con un toque (minutos desde las 00:00). */
    val WAKE_PRESETS = listOf(6 * 60, 6 * 60 + 30, 7 * 60, 7 * 60 + 30, 8 * 60)

    const val DEFAULT_WAKE_MINUTE = 7 * 60

    private val MAX = Duration.ofHours(24)

    /** La próxima vez que el reloj marque [wakeMinute] después de [now]; si ya pasó hoy, mañana. Nunca más de 24 h. */
    fun wakeAt(now: Instant, wakeMinute: Int, zone: ZoneId): Instant {
        require(wakeMinute in 0 until 24 * 60) { "hora de despertar fuera de rango: $wakeMinute" }
        val today = now.atZone(zone).toLocalDate()
        var wake = LocalDateTime.of(today, LocalTime.of(wakeMinute / 60, wakeMinute % 60))
        if (!wake.atZone(zone).toInstant().isAfter(now)) wake = wake.plusDays(1)
        return minOf(wake.atZone(zone).toInstant(), now.plus(MAX))
    }

    /** Cuántos minutos faltan hasta despertar, redondeando hacia arriba. */
    fun minutesUntil(now: Instant, wakeMinute: Int, zone: ZoneId): Int {
        val seconds = Duration.between(now, wakeAt(now, wakeMinute, zone)).seconds
        return ((seconds + 59) / 60).toInt()
    }
}
