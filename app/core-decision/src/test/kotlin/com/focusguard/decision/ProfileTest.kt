package com.focusguard.decision

import com.focusguard.model.BlockMode
import com.focusguard.model.Profile
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileTest {

    private val now = Instant.parse("2026-10-27T10:00:00Z")

    @Test
    fun unPerfilNuevoBloqueaPorDefecto() {
        assertEquals(BlockMode.BLOCK, Profile(1, "p").mode)
    }

    @Test
    fun unaEdicionAprobadaAplicaTodosLosCamposEditablesYConservaElEstado() {
        val current = Profile(7, "viejo", enabled = false, pausedUntil = now)
        val edited = Profile(
            7, "nuevo", emoji = "x", apps = setOf("a"), categories = setOf("social"), subApps = setOf("YOUTUBE_SHORTS"),
            wifiNames = setOf("Casa"), requireCharging = true, enabled = true,
        )
        val applied = current.withEditableFieldsFrom(edited)
        assertEquals(setOf("social"), applied.categories)
        assertEquals(setOf("YOUTUBE_SHORTS"), applied.subApps)
        assertEquals(setOf("Casa"), applied.wifiNames)
        assertTrue(applied.requireCharging)
        assertEquals("nuevo", applied.title)
        assertFalse(applied.enabled)
        assertEquals(now, applied.pausedUntil)
    }
}
