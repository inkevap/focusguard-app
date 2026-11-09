package com.focusguard.server

import com.focusguard.data.PartnerApprovals
import com.focusguard.data.PartnerRepository
import com.focusguard.data.PartnerRequestStore
import com.focusguard.data.PartnerState
import com.focusguard.data.PartnerStore
import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.RemoteStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

internal class MemoryPartnerStore(var value: PartnerState = PartnerState.None) : PartnerStore {
    override fun read() = value

    override fun write(state: PartnerState) {
        value = state
    }
}

private class MemoryRequests(initial: List<ApprovalRequest>) : PartnerRequestStore {
    private val _pending = MutableStateFlow(initial)
    override val pending: StateFlow<List<ApprovalRequest>> = _pending

    override fun setRemote(id: Long, remoteId: String?, status: RemoteStatus) {
        _pending.value = _pending.value.map { if (it.id == id) it.copy(remoteId = remoteId ?: it.remoteId, remote = status) else it }
    }

    override fun remove(id: Long) {
        _pending.value = _pending.value.filterNot { it.id == id }
    }

    fun get(id: Long) = _pending.value.first { it.id == id }
}

private fun partnerRequest(id: Long = 1, remoteId: String? = null, remote: RemoteStatus = RemoteStatus.NOT_SENT, mode: ApprovalMode = ApprovalMode.PARTNER) =
    ApprovalRequest(id, "pause_profile", 1001, mode, Instant.parse("2026-01-01T00:00:00Z"), remoteId = remoteId, remote = remote)

class PartnerRepositoryTest {
    private val account = MemoryAccountStore().apply { saveSession("ana@example.com", "a", "r") }
    private val partnerStore = MemoryPartnerStore()

    private fun repo(transport: FakeTransport) = PartnerRepository(ServerApi(transport, { "https://fg.test" }, account), partnerStore)

    @Test fun refreshReportingActivationDiceSoloLaPrimeraVezQueAcepta() = runBlocking {
        partnerStore.value = PartnerState.Invited("p1", "luis@example.com")
        val repo = repo(FakeTransport { json(200, """{"partners":[{"id":"p1","email":"luis@example.com","status":"aceptado"}]}""") })
        assertTrue(repo.refreshReportingActivation())
        assertFalse(repo.refreshReportingActivation())
    }

    @Test fun unCompanieroQueSigueInvitadoNoCuentaComoActivado() = runBlocking {
        partnerStore.value = PartnerState.Invited("p1", "luis@example.com")
        val repo = repo(FakeTransport { json(200, """{"partners":[{"id":"p1","email":"luis@example.com","status":"pendiente"}]}""") })
        assertFalse(repo.refreshReportingActivation())
    }

    @Test fun sinConexionNoSeDiceQueSeActivo() = runBlocking {
        partnerStore.value = PartnerState.Invited("p1", "luis@example.com")
        assertFalse(repo(FakeTransport { null }).refreshReportingActivation())
    }

    @Test fun refreshLeeAlCompanieroYLoGuarda() = runBlocking {
        val transport = FakeTransport { json(200, """{"partners":[{"id":"p1","email":"luis@example.com","status":"aceptado"}]}""") }
        val repo = repo(transport)
        assertIs<ApiResult.Ok<Unit>>(repo.refresh())
        assertEquals(PartnerState.Active("p1", "luis@example.com"), repo.state.value)
        assertEquals(PartnerState.Active("p1", "luis@example.com"), partnerStore.value)
    }

    @Test fun sinConexionSeConservaLoUltimoQueSeSupo() = runBlocking {
        partnerStore.value = PartnerState.Active("p1", "luis@example.com")
        val repo = repo(FakeTransport { null })
        assertIs<ApiResult.Failure>(repo.refresh())
        assertEquals(PartnerState.Active("p1", "luis@example.com"), repo.state.value)
    }

    @Test fun sinCompanieroActivoSeAvisa() = runBlocking {
        var notices = 0
        val none = PartnerRepository(ServerApi(FakeTransport { json(200, """{"partners":[]}""") }, { "https://fg.test" }, account), partnerStore, onNoActivePartner = { notices++ })
        none.refresh()
        assertEquals(1, notices)
        val active = PartnerRepository(ServerApi(FakeTransport { json(200, """{"partners":[{"id":"p","email":"l@e.com","status":"aceptado"}]}""") }, { "https://fg.test" }, account), partnerStore, onNoActivePartner = { notices++ })
        active.refresh()
        assertEquals(1, notices)
    }

