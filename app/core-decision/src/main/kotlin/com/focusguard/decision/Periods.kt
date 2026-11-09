package com.focusguard.decision

import com.focusguard.model.LimitPeriod
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Límites de los periodos de uso. El día lógico empieza a la hora configurada (E17),
 * por ejemplo a las 04:00: a las 02:00 todavía es "ayer". No tiene en cuenta cambios de hora de verano.
 */
object Periods {

    fun start(period: LimitPeriod, now: LocalDateTime, dayStartMinute: Int = 0): LocalDateTime {
        val logicalDate = now.minusMinutes(dayStartMinute.toLong()).toLocalDate()
        return when (period) {
            LimitPeriod.HOURLY -> now.withMinute(0).withSecond(0).withNano(0)
            LimitPeriod.DAILY -> logicalDate.atStartOfDay().plusMinutes(dayStartMinute.toLong())
            LimitPeriod.WEEKLY ->
                logicalDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay().plusMinutes(dayStartMinute.toLong())
        }
    }

    /** Como [start], en milisegundos desde 1970, que es como se guarda en el uso acumulado. */
    fun startMs(period: LimitPeriod, now: LocalDateTime, dayStartMinute: Int = 0, zone: ZoneId = ZoneId.systemDefault()): Long =
        start(period, now, dayStartMinute).atZone(zone).toInstant().toEpochMilli()

    /** Cuándo se reinicia el contador (para la cuenta atrás de la pantalla de bloqueo). */
    fun nextReset(period: LimitPeriod, now: LocalDateTime, dayStartMinute: Int = 0): LocalDateTime {
        val start = start(period, now, dayStartMinute)
        return when (period) {
            LimitPeriod.HOURLY -> start.plusHours(1)
            LimitPeriod.DAILY -> start.plusDays(1)
            LimitPeriod.WEEKLY -> start.plusWeeks(1)
        }
    }
}
