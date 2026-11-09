package com.focusguard.decision

import com.focusguard.model.ExitContext
import com.focusguard.model.ExitMethod
import com.focusguard.model.ExitRequest
import com.focusguard.model.ExitRequirement
import com.focusguard.model.ExitStatus
import com.focusguard.model.StrictAction
import com.focusguard.model.StrictConfig
import com.focusguard.model.StrictProtection
import com.focusguard.model.StrictState
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime

object StrictMode {

    val PAUSE_MINUTES = listOf(5, 10, 15, 30)

    fun state(config: StrictConfig, localNow: LocalDateTime, now: Instant): StrictState = when {
        !config.enabled -> StrictState.INACTIVE
        config.pausedUntil?.let { now.isBefore(it) } == true -> StrictState.PAUSED
        !ScheduleEvaluator.isWithin(config.intervals, localNow) -> StrictState.WAITING
        else -> StrictState.ACTIVE
    }

    fun isActive(config: StrictConfig, localNow: LocalDateTime, now: Instant): Boolean =
        state(config, localNow, now) == StrictState.ACTIVE

    fun isProtected(config: StrictConfig, protection: StrictProtection, localNow: LocalDateTime, now: Instant): Boolean =
        isActive(config, localNow, now) && protection in config.protections

    /** La configuración solo se puede cambiar cuando el modo estricto no está activo. */
    fun canEditConfig(config: StrictConfig, localNow: LocalDateTime, now: Instant): Boolean =
        state(config, localNow, now) != StrictState.ACTIVE

    /**
     * ¿Cambiar de [old] a [new] relaja el modo estricto? Solo cuenta si ya estaba activado.
     * Relajar fuera del horario o en una pausa tampoco es libre: se pide con la solicitud de salida.
     */
    fun isWeakening(old: StrictConfig, new: StrictConfig): Boolean {
        if (!old.enabled) return false
        if (!new.enabled) return true
        if ((old.protections - new.protections).isNotEmpty()) return true
        if (old.exitMethods.any { method -> new.exitMethods.none { candidate -> atLeastAsStrict(candidate, method) } }) return true
        // Sin intervalos está activo siempre: el primero lo limita; quitar uno deja horas libres.
        if (old.intervals.isEmpty() && new.intervals.isNotEmpty()) return true
        return (old.intervals.toSet() - new.intervals.toSet()).isNotEmpty()
    }

    private fun atLeastAsStrict(candidate: ExitMethod, required: ExitMethod): Boolean = when {
        required is ExitMethod.Timer && candidate is ExitMethod.Timer -> candidate.minutes >= required.minutes
        required is ExitMethod.Cooldown && candidate is ExitMethod.Cooldown -> candidate.minutes >= required.minutes
        else -> candidate == required
    }

    /**
     * Comprueba, método por método, qué falta para conceder [request]. Si no hay métodos
     * configurados no falta nada (la pantalla de configuración exige al menos uno).
     */
    fun evaluateExit(
        config: StrictConfig,
        request: ExitRequest,
        context: ExitContext,
        localNow: LocalDateTime,
        now: Instant,
    ): ExitStatus {
        val pending = config.exitMethods.mapNotNull { method -> pendingFor(method, config, request, context, localNow, now) }
        return ExitStatus(satisfied = pending.isEmpty(), pending = pending)
    }

    private fun pendingFor(
        method: ExitMethod,
        config: StrictConfig,
        request: ExitRequest,
        context: ExitContext,
        localNow: LocalDateTime,
        now: Instant,
    ): ExitRequirement? = when (method) {
        is ExitMethod.Timer -> waitUntil(request.requestedAt.plus(Duration.ofMinutes(method.minutes.toLong())), now)
        is ExitMethod.Cooldown -> {
            val ready = request.requestedAt.plus(Duration.ofMinutes(method.minutes.toLong()))
            val expires = ready.plus(Duration.ofMinutes(ExitMethod.GRANT_WINDOW_MINUTES.toLong()))
            when {
                !now.isBefore(expires) -> ExitRequirement.RequestExpired
                else -> waitUntil(ready, now)
            }
        }
        ExitMethod.FollowSchedule ->
            if (state(config, localNow, now) == StrictState.ACTIVE) ExitRequirement.WaitForScheduleEnd else null
        ExitMethod.Charger -> if (context.charging) null else ExitRequirement.NeedCharger
        ExitMethod.Pin -> if (context.pinCorrect == true) null else ExitRequirement.NeedPin
        // Una aprobación ya dada vale aunque el compañero se desvincule después; sin aprobación y sin compañero no hay forma de conseguirla.
        ExitMethod.Partner -> when {
            request.partnerApproved -> null
            context.partnerLinked -> ExitRequirement.NeedPartner
            else -> ExitRequirement.NoPartner
        }
    }

    private fun waitUntil(at: Instant, now: Instant): ExitRequirement? =
        if (now.isBefore(at)) ExitRequirement.WaitUntil(at) else null

    /** Aplica la acción ya concedida: la pausa fija su fin y desactivar apaga el modo estricto. */
    fun apply(config: StrictConfig, action: StrictAction, now: Instant): StrictConfig = when (action) {
        is StrictAction.Pause -> config.copy(pausedUntil = now.plus(Duration.ofMinutes(action.minutes.toLong())))
        StrictAction.Disable -> config.copy(enabled = false, pausedUntil = null)
    }
}
