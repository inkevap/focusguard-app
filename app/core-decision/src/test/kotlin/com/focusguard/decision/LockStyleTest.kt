package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockReason
import com.focusguard.model.ForegroundState
import com.focusguard.model.LimitKind
import com.focusguard.model.LockMotivation
import com.focusguard.model.LockPalettes
import com.focusguard.model.LockStyle
import com.focusguard.model.Profile
import com.focusguard.model.ProfileUsage
import com.focusguard.model.StrictProtection
import com.focusguard.model.UsageLimit
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LockStyleTest {

    @Test
    fun elBloqueoPorLimiteDiceCuandoSeReinicia() {
        val decider = BlockDecider(neverBlock = emptySet(), zone = ZoneOffset.UTC)
        val now = Instant.parse("2026-10-27T10:00:00Z")
        val profile = Profile(1, "juegos", apps = setOf("com.game"), limit = UsageLimit(LimitKind.TIME, 30))
        val usage = mapOf(1L to ProfileUsage(periodStartMs = Instant.parse("2026-10-27T00:00:00Z").toEpochMilli(), timeMs = 31 * 60_000L))
        val block = decider.decide(ForegroundState("com.game"), listOf(profile), now, usage) as BlockDecision.Block
        assertEquals(BlockReason.LIMIT_REACHED, block.reason)
        assertEquals(Instant.parse("2026-10-28T00:00:00Z"), block.availableAt)
        val continuous = profile.copy(limit = UsageLimit(LimitKind.CONTINUOUS, 30))
        val tired = mapOf(1L to ProfileUsage(continuousMs = 31 * 60_000L, lastActiveMs = now.toEpochMilli()))
        assertNull((decider.decide(ForegroundState("com.game"), listOf(continuous), now, tired) as BlockDecision.Block).availableAt)
    }

    @Test
    fun todasLasPaletasCumplenContrasteAAA() {
        LockPalettes.all.forEach {
            assertTrue(Contrast.ratio(it.background, it.text) >= 7.0, "${it.id} tiene poco contraste")
        }
        assertEquals(LockPalettes.all.size, LockPalettes.all.map { it.id }.toSet().size)
    }

    @Test
    fun elContrasteDeNegroSobreBlancoEs21() {
        assertEquals(21.0, Contrast.ratio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test
    fun unaPaletaDesconocidaUsaLaPorDefecto() {
        assertEquals(LockPalettes.DEFAULT_ID, LockPalettes.byId("no-existe").id)
    }

    @Test
    fun elMensajeDeUnaAppGanaAlGeneral() {
        val style = LockStyle(message = "General", appMessages = mapOf("com.instagram.android" to "Instagram no", "com.x" to "  "))
        val app = BlockReason.PROFILE_APP
        assertEquals("Instagram no", style.messageFor(app, "com.instagram.android"))
        assertEquals("General", style.messageFor(app, "com.x"))
        assertEquals("General", style.messageFor(BlockReason.LIMIT_REACHED, "otra"))
        assertNull(LockStyle().messageFor(app, "otra"))
    }

    @Test
    fun elModoEstrictoYLaHoraSiempreExplicanSuMotivo() {
        val style = LockStyle(message = "General", appMessages = mapOf("com.android.settings" to "Déjame entrar"))
        assertNull(style.messageFor(BlockReason.STRICT_MODE, "com.android.settings"))
        assertNull(style.messageFor(BlockReason.CLOCK_PROTECTED, "fecha y hora"))
    }

    @Test
    fun recortaPorCaracteresSinPartirUnEmoji() {
        val family = "👨‍👩‍👧"
        val clamped = LockStyle(emoji = "👍".repeat(LockStyle.MAX_EMOJI + 3)).clamped()
        assertEquals("👍".repeat(LockStyle.MAX_EMOJI), clamped.emoji)
        assertEquals(family, LockStyle(emoji = family).clamped().emoji)
    }

    @Test
    fun elMensajeSoloVaConLosMotivosDeUnPerfil() {
        assertEquals(setOf(BlockReason.PROFILE_APP, BlockReason.PROFILE_WEB, BlockReason.NOT_ALLOWED, BlockReason.LIMIT_REACHED), EmergencyUnlock.ELIGIBLE_REASONS)
    }

    @Test
    fun recortaLoEscritoDeMasYQuitaMensajesVacios() {
        val long = "x".repeat(LockStyle.MAX_MESSAGE + 20)
        val clamped = LockStyle(message = long, emoji = "e".repeat(20), appMessages = mapOf("a" to long, "b" to " ")).clamped()
        assertEquals(LockStyle.MAX_MESSAGE, clamped.message.length)
        assertEquals(LockStyle.MAX_EMOJI, clamped.emoji.length)
        assertEquals(setOf("a"), clamped.appMessages.keys)
        assertEquals(LockStyle.MAX_MESSAGE, clamped.appMessages.getValue("a").length)
    }

    @Test
    fun laFrasePropiaDelIntentoDeApagarSoloVaConLosAjustesProtegidos() {
        val style = LockStyle(guardMessage = "Piensa por qué lo instalaste.")
        assertEquals("Piensa por qué lo instalaste.", style.guardNoteFor(BlockReason.STRICT_MODE, StrictProtection.SETTINGS_GUARD.screenName))
        assertEquals(null, style.guardNoteFor(BlockReason.STRICT_MODE, StrictProtection.RECENTS.screenName))
        assertEquals(null, style.guardNoteFor(BlockReason.PROFILE_APP, StrictProtection.SETTINGS_GUARD.screenName))
        assertEquals(null, LockStyle().guardNoteFor(BlockReason.STRICT_MODE, StrictProtection.SETTINGS_GUARD.screenName))
        // El motivo del modo estricto no se sustituye: la frase se suma y no entra en messageFor.
        assertEquals(null, style.messageFor(BlockReason.STRICT_MODE, StrictProtection.SETTINGS_GUARD.screenName))
        assertEquals(LockStyle.MAX_MESSAGE, LockStyle(guardMessage = "x".repeat(500)).clamped().guardMessage.length)
    }

    @Test
    fun lasFrasesRotanYDanLaVueltaSinFallar() {
        assertTrue(LockMotivation.phrases.size >= 12)
        assertEquals(LockMotivation.pick(0), LockMotivation.pick(LockMotivation.phrases.size))
        assertEquals(LockMotivation.pick(LockMotivation.phrases.size - 1), LockMotivation.pick(-1))
    }
}
