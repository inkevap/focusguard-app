package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals

class ChangeRuleTest {

    @Test
    fun sinNadaActivadoElCambioSePermite() {
        assertEquals(ChangeOutcome.ALLOWED, ChangeRule.decide(locked = false, approvalRequired = false))
    }

    @Test
    fun conAprobacionesElCambioSolicitaUna() {
        assertEquals(ChangeOutcome.NEEDS_APPROVAL, ChangeRule.decide(locked = false, approvalRequired = true))
    }

    @Test
    fun elModoEstrictoRechazaYNoDejaSolicitarAprobacion() {
        assertEquals(ChangeOutcome.LOCKED, ChangeRule.decide(locked = true, approvalRequired = false))
        assertEquals(ChangeOutcome.LOCKED, ChangeRule.decide(locked = true, approvalRequired = true))
    }
}
