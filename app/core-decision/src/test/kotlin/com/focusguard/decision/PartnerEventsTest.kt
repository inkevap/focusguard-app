package com.focusguard.decision

import com.focusguard.model.BlockReason
import com.focusguard.model.GuardTargets
import com.focusguard.model.PartnerEvent
import com.focusguard.model.PartnerEventType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartnerEventsTest {
    private val t0 = Instant.parse("2026-01-01T10:00:00Z")
    private fun blocked(label: String, at: Instant = t0) = PartnerEvent(PartnerEventType.BLOCKED, at, label)

    @Test fun losAjustesProtegidosSonIntentosDeApagarLaProteccion() {
        assertEquals(PartnerEventType.SETTINGS_ATTEMPT, PartnerEvents.classify(BlockReason.STRICT_MODE, "ajustes"))
    }

    @Test fun intentarDesinstalarEsOtroSuceso() {
        assertEquals(PartnerEventType.UNINSTALL_ATTEMPT, PartnerEvents.classify(BlockReason.STRICT_MODE, GuardTargets.UNINSTALL))
    }

    @Test fun otrasPantallasDelSistemaNoSeCuentan() {
        assertNull(PartnerEvents.classify(BlockReason.STRICT_MODE, "apps recientes"))
        assertNull(PartnerEvents.classify(BlockReason.CLOCK_PROTECTED, "fecha y hora"))
        assertEquals(PartnerEventType.UNINSTALL_ATTEMPT, PartnerEvents.classify(BlockReason.ADMIN_PROTECTED, GuardTargets.ADMIN))
    }

    @Test fun losBloqueosDeUnPerfilSonBloqueos() {
        assertEquals(PartnerEventType.BLOCKED, PartnerEvents.classify(BlockReason.PROFILE_APP, "com.example"))
        assertEquals(PartnerEventType.BLOCKED, PartnerEvents.classify(BlockReason.LIMIT_REACHED, "com.example"))
    }

    @Test fun soloLosBloqueosEsperan() {
        assertTrue(!PartnerEvents.urgent(PartnerEventType.BLOCKED))
        assertTrue(PartnerEvents.urgent(PartnerEventType.PANIC))
        assertTrue(PartnerEvents.urgent(PartnerEventType.SETTINGS_ATTEMPT))
    }

    @Test fun unBloqueoRepetidoSeCuentaUnaVezCadaCincoMinutos() {
        var queue = PartnerEvents.enqueue(emptyList(), blocked("a"))
        queue = PartnerEvents.enqueue(queue, blocked("a", t0.plusSeconds(60)))
        assertEquals(1, queue.size)
        queue = PartnerEvents.enqueue(queue, blocked("b", t0.plusSeconds(60)))
        assertEquals(2, queue.size)
        queue = PartnerEvents.enqueue(queue, blocked("a", t0.plusSeconds(6 * 60)))
        assertEquals(3, queue.size)
    }

    @Test fun losSucesosGravesNoSeAgrupan() {
        val panic = PartnerEvent(PartnerEventType.PANIC, t0)
        assertEquals(2, PartnerEvents.enqueue(PartnerEvents.enqueue(emptyList(), panic), panic).size)
    }

    @Test fun losIntentosRepetidosEnLosAjustesSeAgrupanDosMinutos() {
        val attempt = { at: Instant -> PartnerEvent(PartnerEventType.SETTINGS_ATTEMPT, at, "ajustes") }
        var queue = PartnerEvents.enqueue(emptyList(), attempt(t0))
        queue = PartnerEvents.enqueue(queue, attempt(t0.plusSeconds(30)))
        assertEquals(1, queue.size)
        queue = PartnerEvents.enqueue(queue, attempt(t0.plusSeconds(150)))
        assertEquals(2, queue.size)
    }

    @Test fun laEtiquetaSeRecorta() {
        val queue = PartnerEvents.enqueue(emptyList(), blocked("x".repeat(500)))
        assertEquals(PartnerEvents.MAX_LABEL, queue.single().label!!.length)
    }

    @Test fun conLaColaLlenaSeTiranPrimeroLosBloqueos() {
        val grave = PartnerEvent(PartnerEventType.PANIC, t0)
        var queue = listOf(grave)
        repeat(PartnerEvents.MAX_QUEUED) { queue = PartnerEvents.enqueue(queue, blocked("app$it", t0.plusSeconds(it * 1000L))) }
        assertEquals(PartnerEvents.MAX_QUEUED, queue.size)
        assertTrue(grave in queue)
        assertEquals(PartnerEvents.MAX_BATCH, PartnerEvents.nextBatch(queue).size)
    }
}
