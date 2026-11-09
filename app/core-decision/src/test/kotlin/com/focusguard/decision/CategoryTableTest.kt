package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockMode
import com.focusguard.model.BlockReason
import com.focusguard.model.ForegroundState
import com.focusguard.model.Profile
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryTableTest {

    private val table = CategoryTable(
        packages = mapOf("com.instagram.android" to "social", "com.netflix.mediaclient" to "video"),
        domains = mapOf("instagram.com" to "social", "netflix.com" to "video"),
    )
    private val now = Instant.parse("2026-10-27T10:00:00Z")
    private val decider = BlockDecider(neverBlock = emptySet(), zone = ZoneOffset.UTC, categories = table)

    @Test
    fun reconoceLaCategoriaDeUnaWebYDeSusSubdominios() {
        assertEquals("social", table.ofUrl("https://www.instagram.com/explore"))
        assertEquals("social", table.ofUrl("m.instagram.com"))
        assertNull(table.ofUrl("https://notinstagram.com"))
        assertNull(table.ofUrl("com"))
    }

    @Test
    fun bloqueaLasAppsYLasWebsDeLaCategoriaElegida() {
        val profile = Profile(1, "sin redes", categories = setOf("social"))
        val app = decider.decide(ForegroundState("com.instagram.android"), listOf(profile), now) as BlockDecision.Block
        assertEquals(BlockReason.PROFILE_APP, app.reason)
        val web = decider.decide(ForegroundState("com.android.chrome", url = "https://www.instagram.com/", isBrowser = true), listOf(profile), now) as BlockDecision.Block
        assertEquals(BlockReason.PROFILE_WEB, web.reason)
        assertEquals("instagram.com", web.target)
        assertEquals(BlockDecision.Allow, decider.decide(ForegroundState("com.netflix.mediaclient"), listOf(profile), now))
    }

    @Test
    fun sinTablaLasCategoriasNoBloqueanNada() {
        val plain = BlockDecider(neverBlock = emptySet(), zone = ZoneOffset.UTC)
        val profile = Profile(1, "p", categories = setOf("social"))
        assertEquals(BlockDecision.Allow, plain.decide(ForegroundState("com.instagram.android"), listOf(profile), now))
    }

    @Test
    fun cuentaComoUsoParaLosLimites() {
        val profile = Profile(1, "p", categories = setOf("video"))
        assertTrue(UsageTargets.isUsing(profile, ForegroundState("com.netflix.mediaclient"), emptySet(), table))
        assertFalse(UsageTargets.isUsing(profile, ForegroundState("com.instagram.android"), emptySet(), table))
        assertFalse(UsageTargets.isUsing(profile, ForegroundState("com.netflix.mediaclient"), emptySet()))
    }

    @Test
    fun quitarUnaCategoriaDebilitaYAnadirlaNo() {
        val old = Profile(1, "p", categories = setOf("social", "video"))
        assertEquals(listOf(Weakening.ACTION_CATEGORY), Weakening.of(old, old.copy(categories = setOf("social"))))
        assertTrue(Weakening.of(old, old.copy(categories = old.categories + "games")).isEmpty())
    }

    @Test
    fun enPermitirSoloEstoLasCategoriasNoCuentan() {
        val allowOnly = Profile(1, "p", mode = BlockMode.ALLOW_ONLY, categories = setOf("social"))
        assertNull(ProfileTargets.listed(allowOnly, ForegroundState("com.instagram.android"), table))
        assertTrue(Weakening.of(allowOnly, allowOnly.copy(categories = emptySet())).isEmpty())
    }
}
