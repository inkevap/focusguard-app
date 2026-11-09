package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.ForegroundState
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PanicTest {

    private val now = Instant.parse("2026-10-26T10:00:00Z")
    private val panic = Profile.panic(now.plusSeconds(30 * 60)).copy(id = 1)
    private val decider = BlockDecider(neverBlock = setOf("com.android.dialer"), zone = ZoneOffset.UTC)

    @Test
    fun elPanicBloqueaTodoMenosLasExcepciones() {
        assertTrue(decider.decide(ForegroundState("com.instagram.android"), listOf(panic), now) is BlockDecision.Block)
        assertTrue(decider.decide(ForegroundState("com.android.chrome", url = "https://example.com", isBrowser = true), listOf(panic), now) is BlockDecision.Block)
        assertEquals(BlockDecision.Allow, decider.decide(ForegroundState("com.android.dialer"), listOf(panic), now))
    }

    @Test
    fun alCaducarYaNoBloqueaYSeDescarta() {
        val after = now.plusSeconds(31 * 60)
        assertEquals(BlockDecision.Allow, decider.decide(ForegroundState("com.instagram.android"), listOf(panic), after))
        assertTrue(ProfileActivation.isExpired(panic, after))
    }

    @Test
    fun elPanicSoloCuentaMientrasDura() {
        val normal = Profile(2, "normal")
        assertTrue(ProfileActivation.isPanicActive(listOf(normal, panic), now))
        assertFalse(ProfileActivation.isPanicActive(listOf(normal), now))
        assertFalse(ProfileActivation.isPanicActive(listOf(panic), now.plusSeconds(31 * 60)))
    }

    @Test
    fun soloElPanicNoSePuedeTerminarAntesDeTiempo() {
        assertFalse(panic.canEndEarly)
        assertTrue(panic.copy(kind = ProfileKind.QUICK_BLOCK).canEndEarly)
        assertTrue(Profile(2, "normal").canEndEarly)
    }

    @Test
    fun elBloqueoSinSalidaEnCursoSeEncuentra() {
        val normal = Profile(id = 2, title = "x")
        assertEquals(panic, ProfileActivation.activePanic(listOf(normal, panic), now))
        assertNull(ProfileActivation.activePanic(listOf(normal), now))
        assertNull(ProfileActivation.activePanic(listOf(panic), now.plusSeconds(31 * 60)))
    }
}
