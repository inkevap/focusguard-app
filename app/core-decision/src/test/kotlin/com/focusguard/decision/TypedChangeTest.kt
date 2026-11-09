package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypedChangeTest {
    @Test fun teclearUnaLetraSeAcepta() {
        assertTrue(Approvals.isTypedChange("hola", "hola m"))
        assertTrue(Approvals.isTypedChange("hola", "hol"))
    }

    @Test fun pegarUnBloqueSeRechaza() {
        assertFalse(Approvals.isTypedChange("", "confirmo que este elemento no es pornográfico"))
    }

    @Test fun pegarUnBloqueSobreUnaSeleccionSeRechaza() {
        val previous = "x".repeat(35)
        assertFalse(Approvals.isTypedChange(previous, "confirmo que este elemento no es pornográfico"))
    }

    @Test fun pegarUnBloqueEnMedioDelTextoSeRechaza() {
        assertFalse(Approvals.isTypedChange("inicio fin", "inicio ${"y".repeat(30)} fin"))
    }
}
