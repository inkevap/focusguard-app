package com.focusguard.server

import com.focusguard.dns.DohClient
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class DohClientTest {
    private val query = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0, 1, 'a'.code.toByte(), 0, 0, 1, 0, 1)
    private val answer = query.copyOf().also { it[2] = 0x81.toByte() }

    @Test fun mandaLaConsultaTalCualYDevuelveLaRespuesta() = runBlocking {
        val transport = FakeTransport { HttpResponse(200, emptyMap(), answer) }
        assertContentEquals(answer, DohClient(transport).resolve("https://dns.test/dns-query/abc", query))
        val sent = transport.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("application/dns-message", sent.headers["content-type"])
        assertContentEquals(query, sent.body)
        assertEquals(4_000, sent.timeoutMs)
        assertEquals(65_535, sent.maxBodyBytes)
    }

    @Test fun sinRedNoHayRespuesta() = runBlocking {
        assertNull(DohClient(FakeTransport { null }).resolve("https://dns.test/x", query))
    }

    @Test fun unEstadoDistintoDe200NoSirve() = runBlocking {
        assertNull(DohClient(FakeTransport { HttpResponse(503, emptyMap(), answer) }).resolve("https://dns.test/x", query))
    }

    @Test fun laRespuestaDeOtraConsultaNoSeAcepta() = runBlocking {
        val other = answer.copyOf().also { it[1] = 0x99.toByte() }
        assertNull(DohClient(FakeTransport { HttpResponse(200, emptyMap(), other) }).resolve("https://dns.test/x", query))
    }

    @Test fun unCuerpoQueNoEsDnsNoSeAcepta() = runBlocking {
        assertNull(DohClient(FakeTransport { HttpResponse(200, emptyMap(), "<html>".toByteArray()) }).resolve("https://dns.test/x", query))
    }
}
