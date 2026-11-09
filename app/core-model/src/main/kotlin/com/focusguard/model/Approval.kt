package com.focusguard.model

import java.time.Instant

/** Cómo se aprueba debilitar una protección cuando no hay compañero real. */
enum class ApprovalMode {
    /** Sin fricción: debilitar es inmediato (comportamiento por defecto). */
    NONE,
    DELAY_24H,
    DELAY_72H,
    LONG_PHRASE,
    STEPS,

    /** Lo aprueba el compañero de responsabilidad por correo (necesita cuenta y un compañero que haya aceptado). */
    PARTNER,
}

/** Qué sabe el teléfono de la solicitud que mandó al compañero a través del servidor. */
enum class RemoteStatus {
    /** Aún no se envió (sin conexión, o recién creada). */
    NOT_SENT,
    WAITING,
    APPROVED,
    REJECTED,

    /** Caducó (48 h) o se canceló sin respuesta. */
    EXPIRED,
}

/**
 * Solicitud pendiente de debilitar algo. [actionKey] identifica qué se quiere hacer
 * (por ejemplo `adult_off` o `profile_delete:5`) y [actionId] es el código compartido con el
 * servidor (TABLAS_Y_ALGORITMO sección 7). [payload] guarda lo que se aplicará al aprobar.
 */
data class ApprovalRequest(
    val id: Long,
    val actionKey: String,
    val actionId: Int,
    val mode: ApprovalMode,
    val createdAt: Instant,
    val phraseIndex: Int = 0,
    val stepsNeeded: Int = 250,
    val payload: String? = null,
    /** Valor del contador de pasos al empezar (modo STEPS). */
    val stepsBaseline: Long? = null,
    /** Identificador de la solicitud en el servidor (modo PARTNER), una vez enviada. */
    val remoteId: String? = null,
    val remote: RemoteStatus = RemoteStatus.NOT_SENT,
    /** Modo PARTNER: no se manda al compañero hasta esta hora (la espera elegida en las aprobaciones). Null = se manda ya. */
    val sendAfter: Instant? = null,
)

/** Lo que el usuario lleva hecho en la solicitud. */
data class ApprovalProgress(val typed: String = "", val steps: Int = 0)

sealed interface ApprovalStatus {
    data object Ready : ApprovalStatus

    data class WaitingUntil(val at: Instant) : ApprovalStatus

    data object NeedPhrase : ApprovalStatus

    data class Walking(val done: Int, val needed: Int) : ApprovalStatus

    /** Esperando al compañero; `sent` es falso si la solicitud aún no salió del teléfono. */
    data class WaitingPartner(val sent: Boolean) : ApprovalStatus

    /** Aún no se le ha mandado al compañero: primero hay que esperar hasta [at] (la espera elegida en las aprobaciones). */
    data class HoldingBeforePartner(val at: Instant) : ApprovalStatus

    data object PartnerRejected : ApprovalStatus

    /** Caducó sin respuesta: hay que pedirlo otra vez. */
    data object PartnerExpired : ApprovalStatus
}
