package com.focusguard.decision

import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalProgress
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.ApprovalStatus
import com.focusguard.model.RemoteStatus
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApprovalsTest {

    private val created = Instant.parse("2026-10-26T10:00:00Z")
    private fun request(mode: ApprovalMode, phraseIndex: Int = 0) =
        ApprovalRequest(1, "adult_off", Approvals.ACTION_ADULT_BLOCK, mode, created, phraseIndex)
    private fun evaluate(r: ApprovalRequest, progress: ApprovalProgress = ApprovalProgress(), at: Instant = created) =
        Approvals.evaluate(r, progress, at)

    @Test
    fun sinModoEsInmediato() {
        assertEquals(ApprovalStatus.Ready, evaluate(request(ApprovalMode.NONE)))
    }

    @Test
    fun retardoDe24HoEsperaHastaCumplirlas() {
        val r = request(ApprovalMode.DELAY_24H)
        val ready = created.plusSeconds(24 * 3600)
        assertEquals(ApprovalStatus.WaitingUntil(ready), evaluate(r, at = ready.minusSeconds(1)))
        assertEquals(ApprovalStatus.Ready, evaluate(r, at = ready))
    }

    @Test
    fun retardoDe72HoEsperaTresDias() {
        val r = request(ApprovalMode.DELAY_72H)
        assertEquals(ApprovalStatus.WaitingUntil(created.plusSeconds(72 * 3600)), evaluate(r, at = created.plusSeconds(48 * 3600)))
        assertEquals(ApprovalStatus.Ready, evaluate(r, at = created.plusSeconds(72 * 3600)))
    }

    @Test
    fun fraseExigeElTextoExactoSalvoEspacios() {
        val r = request(ApprovalMode.LONG_PHRASE, phraseIndex = 2)
        val phrase = Approvals.phrase(2)
        assertEquals(ApprovalStatus.NeedPhrase, evaluate(r, ApprovalProgress(typed = "")))
        assertEquals(ApprovalStatus.Ready, evaluate(r, ApprovalProgress(typed = "  " + phrase.replace(" ", "  ") + " ")))
        assertEquals(ApprovalStatus.NeedPhrase, evaluate(r, ApprovalProgress(typed = phrase.dropLast(1))))
        assertEquals(ApprovalStatus.NeedPhrase, evaluate(r, ApprovalProgress(typed = phrase.lowercase())))
    }

    @Test
    fun laFraseSeEligeCiclicamentePorIndice() {
        assertEquals(Approvals.phrase(0), Approvals.phrase(Approvals.PHRASES.size))
        assertEquals(Approvals.phrase(1), Approvals.phrase(-Approvals.PHRASES.size + 1))
    }

    @Test
    fun todasLasFrasesSonLargasYDistintas() {
        assertTrue(Approvals.PHRASES.all { it.length >= 150 })
        assertEquals(Approvals.PHRASES.size, Approvals.PHRASES.toSet().size)
    }

    @Test
    fun pasosNecesitanLaCantidadPedida() {
        val r = request(ApprovalMode.STEPS)
        assertEquals(ApprovalStatus.Walking(100, 250), evaluate(r, ApprovalProgress(steps = 100)))
        assertEquals(ApprovalStatus.Ready, evaluate(r, ApprovalProgress(steps = 250)))
        assertEquals(ApprovalStatus.Walking(0, 250), evaluate(r, ApprovalProgress(steps = -5)))
    }

    @Test
    fun contadorDePasosToleraReinicios() {
        assertEquals(40, Approvals.stepsSince(baseline = 1_000, current = 1_040))
        assertEquals(0, Approvals.stepsSince(baseline = 1_000, current = 1_000))
        assertEquals(15, Approvals.stepsSince(baseline = 1_000, current = 15)) // reinicio del teléfono
    }


    @Test
    fun soloSeReutilizaUnaSolicitudIdentica() {
        val pending = listOf(request(ApprovalMode.DELAY_72H).copy(payload = "cambio pequeño"))
        assertEquals(pending[0], Approvals.reusable(pending, "adult_off", "cambio pequeño"))
        // Otro contenido bajo la misma clave no hereda la espera ya cumplida.
        assertEquals(null, Approvals.reusable(pending, "adult_off", "cambio enorme"))
        assertEquals(null, Approvals.reusable(pending, "otra_clave", "cambio pequeño"))
    }

    @Test
    fun conCompañeroSoloEstaListoCuandoApruebaYAvisaDeCadaEstado() {
        fun partner(remote: RemoteStatus) = request(ApprovalMode.PARTNER).copy(remote = remote)
        assertEquals(ApprovalStatus.WaitingPartner(sent = false), evaluate(partner(RemoteStatus.NOT_SENT)))
        assertEquals(ApprovalStatus.WaitingPartner(sent = true), evaluate(partner(RemoteStatus.WAITING)))
        assertEquals(ApprovalStatus.Ready, evaluate(partner(RemoteStatus.APPROVED)))
        assertEquals(ApprovalStatus.PartnerRejected, evaluate(partner(RemoteStatus.REJECTED)))
        assertEquals(ApprovalStatus.PartnerExpired, evaluate(partner(RemoteStatus.EXPIRED)))
    }

    @Test
    fun conCompañeroNadaLocalLoAdelanta() {
        val waiting = request(ApprovalMode.PARTNER).copy(remote = RemoteStatus.WAITING)
        val later = created.plusSeconds(10 * 24 * 3600)
        val progress = ApprovalProgress(typed = Approvals.phrase(0), steps = 10_000)
        assertEquals(ApprovalStatus.WaitingPartner(sent = true), evaluate(waiting, progress, later))
    }

    @Test
    fun describeDaUnTextoParaCadaAccionYUnoGenericoParaLasDesconocidas() {
        assertEquals("Apagar el bloqueo de contenido adulto", Approvals.describe("adult_off"))
        assertEquals("Apagar un perfil", Approvals.describe("profile_off:7"))
        assertTrue(Approvals.describe("sign_out").contains("sesión"))
        assertEquals("Debilitar una protección", Approvals.describe("algo_nuevo"))
        // El texto va al compañero: no puede llevar el contenido del cambio.
        assertTrue(!Approvals.describe("profile_edit:7").contains("7"))
    }
}
