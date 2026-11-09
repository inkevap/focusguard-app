package com.focusguard.push

import com.focusguard.data.ApprovalsReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class PushHandlerTest {
    private val calls = mutableListOf<String>()
    private var signedIn = true
    private var report = ApprovalsReport(resolved = true)
    private var activated = true

    private val handler = PushHandler(
        signedIn = { signedIn },
        syncApprovals = { calls += "aprobaciones"; report },
        refreshPartner = { calls += "companero"; activated },
        notifyResolved = { calls += "notificacion:${it.applied.size}/${it.failed.size}" },
        notifyPartnerConfirmed = { calls += "aceptado" },
    )

    @Test fun unaSolicitudResueltaSincronizaYAvisaAlUsuario() = runBlocking {
        handler.handle(mapOf("type" to "request_resolved", "requestId" to "abc"))
        assertEquals(listOf("aprobaciones", "notificacion:0/0"), calls)
    }

    @Test fun loQueSeAplicoSoloSeCuentaEnElAviso() = runBlocking {
        report = ApprovalsReport(resolved = true, applied = listOf("apagar el DNS"))
        handler.handle(mapOf("type" to "request_resolved"))
        assertEquals(listOf("aprobaciones", "notificacion:1/0"), calls)
    }

    @Test fun unFalloAlAplicarTambienSeAvisa() = runBlocking {
        report = ApprovalsReport(resolved = false, failed = listOf("apagar el DNS: ya no existe"))
        handler.handle(mapOf("type" to "request_resolved"))
        assertEquals(listOf("aprobaciones", "notificacion:0/1"), calls)
    }

    @Test fun siNingunaSolicitudCambioNoSeAvisa() = runBlocking {
        report = ApprovalsReport(resolved = false)
        handler.handle(mapOf("type" to "request_resolved"))
        assertEquals(listOf("aprobaciones"), calls)
    }

    @Test fun elCompanieroQueAceptaSeRefrescaYSeAvisaAlUsuario() = runBlocking {
        handler.handle(mapOf("type" to "partner_confirmed"))
        assertEquals(listOf("companero", "aceptado"), calls)
    }

    @Test fun unAvisoRepetidoDelCompanieroNoVuelveAMolestar() = runBlocking {
        activated = false
        handler.handle(mapOf("type" to "partner_confirmed"))
        assertEquals(listOf("companero"), calls)
    }

    @Test fun sinSesionNoSePreguntaNiSeAvisa() = runBlocking {
        signedIn = false
        handler.handle(mapOf("type" to "request_resolved"))
        handler.handle(mapOf("type" to "partner_confirmed"))
        assertEquals(emptyList(), calls)
    }

    @Test fun unTipoDesconocidoOUnAvisoVacioSeIgnora() = runBlocking {
        handler.handle(mapOf("type" to "algo_nuevo"))
        handler.handle(emptyMap())
        assertEquals(emptyList(), calls)
    }
}
