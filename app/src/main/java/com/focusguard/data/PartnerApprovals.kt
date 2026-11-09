package com.focusguard.data

import com.focusguard.decision.Approvals
import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.RemoteStatus
import com.focusguard.server.AccountStore
import com.focusguard.server.ApiError
import com.focusguard.server.ApiResult
import com.focusguard.server.map
import com.focusguard.server.ServerApi
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Lo que `PartnerApprovals` necesita de las solicitudes locales. */
interface PartnerRequestStore {
    val pending: StateFlow<List<ApprovalRequest>>

    fun setRemote(id: Long, remoteId: String?, status: RemoteStatus)

    fun remove(id: Long)
}

/** Estados en los que el compañero ya respondió. */
private val RESOLVED = setOf(RemoteStatus.APPROVED, RemoteStatus.REJECTED)

/**
 * Lleva las solicitudes que se aprueban por el compañero entre el teléfono y el servidor: las envía, consulta su estado y,
 * al aplicarlas, las marca como usadas (una aprobación vale una sola vez).
 */
class PartnerApprovals(
    private val api: ServerApi,
    private val store: PartnerRequestStore,
    private val account: AccountStore,
    private val now: () -> Instant = Instant::now,
) {
    private val lock = Mutex()

    /** Solicitudes que el servidor rechazó al enviarlas (no por falta de red): no se reintentan solas cada pocos segundos. */
    private val refused = mutableSetOf<Long>()

    /** Envía las que faltan y actualiza las que esperan. Devuelve el primer error que no sea de conexión, para mostrarlo. */
    suspend fun sync(): ApiResult<Unit> = lock.withLock {
        var failure: ApiResult.Failure? = null
        for (request in store.pending.value.filter { it.mode == ApprovalMode.PARTNER }) {
            val result = when {
                // Una solicitud con espera previa no sale hasta que pase (así el compañero no recibe el correo antes de tiempo).
                request.remoteId == null -> if (request.id in refused || request.sendAfter?.isAfter(now()) == true) ApiResult.Ok(Unit) else send(request)
                request.remote == RemoteStatus.WAITING -> refresh(request)
                else -> ApiResult.Ok(Unit)
            }
            if (result is ApiResult.Failure && failure == null && result.error != ApiError.Offline) failure = result
        }
        failure ?: ApiResult.Ok(Unit)
    }

    /** Como [sync], y dice si alguna solicitud pasó a aprobada o rechazada con esa consulta (para avisar al usuario, que no la está mirando). */
    suspend fun syncReportingResolved(): Boolean {
        val before = store.pending.value.associate { it.id to it.remote }
        sync()
        return store.pending.value.any { it.mode == ApprovalMode.PARTNER && it.remote in RESOLVED && before[it.id] != it.remote }
    }

    private suspend fun send(request: ApprovalRequest): ApiResult<Unit> {
        val deviceId = account.deviceId ?: return ApiResult.Failure(ApiError.Http(0, "Este teléfono aún no está registrado en tu cuenta."))
        // El compañero solo ve qué quieres hacer, no el contenido del cambio.
        return api.createRequest(deviceId, request.actionId, Approvals.describe(request.actionKey))
            .map { store.setRemote(request.id, it, RemoteStatus.WAITING) }
            .also { if (it is ApiResult.Failure && !it.error.retryable && it.error != ApiError.SessionExpired) refused += request.id }
    }

    private suspend fun refresh(request: ApprovalRequest): ApiResult<Unit> {
        val remoteId = request.remoteId ?: return ApiResult.Ok(Unit)
        return when (val result = api.request(remoteId)) {
            is ApiResult.Ok -> {
                if (result.value != request.remote) store.setRemote(request.id, null, result.value)
                ApiResult.Ok(Unit)
            }
            is ApiResult.Failure ->
                if (result.error.isRequestUnknown) {
                    store.setRemote(request.id, null, RemoteStatus.EXPIRED)
                    ApiResult.Ok(Unit)
                } else {
                    result
                }
        }
    }

    /** Gasta la aprobación en el servidor. Hay que hacerlo antes de aplicar el cambio: si no se puede, no se aplica. */
    suspend fun consume(request: ApprovalRequest): ApiResult<Unit> {
        val remoteId = request.remoteId ?: return ApiResult.Failure(ApiError.Http(0, "Esta solicitud no se envió al compañero."))
        return api.consumeRequest(remoteId)
    }

    /**
     * Retira la solicitud del servidor si estaba esperando, para que el enlace del correo deje de valer. Devuelve el resultado: sin conexión
     * la solicitud sigue viva en el servidor y quien llama no debe darla por cancelada.
     */
    suspend fun cancel(request: ApprovalRequest): ApiResult<Unit> {
        refused -= request.id
        val remoteId = request.remoteId ?: return ApiResult.Ok(Unit)
        return if (request.remote == RemoteStatus.WAITING) api.cancelRequest(remoteId) else ApiResult.Ok(Unit)
    }
}
