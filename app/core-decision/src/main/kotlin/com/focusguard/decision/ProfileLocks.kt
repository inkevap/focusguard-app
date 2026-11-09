package com.focusguard.decision

import com.focusguard.model.ExitContext
import com.focusguard.model.ExitMethod
import com.focusguard.model.ExitRequest
import com.focusguard.model.ExitStatus
import com.focusguard.model.ProfileLock
import com.focusguard.model.StrictConfig
import java.time.Instant
import java.time.LocalDateTime

/** Reglas del candado de un perfil (ver [ProfileLock]). Los métodos de salida son los del modo estricto y se comprueban con la misma lógica. */
object ProfileLocks {

    private val PROFILE_ACTIONS = listOf("profile_off:", "profile_delete:", "profile_edit:", "profile_pause:", "session_end:")

    /** El perfil al que afecta una acción que lo apaga, pausa, borra o edita; null si la acción no es de un perfil. */
    fun targetOf(actionKey: String): Long? =
        PROFILE_ACTIONS.firstOrNull { actionKey.startsWith(it) }?.let { actionKey.substringAfter(':').toLongOrNull() }

    /** Motivo por el que [methods] no sirven como salida de un candado, o null si valen. */
    fun invalidReason(methods: List<ExitMethod>): String? = when {
        methods.isEmpty() -> "Elige al menos un método para poder quitar el candado."
        ExitMethod.FollowSchedule in methods -> "«Seguir horarios» solo vale en el modo estricto."
        else -> null
    }

    /** Qué falta para quitar el candado de [lock] con la solicitud [request]. */
    fun evaluateExit(lock: ProfileLock, request: ExitRequest, context: ExitContext, localNow: LocalDateTime, now: Instant): ExitStatus =
        StrictMode.evaluateExit(StrictConfig(enabled = true, exitMethods = effectiveMethods(lock, request, context)), request, context, localNow, now)

    /** Sin compañero y sin su aprobación ya dada, «compañero» se cambia por una espera de 72 h: así el candado nunca queda imposible de quitar. */
    private fun effectiveMethods(lock: ProfileLock, request: ExitRequest, context: ExitContext): List<ExitMethod> =
        if (context.partnerLinked || request.partnerApproved) lock.exitMethods
        else lock.exitMethods.map { if (it is ExitMethod.Partner) ExitMethod.Timer(PARTNER_GONE_WAIT_MINUTES) else it }

    const val PARTNER_GONE_WAIT_MINUTES = 72 * 60
}
