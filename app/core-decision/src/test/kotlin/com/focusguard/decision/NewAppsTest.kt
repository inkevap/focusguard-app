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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NewAppsTest {

    private val now = Instant.parse("2026-10-27T12:00:00Z")
    private val on = NewAppsPolicy(enabled = true, hours = 24)
    private fun installedAgo(hours: Long) = now.minus(Duration.ofHours(hours)).toEpochMilli()

    @Test
    fun unaAppRecienInstaladaQuedaBloqueadaHastaCumplirseElPlazo() {
        assertEquals(now.plus(Duration.ofHours(19)), NewApps.blockedUntil(on, installedAgo(5), now))
        assertNull(NewApps.blockedUntil(on, installedAgo(24), now))
        assertNull(NewApps.blockedUntil(on, installedAgo(100), now))
    }

    @Test
    fun apagadoOSinFechaDeInstalacionNoBloquea() {
        assertNull(NewApps.blockedUntil(NewAppsPolicy(), installedAgo(1), now))
        assertNull(NewApps.blockedUntil(on, null, now))
    }

    @Test
    fun apagarloOAcortarLasHorasDebilita() {
        assertTrue(Weakening.newAppsWeakened(on, NewAppsPolicy()))
        assertTrue(Weakening.newAppsWeakened(on, on.copy(hours = 12)))
        assertFalse(Weakening.newAppsWeakened(on, on.copy(hours = 48)))
        assertFalse(Weakening.newAppsWeakened(NewAppsPolicy(), on))
        assertFalse(Weakening.newAppsWeakened(NewAppsPolicy(), NewAppsPolicy()))
    }

    @Test
    fun elTextoDeLaSolicitudConservaLasHorasAunqueEsteApagado() {
        assertEquals(on.copy(hours = 72), NewAppsPolicy.decode(on.copy(hours = 72).encode()))
        assertEquals(NewAppsPolicy(false, 72), NewAppsPolicy.decode(NewAppsPolicy(false, 72).encode()))
        assertNull(NewAppsPolicy.decode("true:24"))
        assertNull(NewAppsPolicy.decode("0"))
        assertNull(NewAppsPolicy.decode("100000"))
    }

    @Test
    fun elDecisorBloqueaLaAppNuevaConLaHoraDeDesbloqueoYRespetaExcepcionesYPausa() {
        val decider = BlockDecider(neverBlock = setOf("com.focusguard"), zone = ZoneOffset.UTC)
        val until = now.plus(Duration.ofHours(3))
        val state = ForegroundState("com.nueva")
        val block = decider.decide(state, emptyList(), now, newAppUntil = until)
        assertEquals(BlockDecision.Block(BlockReason.NEW_APP, 0, "com.nueva", until), block)
        assertEquals(BlockDecision.Allow, decider.decide(ForegroundState("com.focusguard"), emptyList(), now, newAppUntil = until))
        assertEquals(BlockDecision.Allow, decider.decide(state, emptyList(), now, newAppUntil = until, pausedUntil = now.plusSeconds(60)))
        assertEquals(BlockDecision.Allow, decider.decide(state, listOf(Profile(1, "p")), now))
    }
}
