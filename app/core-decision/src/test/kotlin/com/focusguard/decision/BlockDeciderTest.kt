package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockMode
import com.focusguard.model.BlockReason
import com.focusguard.model.ForegroundState
import com.focusguard.model.Profile
import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockDeciderTest {

    private val now = Instant.parse("2026-10-26T12:00:00Z")
    private val decider = BlockDecider(neverBlock = setOf("com.focusguard", "com.android.dialer"), zone = ZoneOffset.UTC)

    private fun decide(state: ForegroundState, vararg profiles: Profile) =
        decider.decide(state, profiles.toList(), now)

    @Test
    fun elTemporizadorPermiteSoloLasAppsElegidasYAyudameNinguna() {
        val timer = Profile.panic(now.plusSeconds(1800), "Temporizador", allowed = setOf("com.anki"))
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.anki"), timer.copy(id = 1)))
        assertEquals(BlockDecision.Block(BlockReason.NOT_ALLOWED, 1, "com.otra"), decide(ForegroundState("com.otra"), timer.copy(id = 1)))
        // Ayúdame es lo mismo sin permitidas: ni esa app se salva.
        val panic = Profile.panic(now.plusSeconds(1800)).copy(id = 2)
        assertEquals(BlockDecision.Block(BlockReason.NOT_ALLOWED, 2, "com.anki"), decide(ForegroundState("com.anki"), panic))
        // Las llamadas y la propia app nunca se bloquean.
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.android.dialer"), panic))
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.focusguard"), panic))
    }

    @Test
    fun sinPerfilesTodoSePermite() {
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.x")))
    }

    @Test
    fun listaDeBloqueoBloqueaLaAppElegida() {
        val p = Profile(1, "p", apps = setOf("com.x"))
        assertEquals(
            BlockDecision.Block(BlockReason.PROFILE_APP, 1, "com.x"),
            decide(ForegroundState("com.x"), p),
        )
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.y"), p))
    }

    @Test
    fun variosPerfilesQueBloqueanLoMismoSeNombranTodos() {
        val a = Profile(2, "a", apps = setOf("com.x"))
        val b = Profile(1, "b", apps = setOf("com.x"))
        val c = Profile(3, "c", apps = setOf("com.otra"))
        val d = Profile(4, "d", apps = setOf("com.x"))
        assertEquals(
            BlockDecision.Block(BlockReason.PROFILE_APP, 1, "com.x", otherProfileIds = listOf(2, 4)),
            decide(ForegroundState("com.x"), a, b, c, d),
        )
    }

    @Test
    fun unPerfilQueNoBloqueaLoDeAhoraNoSeNombra() {
        val activo = Profile(1, "a", apps = setOf("com.x"))
        val apagado = Profile(2, "b", enabled = false, apps = setOf("com.x"))
        assertEquals(BlockDecision.Block(BlockReason.PROFILE_APP, 1, "com.x"), decide(ForegroundState("com.x"), activo, apagado))
    }

    @Test
    fun listaDeBloqueoBloqueaLaWebEnUnNavegador() {
        val p = Profile(1, "p", webs = listOf(WebRule("reddit.com")))
        val state = ForegroundState("com.android.chrome", "https://www.reddit.com/r/a", isBrowser = true)
        assertEquals(BlockDecision.Block(BlockReason.PROFILE_WEB, 1, "reddit.com"), decide(state, p))
    }

    @Test
    fun excepcionesNuncaSeBloqueanAunqueEstenEnLaLista() {
        val p = Profile(1, "p", apps = setOf("com.focusguard", "com.android.dialer"))
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.focusguard"), p))
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.android.dialer"), p))
    }

    @Test
    fun excepcionesNuncaSeBloqueanEnModoPermitirSoloEsto() {
        val p = Profile(1, "p", mode = BlockMode.ALLOW_ONLY, apps = setOf("com.notes"))
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.android.dialer"), p))
    }

    @Test
    fun permitirSoloEstoBloqueaTodoLoDemas() {
        val p = Profile(1, "p", mode = BlockMode.ALLOW_ONLY, apps = setOf("com.notes"))
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.notes"), p))
        assertEquals(
            BlockDecision.Block(BlockReason.NOT_ALLOWED, 1, "com.other"),
            decide(ForegroundState("com.other"), p),
        )
    }

    @Test
    fun permitirSoloEstoDejaPasarUnaWebPermitidaEnElNavegador() {
        val p = Profile(1, "p", mode = BlockMode.ALLOW_ONLY, webs = listOf(WebRule("wikipedia.org")))
        val allowed = ForegroundState("com.android.chrome", "es.wikipedia.org/wiki/Kotlin", isBrowser = true)
        val blocked = ForegroundState("com.android.chrome", "youtube.com", isBrowser = true)
        assertEquals(BlockDecision.Allow, decide(allowed, p))
        assertEquals(BlockDecision.Block(BlockReason.NOT_ALLOWED, 1, "youtube.com"), decide(blocked, p))
    }

    @Test
    fun permitirSoloEstoNoBloqueaElNavegadorMientrasNoHayaDireccion() {
        val p = Profile(1, "p", mode = BlockMode.ALLOW_ONLY, webs = listOf(WebRule("wikipedia.org")))
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.android.chrome", null, isBrowser = true), p))
    }

    @Test
    fun soloCuentanLosPerfilesActivos() {
        val inactivo = Profile(1, "p", enabled = false, apps = setOf("com.x"))
        val pausado = Profile(2, "q", apps = setOf("com.x"), pausedUntil = now.plusSeconds(60))
        assertEquals(BlockDecision.Allow, decide(ForegroundState("com.x"), inactivo, pausado))
    }

    @Test
    fun siUnPerfilBloqueaGanaAunqueOtroPermita() {
        val permite = Profile(1, "a", mode = BlockMode.ALLOW_ONLY, apps = setOf("com.x"))
        val bloquea = Profile(2, "b", apps = setOf("com.x"))
        assertEquals(
            BlockDecision.Block(BlockReason.PROFILE_APP, 2, "com.x"),
            decide(ForegroundState("com.x"), permite, bloquea),
        )
    }

    @Test
    fun contieneAplicaSobreLaUrlCompleta() {
        val p = Profile(1, "p", webs = listOf(WebRule("shorts", WebMatchType.CONTAINS)))
        val state = ForegroundState("com.android.chrome", "youtube.com/shorts/abc", isBrowser = true)
        assertEquals(BlockDecision.Block(BlockReason.PROFILE_WEB, 1, "shorts"), decide(state, p))
    }
}
