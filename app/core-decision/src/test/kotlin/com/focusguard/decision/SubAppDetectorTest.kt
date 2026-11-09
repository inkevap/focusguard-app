package com.focusguard.decision

import com.focusguard.model.BlockMode
import com.focusguard.model.LimitKind
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.SubAppRule
import com.focusguard.model.UsageLimit
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubAppDetectorTest {

    private val shorts = SubAppRule("YOUTUBE_SHORTS", "YouTube Shorts", setOf("com.google.android.youtube"), viewIds = setOf("reel_recycler"))
    private val fbReels = SubAppRule(
        "FACEBOOK_REELS", "Reels de Facebook", setOf("com.facebook.katana"),
        activity = "com.facebook.katana.immersiveactivity.ImmersiveActivity", texts = setOf("Reels"),
    )
    private val detector = SubAppDetector(listOf(shorts, fbReels))

    private fun detect(enabled: Set<String>, pkg: String, activity: String? = null, ids: Set<String> = emptySet(), texts: Set<String> = emptySet()) =
        detector.detect(enabled, pkg, activity, { it in ids }, { it in texts })

    @Test
    fun reconocePorIdentificadorDeVistaConElPaqueteAntepuesto() {
        val ids = setOf("com.google.android.youtube:id/reel_recycler")
        assertEquals(shorts, detect(setOf("YOUTUBE_SHORTS"), "com.google.android.youtube", ids = ids))
        assertNull(detect(setOf("YOUTUBE_SHORTS"), "com.google.android.youtube", ids = setOf("otro:id/reel_recycler")))
    }

    @Test
    fun soloDetectaLoQueElPerfilTieneActivado() {
        val ids = setOf("com.google.android.youtube:id/reel_recycler")
        assertNull(detect(emptySet(), "com.google.android.youtube", ids = ids))
        assertNull(detect(setOf("FACEBOOK_REELS"), "com.google.android.youtube", ids = ids))
    }

    @Test
    fun sinIdentificadoresExigeLaActividadYTodosLosTextos() {
        val activity = "com.facebook.katana.immersiveactivity.ImmersiveActivity"
        assertEquals(fbReels, detect(setOf("FACEBOOK_REELS"), "com.facebook.katana", activity, texts = setOf("Reels")))
        assertNull(detect(setOf("FACEBOOK_REELS"), "com.facebook.katana", "otra.Actividad", texts = setOf("Reels")))
        assertNull(detect(setOf("FACEBOOK_REELS"), "com.facebook.katana", activity))
    }

    @Test
    fun saleDeLasAppsQueNoCubreSinLeerLaPantalla() {
        assertTrue(detector.covers("com.facebook.katana"))
        assertFalse(detector.covers("com.whatsapp"))
    }

    @Test
    fun conDosPerfilesQueCompartenUnaSeccionGanaElDeMenorId() {
        val decider = BlockDecider(neverBlock = emptySet(), zone = ZoneOffset.UTC)
        val now = Instant.parse("2026-10-26T10:00:00Z")
        val a = Profile(5, "a", subApps = setOf("YOUTUBE_SHORTS"))
        val b = Profile(2, "b", subApps = setOf("YOUTUBE_SHORTS", "INSTAGRAM_REELS"))
        assertEquals(mapOf("YOUTUBE_SHORTS" to 2L, "INSTAGRAM_REELS" to 2L), decider.activeSubApps(listOf(a, b), now))
    }

    @Test
    fun unPerfilConLimiteSoloAportaSeccionesConElLimiteAgotado() {
        val decider = BlockDecider(neverBlock = emptySet(), zone = ZoneOffset.UTC)
        val now = Instant.parse("2026-10-26T10:00:00Z")
        val limited = Profile(1, "a", subApps = setOf("YOUTUBE_SHORTS"), limit = UsageLimit(LimitKind.TIME, 30))
        assertTrue(decider.activeSubApps(listOf(limited), now).isEmpty())
    }

    @Test
    fun conDosReglasEnLaMismaAppGanaLaPrimeraQueCoincide() {
        val a = SubAppRule("A", "A", setOf("p"), viewIds = setOf("x"))
        val b = SubAppRule("B", "B", setOf("p"), viewIds = setOf("x", "y"))
        val both = SubAppDetector(listOf(a, b))
        assertEquals(a, both.detect(setOf("A", "B"), "p", null, { it == "p:id/x" }, { false }))
        assertEquals(b, both.detect(setOf("A", "B"), "p", null, { it == "p:id/y" }, { false }))
    }

    @Test
    fun elDecisorSoloActivaLasSeccionesDePerfilesActivos() {
        val decider = BlockDecider(neverBlock = emptySet(), zone = ZoneOffset.UTC)
        val monday10 = Instant.parse("2026-10-26T10:00:00Z")
        val always = Profile(1, "a", subApps = setOf("YOUTUBE_SHORTS"))
        val night = Profile(2, "b", subApps = setOf("FACEBOOK_REELS"), intervals = listOf(ScheduleInterval(DayOfWeek.MONDAY, 22 * 60, 23 * 60)))
        val off = Profile(3, "c", enabled = false, subApps = setOf("INSTAGRAM_REELS"))
        assertEquals(mapOf("YOUTUBE_SHORTS" to 1L), decider.activeSubApps(listOf(always, night, off), monday10))
        assertTrue(decider.activeSubApps(listOf(Profile(4, "d", mode = BlockMode.ALLOW_ONLY)), monday10).isEmpty())
    }
}
