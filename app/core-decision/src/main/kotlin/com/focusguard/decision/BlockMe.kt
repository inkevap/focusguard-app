package com.focusguard.decision

import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import java.time.DayOfWeek

/**
 * Horario «Block Me» (E18): un solo tramo repetido los días elegidos y, si se quiere, un tiempo de emergencia
 * al día. El tiempo de emergencia es un límite de uso diario: el perfil solo bloquea cuando se gasta, y solo cuenta
 * el uso que se hace dentro del horario.
 */
object BlockMe {

    val EMERGENCY_OPTIONS = listOf(0, 5, 10, 15, 30)

    fun template(days: Set<DayOfWeek>, startMinute: Int, endMinute: Int, emergencyMinutes: Int): ScheduleTemplate = ScheduleTemplate(
        id = "block_me",
        title = "Block Me",
        emoji = "🚫",
        description = "Un horario sencillo con tiempo de emergencia.",
        intervals = days.sorted().map { ScheduleInterval(it, startMinute, endMinute) },
        limit = emergencyMinutes.takeIf { it > 0 }?.let { UsageLimit(LimitKind.TIME, it, LimitPeriod.DAILY) },
    )
}