    @Test fun alAceptarElCompanieroSeAvisaUnaSolaVez() = runBlocking {
        var activated = 0
        val transport = FakeTransport { json(200, """{"partners":[{"id":"p","email":"l@e.com","status":"aceptado"}]}""") }
        val repo = PartnerRepository(ServerApi(transport, { "https://fg.test" }, account), partnerStore, onActivated = { activated++ })
        repo.refresh()
        repo.refresh()
        assertEquals(1, activated)
    }

    @Test fun invitarDejaAlCompanieroComoInvitado() = runBlocking {
        val transport = FakeTransport { json(201, """{"id":"p2","email":"luis@example.com"}""") }
        val repo = repo(transport)
        assertIs<ApiResult.Ok<Unit>>(repo.invite("luis@example.com"))
        assertEquals(PartnerState.Invited("p2", "luis@example.com"), repo.state.value)
    }

    @Test fun conVariosCompanierosElPrincipalEsElPrimeroQueAcepto() = runBlocking {
        val repo = repo(FakeTransport { json(200, """{"partners":[{"id":"p1","email":"a@e.com","status":"pendiente"},{"id":"p2","email":"b@e.com","status":"aceptado"}]}""") })
        repo.refresh()
        assertEquals(PartnerState.Active("p2", "b@e.com"), repo.state.value)
        assertEquals(listOf("p1", "p2"), repo.partners.value.map { it.id })
        assertTrue(repo.isActive)
    }

    @Test fun invitarAOtroNoQuitaAlCompanieroActivo() = runBlocking {
        partnerStore.value = PartnerState.Active("p1", "luis@example.com")
        val repo = repo(FakeTransport { json(201, """{"id":"p2","email":"marta@example.com"}""") })
        assertIs<ApiResult.Ok<Unit>>(repo.invite("marta@example.com"))
        assertEquals(PartnerState.Active("p1", "luis@example.com"), repo.state.value)
        assertEquals(listOf("p1", "p2"), repo.partners.value.map { it.id })
    }

    @Test fun noHayMasDeTresCompanieros() = runBlocking {
        val repo = repo(FakeTransport { json(200, """{"partners":[{"id":"1","email":"a@e.com","status":"aceptado"},{"id":"2","email":"b@e.com","status":"aceptado"},{"id":"3","email":"c@e.com","status":"aceptado"},{"id":"4","email":"d@e.com","status":"aceptado"}]}""") })
        repo.refresh()
        assertEquals(PartnerRepository.MAX_PARTNERS, repo.partners.value.size)
    }

    @Test fun cancelarUnaInvitacionConcretaDejaALosDemas() = runBlocking {
        val transport = FakeTransport { json(200, """{"partners":[{"id":"p1","email":"a@e.com","status":"aceptado"},{"id":"p2","email":"b@e.com","status":"pendiente"}]}""") }
        val repo = repo(transport)
        repo.refresh()
        val noContent = FakeTransport { json(204, "") }
        val removing = PartnerRepository(ServerApi(noContent, { "https://fg.test" }, account), MemoryPartnerStore(repo.state.value))
        // Con un almacén que solo recuerda al principal, quitar a otro por su id no toca al principal.
        assertIs<ApiResult.Ok<PartnerRemoval>>(removing.remove("zzz"))
        assertEquals(PartnerState.Active("p1", "a@e.com"), removing.state.value)
    }

    @Test fun cancelarUnaInvitacionEsLibre() = runBlocking {
        partnerStore.value = PartnerState.Invited("p2", "luis@example.com")
        val transport = FakeTransport { json(204, "") }
        val repo = repo(transport)
        val removed = repo.remove()
        assertIs<ApiResult.Ok<PartnerRemoval>>(removed)
        assertEquals(PartnerRemoval.Done, removed.value)
        assertEquals(PartnerState.None, repo.state.value)
        assertEquals("DELETE", transport.requests.single().method)
    }

    @Test fun quitarAUnCompanieroActivoPideSuAprobacionYNoLoQuitaTodavia() = runBlocking {
        partnerStore.value = PartnerState.Active("p1", "luis@example.com")
        val transport = FakeTransport { json(202, """{"requestId":"req-9"}""") }
        val repo = repo(transport)
        val result = repo.remove()
        assertIs<ApiResult.Ok<PartnerRemoval>>(result)
        assertEquals("req-9", assertIs<PartnerRemoval.NeedsApproval>(result.value).requestId)
        assertEquals(PartnerState.Active("p1", "luis@example.com"), repo.state.value)
    }

