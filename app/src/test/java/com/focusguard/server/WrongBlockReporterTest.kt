package com.focusguard.server

import com.focusguard.data.WrongBlockReporter
import com.focusguard.decision.WrongBlockReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class WrongBlockReporterTest {
    private val report = WrongBlockReport(WrongBlockReport.Kind.DOMAIN, "ejemplo.org")

    private fun reporter(store: MemoryAccountStore, transport: FakeTransport) = WrongBlockReporter(ServerApi(transport, { "https://fg.test" }, store), store)

    @Test fun conCuentaSeEnviaSoloElTipoYElValor() = runBlocking<Unit> {
        val store = MemoryAccountStore().apply { saveSession("ana@example.com", "a", "r") }
        val transport = FakeTransport { json(201, """{"ok":true}""") }
        assertEquals(WrongBlockReporter.Outcome.SENT, reporter(store, transport).send(report))
        val sent = transport.requests.single()
        assertTrue(sent.url.endsWith("/reports/wrong-block"))
        assertEquals("""{"kind":"domain","value":"ejemplo.org"}""", sent.bodyText())
    }

    @Test fun sinCuentaNoSaleNada() = runBlocking<Unit> {
        val transport = FakeTransport { json(201, "{}") }
        assertEquals(WrongBlockReporter.Outcome.NEEDS_ACCOUNT, reporter(MemoryAccountStore(), transport).send(report))
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun unFalloDelServidorSeCuenta() = runBlocking<Unit> {
        val store = MemoryAccountStore().apply { saveSession("ana@example.com", "a", "r") }
        assertEquals(WrongBlockReporter.Outcome.FAILED, reporter(store, FakeTransport { json(429, """{"error":"x"}""") }).send(report))
    }
}
