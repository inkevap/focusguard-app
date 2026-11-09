package com.focusguard.data

import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.RemoteStatus
import com.focusguard.server.ApiError
import com.focusguard.server.ApiResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Lo que pasó al aplicar solicitudes aprobadas: qué se aplicó y qué no se pudo, ya descritos para el usuario. */
data class AppliedReport(val applied: List<String> = emptyList(), val failed: List<String> = emptyList()) {
    val isEmpty: Boolean get() = applied.isEmpty() && failed.isEmpty()
}

/**
 * Aplica solas las solicitudes que el compañero ya aprobó, sin pantalla: en cuanto llega el aviso, en el envío periódico y al abrir
 * Aprobaciones. La secuencia es la de siempre: **primero se gasta la aprobación en el servidor** (vale una sola vez) y solo entonces
 * se aplica el cambio; si el servidor no deja gastarla, no se aplica nada.
 *
 * Una sola pasada a la vez (`lock`): dos avisos seguidos, o un aviso y la pantalla a la vez, no aplican dos veces lo mismo, y quien
 * llega segundo ve la lista ya al día.
 */
class ApprovalApplier(
    private val store: PartnerRequestStore,
    /** Gasta la aprobación en el servidor. */
    private val consume: suspend (ApprovalRequest) -> ApiResult<Unit>,
    /** Aplica el cambio y retira la solicitud; devuelve un mensaje de error, o null si se aplicó. */
    private val apply: suspend (ApprovalRequest) -> String?,
    /** Motivo por el que ahora no se puede aplicar esa solicitud (modo estricto); la aprobación no se gasta y sigue valiendo. */
    private val blockedReason: (ApprovalRequest) -> String?,
    private val describe: (ApprovalRequest) -> String,
) {
    private val lock = Mutex()

    suspend fun applyApproved(): AppliedReport = lock.withLock {
        val applied = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val approved = store.pending.value.filter { it.mode == ApprovalMode.PARTNER && it.remote == RemoteStatus.APPROVED }
        for (request in approved) {
            if (blockedReason(request) != null) continue
            val what = describe(request)
            when (val spent = consume(request)) {
                is ApiResult.Failure -> {
                    // Sin conexión o con el servidor caído se reintenta en la siguiente pasada; un rechazo definitivo (ya usada, caducada)
                    // no se arregla solo: se retira para que no se quede atascada y se cuenta.
                    val error = spent.error
                    if (error is ApiError.Http && !error.retryable) {
                        store.remove(request.id)
                        failed += "$what: ${error.message}"
                    }
                }
                is ApiResult.Ok -> {
                    val problem = apply(request)
                    if (problem == null) {
                        applied += what
                    } else {
                        // La aprobación ya se gastó: dejarla aquí solo daría un botón que ya no puede funcionar.
                        store.remove(request.id)
                        failed += "$what: $problem"
                    }
                }
            }
        }
        AppliedReport(applied, failed)
    }
}
