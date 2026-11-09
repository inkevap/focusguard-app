package com.focusguard.server

import com.focusguard.data.ApprovalApplier
import com.focusguard.data.PartnerRequestStore
import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.RemoteStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

private class Requests(initial: List<ApprovalRequest>) : PartnerRequestStore {
    private val _pending = MutableStateFlow(initial)
    override val pending: StateFlow<List<ApprovalRequest>> = _pending
    override fun setRemote(id: Long, remoteId: String?, status: RemoteStatus) {
        _pending.value = _pending.value.map { if (it.id == id) it.copy(remote = status) else it }
    }
    override fun remove(id: Long) { _pending.value = _pending.value.filterNot { it.id == id } }
}

private fun request(id: Long, remote: RemoteStatus = RemoteStatus.APPROVED, mode: ApprovalMode = ApprovalMode.PARTNER) =
    ApprovalRequest(id, "dns_off", 1001, mode, Instant.parse("2026-01-01T00:00:00Z"), remoteId = "r$id", remote = remote)

class ApprovalApplierTest {
    private val log = mutableListOf<String>()

    private fun applier(
        store: Requests,
        consume: suspend (ApprovalRequest) -> ApiResult<Unit> = { log += "gasta ${it.id}"; ApiResult.Ok(Unit) },
        apply: suspend (ApprovalRequest) -> String? = { log += "aplica ${it.id}"; store.remove(it.id); null },
        blocked: (ApprovalRequest) -> String? = { null },
    ) = ApprovalApplier(store, consume, apply, blocked) { "cambio ${it.id}" }

    @Test fun gastaLaAprobacionYLuegoAplicaYRetiraLaSolicitud() = runBlocking {
        val store = Requests(listOf(request(1)))
        val report = applier(store).applyApproved()
        assertEquals(listOf("gasta 1", "aplica 1"), log)
        assertEquals(listOf("cambio 1"), report.applied)
        assertTrue(report.failed.isEmpty())
        assertTrue(store.pending.value.isEmpty())
    }

    @Test fun elBloqueoSeMiraSolicitudPorSolicitud() = runBlocking {
        // La salida del propio modo estricto no se frena aunque lo demás sí: es lo que el compañero aprueba.
        val exit = request(2).copy(actionKey = "strict_exit:disable")
        val store = Requests(listOf(request(1), exit))
        val report = applier(store, blocked = { if (it.actionKey.startsWith("strict_exit:")) null else "Modo estricto" }).applyApproved()
        assertEquals(listOf("gasta 2", "aplica 2"), log)
        assertEquals(listOf("cambio 2"), report.applied)
        assertEquals(listOf(1L), store.pending.value.map { it.id })
    }

    @Test fun soloAplicaLasAprobadasDelCompaniero() = runBlocking {
        val store = Requests(listOf(
            request(1, RemoteStatus.WAITING), request(2, RemoteStatus.REJECTED), request(3, RemoteStatus.EXPIRED),
            request(4, mode = ApprovalMode.DELAY_24H), request(5),
        ))
        val report = applier(store).applyApproved()
        assertEquals(listOf("gasta 5", "aplica 5"), log)
        assertEquals(listOf("cambio 5"), report.applied)
        assertEquals(listOf(1L, 2L, 3L, 4L), store.pending.value.map { it.id })
    }

    @Test fun conElModoEstrictoNoSeGastaLaAprobacionYSigueValiendo() = runBlocking {
        val store = Requests(listOf(request(1)))
        val report = applier(store, blocked = { "Modo estricto" }).applyApproved()
        assertTrue(log.isEmpty())
        assertTrue(report.isEmpty)
        assertEquals(listOf(1L), store.pending.value.map { it.id })
    }

    @Test fun sinConexionSeReintentaDespuesYNoSeCuentaComoFallo() = runBlocking {
        val store = Requests(listOf(request(1)))
        val report = applier(store, consume = { log += "intenta ${it.id}"; ApiResult.Failure(ApiError.Offline) }).applyApproved()
        assertEquals(listOf("intenta 1"), log)
        assertTrue(report.isEmpty)
        assertEquals(listOf(1L), store.pending.value.map { it.id })
    }

    @Test fun unRechazoDefinitivoDelServidorRetiraLaSolicitudYLoCuenta() = runBlocking {
        val store = Requests(listOf(request(1)))
        val report = applier(store, consume = { ApiResult.Failure(ApiError.Http(409, "Ya se usó")) }).applyApproved()
        assertTrue(log.isEmpty())
        assertEquals(listOf("cambio 1: Ya se usó"), report.failed)
        assertTrue(store.pending.value.isEmpty())
    }

    @Test fun unaSesionCaducadaNoBorraLaAprobacion() = runBlocking {
        val store = Requests(listOf(request(1)))
        val report = applier(store, consume = { ApiResult.Failure(ApiError.SessionExpired) }).applyApproved()
        assertTrue(report.isEmpty)
        assertEquals(listOf(1L), store.pending.value.map { it.id })
    }

    @Test fun siAplicarFallaTrasGastarSeRetiraYSeCuenta() = runBlocking {
        val store = Requests(listOf(request(1)))
        val report = applier(store, apply = { log += "aplica ${it.id}"; "El perfil ya no existe." }).applyApproved()
        assertEquals(listOf("gasta 1", "aplica 1"), log)
        assertEquals(listOf("cambio 1: El perfil ya no existe."), report.failed)
        assertTrue(store.pending.value.isEmpty())
    }

    @Test fun dosPasadasAlaVezNoAplicanDosVecesLoMismo() = runBlocking {
        val store = Requests(listOf(request(1)))
        val a = applier(store, consume = { delay(50); log += "gasta ${it.id}"; ApiResult.Ok(Unit) })
        val reports = listOf(async { a.applyApproved() }, async { a.applyApproved() }).awaitAll()
        assertEquals(listOf("gasta 1", "aplica 1"), log)
        assertEquals(1, reports.sumOf { it.applied.size })
    }
}