    @Test fun conLaSesionCaducadaSeConservaElCompaniero() = runBlocking {
        partnerStore.value = PartnerState.Active("p1", "luis@example.com")
        account.expireSession()
        val repo = repo(FakeTransport { json(401, """{"error":"x"}""") })
        assertIs<ApiResult.Failure>(repo.refresh())
        assertEquals(PartnerState.Active("p1", "luis@example.com"), repo.state.value)
    }
}

class PartnerApprovalsTest {
    private val account = MemoryAccountStore().apply {
        saveSession("ana@example.com", "a", "r")
        saveDeviceId("dev-1")
    }

    private fun approvals(requests: MemoryRequests, transport: FakeTransport) =
        PartnerApprovals(ServerApi(transport, { "https://fg.test" }, account), requests, account)

    @Test fun enviaLaSolicitudPendienteYGuardaSuId() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest()))
        val transport = FakeTransport { json(201, """{"requestId":"req-1"}""") }
        assertIs<ApiResult.Ok<Unit>>(approvals(requests, transport).sync())
        assertEquals("req-1", requests.get(1).remoteId)
        assertEquals(RemoteStatus.WAITING, requests.get(1).remote)
        val body = transport.requests.single().bodyText()
        assertTrue(body.contains("dev-1") && body.contains("1001"))
    }

    @Test fun unaSolicitudConEsperaPreviaNoSaleHastaQuePasa() = runBlocking {
        val sendAfter = Instant.parse("2026-01-02T00:00:00Z")
        val held = partnerRequest().copy(sendAfter = sendAfter)
        val transport = FakeTransport { json(201, """{"requestId":"req-1"}""") }

        val early = MemoryRequests(listOf(held))
        val before = PartnerApprovals(ServerApi(transport, { "https://fg.test" }, account), early, account) { sendAfter.minusSeconds(1) }
        assertIs<ApiResult.Ok<Unit>>(before.sync())
        assertTrue(transport.requests.isEmpty(), "no debe llegar al servidor antes de tiempo")
        assertEquals(RemoteStatus.NOT_SENT, early.get(1).remote)

        val late = MemoryRequests(listOf(held))
        val after = PartnerApprovals(ServerApi(transport, { "https://fg.test" }, account), late, account) { sendAfter }
        assertIs<ApiResult.Ok<Unit>>(after.sync())
        assertEquals("req-1", late.get(1).remoteId)
        assertEquals(1, transport.requests.size)
    }

    @Test fun syncReportingResolvedDiceQueUnaSolicitudSeResolvio() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest(remoteId = "req-1", remote = RemoteStatus.WAITING)))
        val transport = FakeTransport { json(200, """{"status":"aprobada","consumed":false}""") }
        val partnerApprovals = approvals(requests, transport)
        assertTrue(partnerApprovals.syncReportingResolved())
        assertEquals(RemoteStatus.APPROVED, requests.get(1).remote)
        // Con la misma respuesta ya no hay nada nuevo que contar: no se avisa dos veces.
        assertFalse(partnerApprovals.syncReportingResolved())
    }

    @Test fun syncReportingResolvedNoCuentaLoQueSigueEsperando() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest(remoteId = "req-1", remote = RemoteStatus.WAITING)))
        val transport = FakeTransport { json(200, """{"status":"pendiente","consumed":false}""") }
        assertFalse(approvals(requests, transport).syncReportingResolved())
    }

    @Test fun cancelarUnaSolicitudEnEsperaDevuelveLoQueDiceElServidor() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest(remoteId = "req-1", remote = RemoteStatus.WAITING)))
        val ok = FakeTransport { json(200, "{}") }
        assertIs<ApiResult.Ok<Unit>>(approvals(requests, ok).cancel(requests.get(1)))
        assertEquals("/requests/req-1/cancel", ok.paths().single())
        val offline = FakeTransport { null }
        assertIs<ApiResult.Failure>(approvals(requests, offline).cancel(requests.get(1)))
        assertEquals(RemoteStatus.WAITING, requests.get(1).remote)
    }

    @Test fun cancelarUnaSolicitudQueNoSeEnvioNoLlamaAlServidor() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest()))
        val transport = FakeTransport { json(200, "{}") }
        assertIs<ApiResult.Ok<Unit>>(approvals(requests, transport).cancel(requests.get(1)))
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun noMandaLasSolicitudesQueNoSonDelCompaniero() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest(mode = ApprovalMode.DELAY_24H)))
        val transport = FakeTransport { json(201, """{"requestId":"x"}""") }
        approvals(requests, transport).sync()
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun sinConexionLaSolicitudSeQuedaPorEnviarSinMostrarError() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest()))
        assertIs<ApiResult.Ok<Unit>>(approvals(requests, FakeTransport { null }).sync())
        assertNull(requests.get(1).remoteId)
        assertEquals(RemoteStatus.NOT_SENT, requests.get(1).remote)
    }

    @Test fun elErrorDelServidorSiSeMuestra() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest()))
        val transport = FakeTransport { json(403, """{"error":"No hay un compañero activo"}""") }
        val result = approvals(requests, transport).sync()
        assertIs<ApiResult.Failure>(result)
        assertEquals(403, (result.error as ApiError.Http).status)
    }

    @Test fun siElServidorRechazaElEnvioNoSeReintentaSolo() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest()))
        val transport = FakeTransport { json(409, """{"error":"Aún no tienes un compañero que haya aceptado"}""") }
        val approvals = approvals(requests, transport)
        assertIs<ApiResult.Failure>(approvals.sync())
        assertIs<ApiResult.Ok<Unit>>(approvals.sync())
        assertEquals(1, transport.requests.size)
    }

    @Test fun unErrorDeRedSiSeReintenta() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest()))
        val transport = FakeTransport { json(503, """{"error":"x"}""") }
        val approvals = approvals(requests, transport)
        approvals.sync()
        approvals.sync()
        assertEquals(2, transport.requests.size)
    }

    @Test fun laRespuestaDelCompaniero() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest(remoteId = "req-1", remote = RemoteStatus.WAITING)))
        val transport = FakeTransport { json(200, """{"status":"aprobada","consumed":false}""") }
        approvals(requests, transport).sync()
        assertEquals(RemoteStatus.APPROVED, requests.get(1).remote)
        assertEquals("req-1", requests.get(1).remoteId)
    }

    @Test fun unaAprobacionYaUsadaNoSePuedeAplicarOtraVez() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest(remoteId = "req-1", remote = RemoteStatus.WAITING)))
        val transport = FakeTransport { json(200, """{"status":"aprobada","consumed":true}""") }
        approvals(requests, transport).sync()
        assertEquals(RemoteStatus.EXPIRED, requests.get(1).remote)
    }

    @Test fun siElServidorYaNoConoceLaSolicitudCaduca() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest(remoteId = "req-1", remote = RemoteStatus.WAITING)))
        approvals(requests, FakeTransport { json(404, """{"error":"x","code":"request_unknown"}""") }).sync()
        assertEquals(RemoteStatus.EXPIRED, requests.get(1).remote)
    }

    @Test fun unNotFoundSinCodigoNoCaducaLaSolicitud() = runBlocking {
        val requests = MemoryRequests(listOf(partnerRequest(remoteId = "req-1", remote = RemoteStatus.WAITING)))
        val result = approvals(requests, FakeTransport { json(404, "<html>no hay ruta</html>") }).sync()
        assertIs<ApiResult.Failure>(result)
        assertEquals(RemoteStatus.WAITING, requests.get(1).remote)
    }

    @Test fun consumirLlamaAlServidorUnaVez() = runBlocking {
        val transport = FakeTransport { json(204, "") }
        val request = partnerRequest(remoteId = "req-1", remote = RemoteStatus.APPROVED)
        assertIs<ApiResult.Ok<Unit>>(approvals(MemoryRequests(listOf(request)), transport).consume(request))
        assertEquals("/req-1/consume", transport.paths().single().substringAfter("requests"))
    }

    @Test fun sinIdRemotoNoSePuedeConsumir() = runBlocking {
        val request = partnerRequest()
        val result = approvals(MemoryRequests(listOf(request)), FakeTransport { null }).consume(request)
        assertIs<ApiResult.Failure>(result)
        assertTrue(result.error is ApiError.Http)
    }

    @Test fun cancelarSoloAvisaAlServidorSiEstabaEsperando() = runBlocking {
        val transport = FakeTransport { json(204, "") }
        val waiting = partnerRequest(remoteId = "req-1", remote = RemoteStatus.WAITING)
        val done = partnerRequest(id = 2, remoteId = "req-2", remote = RemoteStatus.APPROVED)
        val approvals = approvals(MemoryRequests(listOf(waiting, done)), transport)
        approvals.cancel(waiting)
        approvals.cancel(done)
        assertEquals(1, transport.requests.size)
        assertTrue(transport.requests.single().url.endsWith("/requests/req-1/cancel"))
    }
}
