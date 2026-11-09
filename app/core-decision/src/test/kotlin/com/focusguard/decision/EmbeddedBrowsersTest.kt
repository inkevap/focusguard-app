package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockReason
import com.focusguard.model.ForegroundState
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmbeddedBrowsersTest {

    private val chosen = EmbeddedBrowserPolicy(enabled = true, apps = setOf("com.a", "com.b"))

    @Test
    fun seAplicaALasAppsElegidasOATodasSiEstaActivado() {
        assertTrue(chosen.applies("com.a"))
        assertFalse(chosen.applies("com.c"))
        assertTrue(chosen.copy(allApps = true).applies("com.c"))
        assertFalse(chosen.copy(enabled = false).applies("com.a"))
    }

    @Test
    fun elTextoDeLaSolicitudVuelveIgual() {
        assertEquals(chosen, EmbeddedBrowserPolicy.decode(chosen.encode()))
        val all = EmbeddedBrowserPolicy(enabled = true, allApps = true)
        assertEquals(all, EmbeddedBrowserPolicy.decode(all.encode()))
        assertNull(EmbeddedBrowserPolicy.decode("basura"))
        assertNull(EmbeddedBrowserPolicy.decode("si,no|a"))
    }

    @Test
    fun apagarQuitarAppsOPasarDeTodasAElegidasDebilita() {
        assertTrue(Weakening.embeddedBrowsersWeakened(chosen, chosen.copy(enabled = false)))
        assertTrue(Weakening.embeddedBrowsersWeakened(chosen, chosen.copy(apps = setOf("com.a"))))
        assertTrue(Weakening.embeddedBrowsersWeakened(chosen.copy(allApps = true), chosen))
        assertFalse(Weakening.embeddedBrowsersWeakened(chosen, chosen.copy(apps = chosen.apps + "com.c")))
        assertFalse(Weakening.embeddedBrowsersWeakened(chosen, chosen.copy(allApps = true)))
        assertFalse(Weakening.embeddedBrowsersWeakened(EmbeddedBrowserPolicy(), chosen))
    }

    @Test
    fun elDecisorBloqueaLaAppConVistaWebYRespetaExcepciones() {
        val decider = BlockDecider(neverBlock = setOf("com.focusguard"), zone = ZoneOffset.UTC)
        val now = Instant.parse("2026-10-27T12:00:00Z")
        val block = decider.decide(ForegroundState("com.a"), emptyList(), now, webViewApp = { true })
        assertEquals(BlockDecision.Block(BlockReason.EMBEDDED_BROWSER, 0, "com.a"), block)
        assertEquals(BlockDecision.Allow, decider.decide(ForegroundState("com.a"), emptyList(), now))
        assertEquals(BlockDecision.Allow, decider.decide(ForegroundState("com.focusguard"), emptyList(), now, webViewApp = { error("no debe preguntarse") }))
    }

    @Test
    fun laVistaWebSoloSePreguntaSiNingunPerfilBloqueaYaLaApp() {
        val decider = BlockDecider(neverBlock = emptySet(), zone = ZoneOffset.UTC)
        val now = Instant.parse("2026-10-27T12:00:00Z")
        val blocked = com.focusguard.model.Profile(1, "p", apps = setOf("com.a"))
        val byProfile = decider.decide(ForegroundState("com.a"), listOf(blocked), now, webViewApp = { error("no debe preguntarse") })
        assertEquals(BlockReason.PROFILE_APP, (byProfile as BlockDecision.Block).reason)
        var asked = 0
        decider.decide(ForegroundState("com.b"), listOf(blocked), now, webViewApp = { asked++; false })
        assertEquals(1, asked)
    }
}
