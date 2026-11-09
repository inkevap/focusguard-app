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
}
