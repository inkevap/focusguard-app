package com.focusguard.decision

import com.focusguard.model.LimitKind
import com.focusguard.model.ProfileUsage
import com.focusguard.model.UsageLimit
import java.time.LocalDateTime
import java.time.ZoneId

object LimitEvaluator {

    /**
     * ¿Está agotado el límite? Un uso guardado de un periodo anterior no cuenta,
     * salvo el uso continuo, que depende solo del último descanso.
     */
    fun isExhausted(
        limit: UsageLimit,
        usage: ProfileUsage?,
        nowMs: Long,
        now: LocalDateTime,
        dayStartMinute: Int = 0,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (usage == null || !isCurrent(limit, usage, nowMs, now, dayStartMinute, zone)) return false
        return when (limit.kind) {
            LimitKind.TIME -> usage.timeMs >= limit.amount * 60_000L
            LimitKind.OPENS -> usage.opens > limit.amount
            LimitKind.CONTINUOUS -> usage.continuousMs >= limit.amount * 60_000L
        }
    }

    /** ¿Pertenece [usage] al periodo vigente? El uso continuo no tiene periodo: vale mientras no haya habido un descanso. */
    fun isCurrent(
        limit: UsageLimit,
        usage: ProfileUsage,
        nowMs: Long,
        now: LocalDateTime,
        dayStartMinute: Int = 0,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean =
        if (limit.kind == LimitKind.CONTINUOUS) nowMs - usage.lastActiveMs < UsageAccumulator.CONTINUOUS_GAP_MS
        else usage.periodStartMs == Periods.startMs(limit.period, now, dayStartMinute, zone)
}
