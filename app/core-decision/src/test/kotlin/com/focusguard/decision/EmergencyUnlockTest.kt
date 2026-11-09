package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockReason
import com.focusguard.model.ForegroundState
import com.focusguard.model.Profile
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmergencyUnlockTest {

    private val now = Instant.parse("2026-10-27T10:00:00Z")
    private val config = EmergencyConfig(perWeek = 3, minutes = 5)

    private fun availability(grants: List<Instant>, strict: Boolean = false, panic: Boolean = false) =
        EmergencyUnlock.availability(grants, now, config, strict, panic)

    @Test
    fun sinUsosQuedaElCupoCompleto() {
        assertEquals(EmergencyAvailability.Available(3), availability(emptyList()))
    }

    @Test
    fun losUsosViejosDejanDeContarPasadaUnaSemana() {
        val grants = listOf(now - Duration.ofDays(8), now - Duration.ofDays(1))
        assertEquals(EmergencyAvailability.Available(2), availability(grants))
    }

    @Test
    fun conElCupoGastadoDiceCuandoSeLiberaUnUso() {
        val oldest = now - Duration.ofDays(6)
        val grants = listOf(now - Duration.ofHours(1), oldest, now - Duration.ofDays(2))
        assertEquals(EmergencyAvailability.QuotaSpent(oldest + Duration.ofDays(7)), availability(grants))
    }

    @Test
    fun unUsoConFechaFuturaTambienCuenta() {
        val grants = listOf(now + Duration.ofDays(1), now - Duration.ofHours(2), now - Duration.ofHours(1))
        assertTrue(availability(grants) is EmergencyAvailability.QuotaSpent)
    }

    @Test
    fun conElModoEstrictoOElPanicNoSePuedePedir() {
        assertEquals(EmergencyAvailability.ByStrictMode, availability(emptyList(), strict = true))
        assertEquals(EmergencyAvailability.ByPanic, availability(emptyList(), panic = true))
    }

    @Test
    fun cadaPasoExigeLoSuyoYLaFraseDebeEscribirseIgual() {
        assertFalse(EmergencyUnlock.canAdvance(EmergencyStep.WHY, "poco"))
        assertTrue(EmergencyUnlock.canAdvance(EmergencyStep.WHY, "Me bloqueé para estudiar mejor"))
        val spaced = "  ${EmergencyUnlock.COMMITMENT.replace(" ", "  ")} "
        assertTrue(EmergencyUnlock.canAdvance(EmergencyStep.PLEDGE, spaced))
        assertFalse(EmergencyUnlock.canAdvance(EmergencyStep.PLEDGE, "me comprometo"))
        assertTrue(EmergencyUnlock.canAdvance(EmergencyStep.INTRO, ""))
    }

    @Test
    fun soloSeOfreceEnLosBloqueosDeUnPerfil() {
        assertTrue(BlockReason.PROFILE_APP in EmergencyUnlock.ELIGIBLE_REASONS)
        assertFalse(BlockReason.STRICT_MODE in EmergencyUnlock.ELIGIBLE_REASONS)
        assertFalse(BlockReason.CLOCK_PROTECTED in EmergencyUnlock.ELIGIBLE_REASONS)
    }

    @Test
    fun losPasosAvanzanEnOrdenYTerminanEnDone() {
        assertEquals(EmergencyStep.WHY, EmergencyUnlock.next(EmergencyStep.INTRO))
        assertEquals(EmergencyStep.DONE, EmergencyUnlock.next(EmergencyStep.WAIT))
        assertEquals(EmergencyStep.DONE, EmergencyUnlock.next(EmergencyStep.DONE))
    }

    @Test
    fun laPausaDejaPasarTodoMenosConUnPanicEnCurso() {
        val decider = BlockDecider(neverBlock = emptySet(), zone = ZoneOffset.UTC)
        val state = ForegroundState("com.instagram.android")
        val blocking = Profile(1, "p", apps = setOf("com.instagram.android"))
        val pause = EmergencyUnlock.activeUntil(now, config)
        assertTrue(decider.decide(state, listOf(blocking), now) is BlockDecision.Block)
        assertEquals(BlockDecision.Allow, decider.decide(state, listOf(blocking), now, pausedUntil = pause))
        assertTrue(decider.decide(state, listOf(blocking), pause.plusSeconds(1), pausedUntil = pause) is BlockDecision.Block)
        val panic = Profile.panic(now.plusSeconds(600)).copy(id = 2)
        assertTrue(decider.decide(state, listOf(blocking, panic), now, pausedUntil = pause) is BlockDecision.Block)
    }
}
