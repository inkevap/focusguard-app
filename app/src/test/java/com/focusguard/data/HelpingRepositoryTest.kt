package com.focusguard.data

import com.focusguard.server.ApiResult
import com.focusguard.server.FakeTransport
import com.focusguard.server.HelpedInfo
import com.focusguard.server.MemoryAccountStore
import com.focusguard.server.ServerApi
import com.focusguard.server.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class HelpingRepositoryTest {
    private val store = MemoryAccountStore().also { it.saveSession("a@b.co", "acc1", "ref1") }
    private val body = """{"helping":[{"id":"p1","email":"luis@example.com","since":"2026-10-01T00:00:00Z","mutual":false,"pending":1}]}"""
    private val luis = HelpedInfo("p1", "luis@example.com", mutual = false, pending = 1)

    private fun repository(transport: FakeTransport) = HelpingRepository(ServerApi(transport, { "https://fg.test" }, store))

    @Test fun guardaLoQueDiceElServidor() = runBlocking {
        val repository = repository(FakeTransport { json(200, body) })
        assertIs<ApiResult.Ok<Unit>>(repository.refresh())
        assertEquals(listOf(luis), repository.helping.value)
    }

    @Test fun sinConexionConservaLoUltimoQueSupo() = runBlocking {
        var online = true
        val repository = repository(FakeTransport { if (online) json(200, body) else null })
        repository.refresh()
        online = false
        assertIs<ApiResult.Failure>(repository.refresh())
        assertEquals(listOf(luis), repository.helping.value)
    }

    @Test fun conUnFalloDelServidorSeVacia() = runBlocking {
        var ok = true
        val repository = repository(FakeTransport { if (ok) json(200, body) else json(403, """{"error":"No"}""") })
        repository.refresh()
        ok = false
        assertIs<ApiResult.Failure>(repository.refresh())
        assertEquals(emptyList(), repository.helping.value)
    }

    @Test fun alCerrarLaSesionNoQuedaNada() = runBlocking {
        val repository = repository(FakeTransport { json(200, body) })
        repository.refresh()
        repository.clear()
        assertEquals(emptyList(), repository.helping.value)
    }

    @Test fun faltaMutuoOPendientesNoRompeLaLista() = runBlocking {
        val repository = repository(FakeTransport { json(200, """{"helping":[{"id":"p1","email":"luis@example.com"}]}""") })
        repository.refresh()
        assertEquals(listOf(HelpedInfo("p1", "luis@example.com", mutual = false, pending = 0)), repository.helping.value)
    }
}
