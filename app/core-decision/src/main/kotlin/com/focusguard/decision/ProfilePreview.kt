package com.focusguard.decision

import com.focusguard.model.DeviceContext
import com.focusguard.model.LimitKind
import com.focusguard.model.Profile
import com.focusguard.model.ProfileUsage
import com.focusguard.model.UsageLimit
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Dónde está un perfil respecto a su horario ahora mismo (E11). No tiene en cuenta Wi-Fi, cargador ni lugar. */
sealed interface ProfileStatus {
    data object Off : ProfileStatus
    data class Paused(val until: Instant) : ProfileStatus

    /** Sin horario: bloquea siempre que se cumplan sus condiciones. */
    data object Always : ProfileStatus
    data class ActiveNow(val minutesLeft: Int) : ProfileStatus
    data class StartsIn(val minutes: Int) : ProfileStatus

    /** Dentro de su horario (o sin horario), pero falta una condición de red, cargador o lugar. */
    data object WaitingConditions : ProfileStatus

    /** Fuera de su horario, pero activo porque se cumple otra condición (con «alguna»). */
    data object ActiveByConditions : ProfileStatus
}

/** Cuánto lleva gastado un límite en su periodo actual. [used] va en minutos o aperturas, según [kind]. */
data class LimitProgress(val kind: LimitKind, val used: Int, val allowed: Int, val exhausted: Boolean)

object ProfilePreview {

    fun status(profile: Profile, now: Instant, localNow: LocalDateTime, context: DeviceContext = DeviceContext()): ProfileStatus {
        if (!profile.enabled) return ProfileStatus.Off
        profile.pausedUntil?.takeIf { now.isBefore(it) }?.let { return ProfileStatus.Paused(it) }
        val bySchedule = scheduleStatus(profile, localNow)
        val met = Conditions.triggersMet(profile, localNow, context)
        return when {
            !met && bySchedule !is ProfileStatus.StartsIn -> ProfileStatus.WaitingConditions
            met && bySchedule is ProfileStatus.StartsIn -> ProfileStatus.ActiveByConditions
            else -> bySchedule
        }
    }

    private fun scheduleStatus(profile: Profile, localNow: LocalDateTime): ProfileStatus {
        if (profile.intervals.isEmpty()) return ProfileStatus.Always
        ScheduleEvaluator.minutesUntilEnd(profile.intervals, localNow)?.let { return ProfileStatus.ActiveNow(it) }
        return ProfileStatus.StartsIn(profile.intervals.minOf { ScheduleEvaluator.minutesUntilStart(it, localNow) })
    }

    fun limitProgress(
        limit: UsageLimit,
        usage: ProfileUsage?,
        nowMs: Long,
        localNow: LocalDateTime,
        dayStartMinute: Int = 0,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LimitProgress {
        val exhausted = LimitEvaluator.isExhausted(limit, usage, nowMs, localNow, dayStartMinute, zone)
        val used = if (usage == null || !LimitEvaluator.isCurrent(limit, usage, nowMs, localNow, dayStartMinute, zone)) 0 else when (limit.kind) {
            LimitKind.TIME -> usage.timeMs / 60_000L
            LimitKind.OPENS -> usage.opens.toLong()
            LimitKind.CONTINUOUS -> usage.continuousMs / 60_000L
        }
        return LimitProgress(limit.kind, used.toInt(), limit.amount, exhausted)
    }
}
