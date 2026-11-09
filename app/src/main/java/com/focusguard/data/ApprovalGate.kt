package com.focusguard.data

import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest

/** ¿Hay aprobaciones activadas? Si sí, crea la solicitud. Las pantallas pasan por [ChangeGuard], no por aquí. */
class ApprovalGate(private val repository: ApprovalRepository) {

    val required: Boolean get() = repository.mode.value != ApprovalMode.NONE

    fun hasPending(actionKey: String): Boolean = repository.pending.value.any { it.actionKey == actionKey }

    fun pending(actionKey: String): ApprovalRequest? = repository.pending.value.firstOrNull { it.actionKey == actionKey }

    fun moveAllToPartner() = repository.moveAllToPartner()

    fun request(actionKey: String, actionId: Int, payload: String? = null, mode: ApprovalMode? = null): ApprovalRequest =
        repository.create(actionKey, actionId, payload, mode)

    /** Solicitud que tiene que aprobar el compañero; si el modo elegido es una espera, esa espera va antes de mandarle el correo. */
    fun requestForPartner(actionKey: String, actionId: Int, payload: String? = null): ApprovalRequest =
        repository.create(actionKey, actionId, payload, ApprovalMode.PARTNER, holdByMode = true)
}
