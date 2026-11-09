package com.focusguard.server

import com.focusguard.data.EventStore
import com.focusguard.data.PartnerEventOutbox
import com.focusguard.data.PartnerRepository
import com.focusguard.data.PartnerState
import com.focusguard.model.PartnerEvent
import com.focusguard.model.PartnerEventType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

private class MemoryEvents : EventStore {
    var events = emptyList<PartnerEvent>()

    override fun read() = events

    override fun write(events: List<PartnerEvent>) {
        this.events = events
    }
}

class PartnerEventOutboxTest {
    private val account = MemoryAccountStore().apply {
        saveSession("ana@example.com", "a", "r")
        saveDeviceId("dev-1")
    }
    private val events = MemoryEvents()
    private var flushes = 0
    private var noticesOn = true
    private val t0 = Instant.parse("2026-01-01T10:00:00Z")

    private fun outbox(transport: FakeTransport, partner: PartnerState = PartnerState.Active("p", "luis@example.com")): PartnerEventOutbox {
        val api = ServerApi(transport, { "https://fg.test" }, account)
        return PartnerEventOutbox(api, events, account, PartnerRepository(api, MemoryPartnerStore(partner)), { noticesOn }, { t0 }, { flushes++ })
    }

    @Test fun sinCompanieroActivoNoSeGuardaNada() {
        val outbox = outbox(FakeTransport { null }, PartnerState.Invited("p", "luis@example.com"))
        outbox.record(PartnerEventType.PANIC)
        assertTrue(events.events.isEmpty())
        assertEquals(0, flushes)
    }

    @Test fun conLosAvisosApagadosNoSeGuardaNada() {
        noticesOn = false
        outbox(FakeTransport { null }).record(PartnerEventType.PANIC)
        assertTrue(events.events.isEmpty())
    }

    @Test fun unSucesoGraveGuardaYPideEnvioInmediato() {
        outbox(FakeTransport { null }).record(PartnerEventType.SETTINGS_ATTEMPT, "ajustes")
        assertEquals(1, events.events.size)
        assertEquals(1, flushes)
    }

    @Test fun unBloqueoEsperaAlEnvioPeriodico() {
        outbox(FakeTransport { null }).record(PartnerEventType.BLOCKED, "com.example")
        assertEquals(1, events.events.size)
        assertEquals(0, flushes)
    }

    @Test fun elEnvioVaciaLaColaYSoloLlevaQueYCuando() = runBlocking {
        val transport = FakeTransport { json(202, """{"ok":true}""") }
        val outbox = outbox(transport)
        outbox.record(PartnerEventType.BLOCKED, "com.example")
        assertIs<ApiResult.Ok<Unit>>(outbox.flush())
        assertTrue(events.events.isEmpty())
        val body = transport.requests.single().bodyText()
        assertTrue(body.contains("\"blocked\"") && body.contains("dev-1") && body.contains("com.example"))
    }

    @Test fun sinRedLaColaSeConserva() = runBlocking {
        val outbox = outbox(FakeTransport { null })
        outbox.record(PartnerEventType.PANIC)
        assertIs<ApiResult.Failure>(outbox.flush())
        assertEquals(1, events.events.size)
    }

    @Test fun unLoteMalFormadoSeDescartaParaNoBloquearLaCola() = runBlocking {
        val outbox = outbox(FakeTransport { json(400, """{"error":"Datos no válidos"}""") })
        outbox.record(PartnerEventType.PANIC)
        assertIs<ApiResult.Failure>(outbox.flush())
        assertTrue(events.events.isEmpty())
    }

    @Test fun unSucesoQueElServidorNoConoceNoSeLlevaLosDemas() = runBlocking {
        // Un servidor viejo rechaza el lote entero si lleva un tipo que no conoce; los demás sucesos deben llegar igualmente.
        val transport = FakeTransport { request ->
            val body = request.bodyText()
            if ("icon_changed" in body) json(400, """{"error":"Datos no válidos"}""") else json(202, """{"ok":true}""")
        }
        val outbox = outbox(transport)
        outbox.record(PartnerEventType.PANIC)
        outbox.record(PartnerEventType.ICON_CHANGED, "Calculadora")
        outbox.record(PartnerEventType.PROTECTION_OFF, "accesibilidad")
        outbox.flush()
        assertTrue(events.events.isEmpty())
        val delivered = transport.requests.filter { "icon_changed" !in it.bodyText() }.joinToString { it.bodyText() }
        assertTrue("panic" in delivered && "protection_off" in delivered)
    }

    @Test fun siFallaLaRedAMitadDelUnoAUnoLoNoEnviadoSeQueda() = runBlocking {
        var sent = 0
        val transport = FakeTransport { request ->
            val body = request.bodyText()
            when {
                "icon_changed" in body && "panic" in body -> json(400, """{"error":"x"}""")
                "panic" in body -> { sent++; json(202, """{"ok":true}""") }
                else -> null
            }
        }
        val outbox = outbox(transport)
        outbox.record(PartnerEventType.PANIC)
        outbox.record(PartnerEventType.ICON_CHANGED, "Notas")
        outbox.record(PartnerEventType.PROTECTION_OFF)
        assertIs<ApiResult.Failure>(outbox.flush())
        assertEquals(1, sent)
        // El grave que ya salió no se repite; lo que se quedó sin enviar sigue en la cola.
        assertTrue(events.events.none { it.type == PartnerEventType.PANIC })
        assertTrue(events.events.any { it.type == PartnerEventType.PROTECTION_OFF })
    }

    @Test fun conMasDeUnLoteSeEnvianTodos() = runBlocking {
        val transport = FakeTransport { json(202, """{"ok":true}""") }
        val outbox = outbox(transport)
        repeat(60) { outbox.record(PartnerEventType.PANIC) }
        outbox.flush()
        assertEquals(2, transport.requests.size)
        assertTrue(events.events.isEmpty())
    }

    @Test fun loGuardadoAntesDeApagarLosAvisosNoSale() = runBlocking {
        val transport = FakeTransport { json(202, """{"ok":true}""") }
        val outbox = outbox(transport)
        outbox.record(PartnerEventType.BLOCKED, "com.example")
        noticesOn = false
        assertIs<ApiResult.Ok<Unit>>(outbox.flush())
        assertTrue(transport.requests.isEmpty())
        assertTrue(events.events.isEmpty())
    }

    @Test fun unErrorQueNoEsDeContenidoNoDescartaNada() = runBlocking {
        val outbox = outbox(FakeTransport { json(404, "<html>no hay ruta</html>") })
        outbox.record(PartnerEventType.PANIC)
        assertIs<ApiResult.Failure>(outbox.flush())
        assertEquals(1, events.events.size)
    }

    @Test fun alCerrarSesionLoPendienteSeBorra() {
        val outbox = outbox(FakeTransport { null })
        outbox.record(PartnerEventType.PANIC)
        outbox.clear()
        assertTrue(events.events.isEmpty())
    }

    @Test fun elLatidoSoloSaleConCompanieroActivo() = runBlocking {
        val withPartner = FakeTransport { json(200, """{"ok":true}""") }
        outbox(withPartner).heartbeat()
        assertEquals("/heartbeat", withPartner.paths().single())
        val without = FakeTransport { json(200, """{"ok":true}""") }
        outbox(without, PartnerState.None).heartbeat()
        assertTrue(without.requests.isEmpty())
    }

    @Test fun elLatidoSigueConLosAvisosApagados() = runBlocking {
        noticesOn = false
        val transport = FakeTransport { json(200, """{"ok":true}""") }
        outbox(transport).heartbeat()
        assertEquals(1, transport.requests.size)
    }
}
