package com.focusguard.data

import com.focusguard.decision.ChangeOutcome
import com.focusguard.decision.ChangeRule
import com.focusguard.decision.ProfileLocks
import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.StrictProtection
import java.time.Instant

sealed interface GuardResult {
    data object Allowed : GuardResult

    /** No se hace ahora; [message] explica por qué, para mostrarlo al usuario. [requested] indica que se creó una solicitud de aprobación. */
    data class Blocked(val message: String, val requested: Boolean = false) : GuardResult
}

/**
 * Único punto por el que pasa cualquier cambio que debilita una protección: si el modo estricto
 * lo impide, se rechaza; si hay aprobaciones activadas, se crea la solicitud; si no, se permite.
 */
class ChangeGuard(
    private val strict: StrictGuard,
    private val gate: ApprovalGate,
    private val settings: SettingsRepository,
    private val partner: PartnerRepository,
    private val profileLocks: ProfileLockRepository,
) {

    /** Modo parental (I7) con un compañero activo: todo cambio pasa por él. */
    val parentalActive: Boolean get() = settings.parentalMode.value && partner.isActive

    /** Con compañero, la protección «editar» del panel del compañero hace que los cambios pasen por él aunque el modo estricto esté apagado. */
    private val partnerEditProtected: Boolean
        get() = partner.isActive && StrictProtection.EDIT_LOCK in settings.partnerPanel.value.active(settings.parentalMode.value).protections

    val approvalRequired: Boolean get() = gate.required || parentalActive || partnerEditProtected

    /** Enciende el modo parental: las solicitudes que esperaban por otra vía pasan al compañero, o seguirían aplicándose sin él. */
    fun enableParental() {
        settings.setPartnerNotices(true)
        settings.setParentalMode(true)
        gate.moveAllToPartner()
    }

    fun isLocked(now: Instant? = null): Boolean = strict.isEditLocked(now)

    /** ¿Tiene ese perfil un candado puesto? Es independiente del modo estricto. */
    fun isProfileLocked(profileId: Long): Boolean = profileLocks.isLocked(profileId)

    /**
     * Pide poder desactivar el administrador (lo que permite desinstalar). Con el modo estricto no se puede. Con un compañero vinculado siempre lo
     * aprueba él por correo, y si en las aprobaciones se eligió una espera (24 h o 72 h) esa espera va antes de mandarle el correo. Sin compañero
     * se aplica el modo de aprobación de siempre.
     */
    fun requestUninstall(actionKey: String, actionId: Int): GuardResult {
        if (!partner.isActive) return check(actionKey, actionId)
        if (isLocked()) return GuardResult.Blocked(LOCKED)
        val request = gate.requestForPartner(actionKey, actionId)
        return GuardResult.Blocked(if (request.sendAfter != null) REQUESTED_PARTNER_AFTER_WAIT else REQUESTED_PARTNER, requested = true)
    }

    /** Hay un compañero vinculado y activo que puede aprobar. */
    val partnerLinked: Boolean get() = partner.isActive

    /**
     * Pide al compañero que apruebe salir del modo estricto. Es la única solicitud que se crea con el modo estricto activo (no pasa por
     * [check], que la rechazaría): el compañero solo la puede aprobar o rechazar, y lo aprobado no cambia nada hasta que el usuario cumpla
     * el resto de sus métodos de salida.
     */
    fun requestStrictExit(actionKey: String, actionId: Int): ApprovalRequest = gate.request(actionKey, actionId, mode = ApprovalMode.PARTNER)

    /** La solicitud pendiente de esa acción, si la hay. */
    fun pending(actionKey: String): ApprovalRequest? = gate.pending(actionKey)

    fun hasPending(actionKey: String) = gate.hasPending(actionKey)

    /**
     * Con [viaPartner] el cambio siempre lo tiene que aprobar el compañero, aunque el usuario no haya elegido aprobaciones:
     * es para lo que dejaría sin efecto al propio compañero (cerrar la sesión de la cuenta).
     */
    fun check(actionKey: String, actionId: Int, payload: String? = null, viaPartner: Boolean = false): GuardResult {
        ProfileLocks.targetOf(actionKey)?.takeIf(profileLocks::isLocked)?.let { return GuardResult.Blocked(PROFILE_LOCKED) }
        val byPartner = viaPartner || parentalActive || partnerEditProtected
        return when (ChangeRule.decide(locked = isLocked(), approvalRequired = approvalRequired || viaPartner)) {
            ChangeOutcome.LOCKED -> GuardResult.Blocked(LOCKED)
            ChangeOutcome.NEEDS_APPROVAL -> {
                gate.request(actionKey, actionId, payload, mode = if (byPartner) ApprovalMode.PARTNER else null)
                GuardResult.Blocked(if (byPartner) REQUESTED_PARTNER else REQUESTED, requested = true)
            }
            ChangeOutcome.ALLOWED -> GuardResult.Allowed
        }
    }

    /** Pide a su compañero activo un cambio que se aplicará cuando él lo apruebe: la solicitud queda creada y se devuelve el aviso para el usuario. */
    fun askPartner(actionKey: String, actionId: Int): GuardResult.Blocked? = check(actionKey, actionId, viaPartner = true) as? GuardResult.Blocked

    /** Para los cambios que se aplican ahora mismo si nada lo impide: el motivo por el que no se puede (o se pidió aprobación), o null si ya se puede. */
    fun blockedMessage(actionKey: String, actionId: Int, payload: String? = null): String? =
        (check(actionKey, actionId, payload) as? GuardResult.Blocked)?.message

    companion object {
        const val LOCKED = "El modo estricto está activo: no se puede cambiar ahora."
        const val PANIC = "El bloqueo de pánico dura hasta que termine."
        const val NO_EARLY_END = "Elegiste que este bloqueo solo termine como dijiste al empezarlo."
        const val REQUESTED = "Solicitud creada. Ábrela en Aprobaciones para completarla."
        const val REQUESTED_PARTNER = "Se lo hemos pedido a tu compañero. Verás su respuesta en Aprobaciones."
        const val REQUESTED_PARTNER_AFTER_WAIT = "Primero toca esperar el tiempo que elegiste en Aprobaciones; después se lo pediremos a tu compañero por correo."
        const val PROFILE_LOCKED = "Este perfil tiene un candado puesto: quítalo desde su pantalla para poder cambiarlo."
    }
}
