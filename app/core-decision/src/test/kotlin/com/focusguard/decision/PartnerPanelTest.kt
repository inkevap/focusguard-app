package com.focusguard.decision

import com.focusguard.model.PartnerEventType
import com.focusguard.model.StrictProtection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartnerPanelTest {

    private val empty = PartnerPanel()

    @Test fun deSerieSeMandaTodoYNoHayProteccionesExtra() {
        assertTrue(PartnerPanel.NOTICE_TYPES.all { empty.watch.allows(it) && empty.parental.allows(it) })
        assertTrue(empty.watch.protections.isEmpty() && empty.parental.protections.isEmpty())
    }

    @Test fun valeElModoQueEstaEnUso() {
        val panel = PartnerPanel(
            watch = PartnerChoices(noticesOff = setOf(PartnerEventType.BLOCKED)),
            parental = PartnerChoices(protections = setOf(StrictProtection.EDIT_LOCK)),
        )
        assertFalse(panel.active(parentalOn = false).allows(PartnerEventType.BLOCKED))
        assertTrue(panel.active(parentalOn = true).allows(PartnerEventType.BLOCKED))
        assertEquals(setOf(StrictProtection.EDIT_LOCK), panel.active(parentalOn = true).protections)
        assertTrue(panel.active(parentalOn = false).protections.isEmpty())
    }

    @Test fun apagarUnAvisoOQuitarUnaProteccionDebilitaYAnadirEsLibre() {
        val base = PartnerPanel(watch = PartnerChoices(protections = setOf(StrictProtection.RECENTS)))
        assertTrue(base.weakens(base.copy(watch = base.watch.copy(noticesOff = setOf(PartnerEventType.PANIC)))))
        assertTrue(base.weakens(base.copy(watch = base.watch.copy(protections = emptySet()))))
        assertTrue(base.weakens(base.copy(parental = PartnerChoices(noticesOff = setOf(PartnerEventType.BLOCKED)))))
        assertFalse(base.weakens(base.copy(watch = base.watch.copy(protections = base.watch.protections + StrictProtection.EDIT_LOCK))))
        val off = base.copy(watch = base.watch.copy(noticesOff = setOf(PartnerEventType.PANIC)))
        assertFalse(off.weakens(base))
        assertFalse(base.weakens(base))
    }

    @Test fun seGuardaYSeLeeSinPerderNada() {
        val panel = PartnerPanel(
            watch = PartnerChoices(setOf(PartnerEventType.BLOCKED, PartnerEventType.DNS_COUNTS), setOf(StrictProtection.SETTINGS_GUARD, StrictProtection.EDIT_LOCK)),
            parental = PartnerChoices(emptySet(), setOf(StrictProtection.POWER_MENU)),
        )
        assertEquals(panel, PartnerPanel.decode(panel.encode()))
        assertEquals(empty, PartnerPanel.decode(empty.encode()))
    }

    @Test fun loQueNoSeEntiendeSeDescartaEnteroEnVezDeAdivinar() {
        assertNull(PartnerPanel.decode(""))
        assertNull(PartnerPanel.decode("a;b"))
        assertNull(PartnerPanel.decode("NOEXISTE;|;"))
        assertNull(PartnerPanel.decode(";EDIT_LOCK|;NOEXISTE"))
    }

    @Test fun cadaSolicitudConocidaTieneSuAreaYLasDemasNinguna() {
        assertEquals(PartnerArea.PROFILES_EDIT, PartnerArea.of("profile_off:3"))
        assertEquals(PartnerArea.PROFILES_PAUSE, PartnerArea.of("session_end:1"))
        assertEquals(PartnerArea.ADULT_FILTER, PartnerArea.of("adult_text_exempt"))
        assertEquals(PartnerArea.IMAGES, PartnerArea.of("image_sensitivity"))
        assertEquals(PartnerArea.STREAK, PartnerArea.of("streak_restore:20000"))
        assertEquals(PartnerArea.PARTNER_SETTINGS, PartnerArea.of("partner_panel"))
        assertNull(PartnerArea.of("strict_exit:disable"))
        assertNull(PartnerArea.of("clave_que_no_existe"))
    }

    @Test fun todasLasAccionesDeLaAppEstanEnAlgunAreaSalvoLasDelModoEstrictoYElAdministrador() {
        val keys = listOf(
            "adult_off", "adult_word_remove", "adult_allow", "dns_off", "dns_exclude", "adult_text_exempt", "server_change", "approval_mode", "day_start",
            "new_apps", "embedded_browsers", "image_sensitivity", "image_probe_off", "image_vigilance", "image_adaptive", "fold_floating", "sign_out",
            "launcher_icon", "parental_off", "urge_sharing_off", "partner_notices_off", "partner_panel", "profile_edit:1", "profile_off:1", "profile_delete:1",
            "profile_pause:1", "session_end:1", "streak_restore:1",
        )
        keys.forEach { assertTrue(PartnerArea.of(it) != null, it) }
        assertEquals(PartnerArea.entries.toSet(), keys.mapNotNull(PartnerArea::of).toSet())
    }

    @Test fun losBloqueosSeAvisanPorClaseYPorPerfil() {
        val c = PartnerChoices(
            blockedReasonsOff = setOf(com.focusguard.model.BlockReason.LIMIT_REACHED),
            blockedProfilesOff = setOf(7L),
        )
        assertTrue(c.allowsBlock(com.focusguard.model.BlockReason.PROFILE_APP, 1))
        assertFalse(c.allowsBlock(com.focusguard.model.BlockReason.LIMIT_REACHED, 1))
        assertFalse(c.allowsBlock(com.focusguard.model.BlockReason.PROFILE_APP, 7))
        // Con el aviso general de bloqueos apagado no se avisa de ninguno.
        assertFalse(PartnerChoices(noticesOff = setOf(PartnerEventType.BLOCKED)).allowsBlock(com.focusguard.model.BlockReason.PROFILE_APP, 1))
    }

    @Test fun quitarUnAreaOApagarUnaClaseDeBloqueoDebilitaYElFormatoViejoSeSigueLeyendo() {
        val base = PartnerPanel(watch = PartnerChoices(areas = setOf(PartnerArea.DNS_FILTER)))
        assertTrue(base.weakens(base.copy(watch = PartnerChoices())))
        assertTrue(base.weakens(base.copy(watch = base.watch.copy(blockedProfilesOff = setOf(2L)))))
        assertFalse(base.weakens(base.copy(watch = base.watch.copy(areas = base.watch.areas + PartnerArea.IMAGES))))
        val full = PartnerPanel(watch = PartnerChoices(setOf(PartnerEventType.PANIC), setOf(StrictProtection.RECENTS), setOf(PartnerArea.IMAGES), setOf(com.focusguard.model.BlockReason.SUB_APP), setOf(4L, 9L)))
        assertEquals(full, PartnerPanel.decode(full.encode()))
        assertEquals(PartnerPanel(watch = PartnerChoices(setOf(PartnerEventType.BLOCKED), setOf(StrictProtection.EDIT_LOCK))), PartnerPanel.decode("BLOCKED;EDIT_LOCK|;"))
    }
}
