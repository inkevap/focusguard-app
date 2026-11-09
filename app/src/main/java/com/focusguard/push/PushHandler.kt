package com.focusguard.push

import com.focusguard.data.ApprovalsReport

/**
 * Qué hace el teléfono al recibir un aviso del servidor. El aviso solo trae el tipo (y el id de la solicitud): ningún dato personal.
 * No se confía en su contenido; solo sirve para ir a preguntar al servidor ya, sin esperar a las 6 horas del envío periódico.
 * Como el usuario no está mirando la app, si algo se resolvió (una solicitud respondida, el compañero que acepta) se le avisa con una
 * notificación; un aviso que no cambia nada no molesta. Lo que el compañero aprobó se aplica solo (`ApprovalApplier`).
 */
class PushHandler(
    private val signedIn: () -> Boolean,
    /** Consulta las solicitudes al compañero y aplica las aprobadas. */
    private val syncApprovals: suspend () -> ApprovalsReport,
    /** Consulta al compañero. Devuelve si acaba de aceptar la invitación. */
    private val refreshPartner: suspend () -> Boolean,
    private val notifyResolved: (ApprovalsReport) -> Unit,
    private val notifyPartnerConfirmed: () -> Unit,
) {
    suspend fun handle(data: Map<String, String>) {
        // Un aviso de la cuenta anterior de este teléfono no es de nadie: sin sesión no se pregunta ni se avisa.
        if (!signedIn()) return
        when (data[TYPE]) {
            REQUEST_RESOLVED -> syncApprovals().let { if (it.worthTelling) notifyResolved(it) }
            PARTNER_CONFIRMED -> if (refreshPartner()) notifyPartnerConfirmed()
            // Un tipo que esta versión no conoce (el servidor es más nuevo que la app) se ignora.
            else -> Unit
        }
    }

    companion object {
        const val TYPE = "type"
        const val REQUEST_RESOLVED = "request_resolved"
        const val PARTNER_CONFIRMED = "partner_confirmed"
    }
}
