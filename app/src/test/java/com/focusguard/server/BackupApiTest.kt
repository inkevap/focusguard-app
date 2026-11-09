package com.focusguard.server

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class BackupApiTest {
    private val store = MemoryAccountStore().apply { saveSession("ana@example.com", "a", "r") }
    private fun api(transport: FakeTransport) = ServerApi(transport, { "https://fg.test" }, store)

    @Test fun subeLosBytesTalCualConSuFormato() = runBlocking<Unit> {
        val transport = FakeTransport { json(201, """{"id":"b1","createdAt":"2026-01-01T00:00:00Z"}""") }
        val blob = byteArrayOf(1, 2, 3, 0, 127, -1)
        assertIs<ApiResult.Ok<Unit>>(api(transport).uploadBackup(1, blob))
        val sent = transport.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("application/octet-stream", sent.headers["content-type"])
        assertEquals("1", sent.headers["x-backup-format"])
        assertContentEquals(blob, sent.body)
        assertTrue(sent.bearer()!!.startsWith("Bearer "))
    }

    @Test fun bajaLaUltimaCopiaConSuFormatoYSuFecha() = runBlocking<Unit> {
        val blob = byteArrayOf(9, 8, 7)
        val transport = FakeTransport { HttpResponse(200, mapOf("x-backup-format" to "1", "x-backup-created" to "2026-01-01T00:00:00.000Z"), blob) }
        val result = api(transport).latestBackup(1)
        assertIs<ApiResult.Ok<RemoteBackup>>(result)
        assertEquals(1, result.value.format)
        assertContentEquals(blob, result.value.blob)
        assertTrue(transport.requests.single().url.endsWith("/backups/latest?maxFormat=1"))
        assertEquals("application/octet-stream", transport.requests.single().headers["accept"])
    }

    @Test fun sinCopiasElServidorDiceQueNoHay() = runBlocking<Unit> {
        val result = api(FakeTransport { json(404, """{"error":"No hay copias"}""") }).latestBackup(1)
        assertIs<ApiResult.Failure>(result)
        assertTrue(result.error.isNotFound)
    }

    @Test fun unaRespuestaSinFormatoNoSeAcepta() = runBlocking<Unit> {
        assertIs<ApiResult.Failure>(api(FakeTransport { HttpResponse(200, emptyMap(), byteArrayOf(1)) }).latestBackup(1))
    }

    @Test fun elErrorDelServidorAlSubirSeConserva() = runBlocking<Unit> {
        val result = api(FakeTransport { json(429, """{"error":"Demasiadas copias hoy"}""") }).uploadBackup(1, byteArrayOf(1))
        assertIs<ApiResult.Failure>(result)
        assertTrue(result.error.retryable)
    }
}
