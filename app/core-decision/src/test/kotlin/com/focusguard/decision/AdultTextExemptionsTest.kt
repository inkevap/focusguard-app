package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdultTextExemptionsTest {

    @Test fun lasAppsDeNotasDeSerieNoSeLeen() {
        assertTrue(AdultTextExemptions.skipsText("md.obsidian", isBrowser = false, userChosen = emptySet()))
        assertTrue(AdultTextExemptions.skipsText("com.google.android.keep", isBrowser = false, userChosen = emptySet()))
    }

    @Test fun lasQueEligeElUsuarioTambien() {
        assertFalse(AdultTextExemptions.skipsText("com.otra", isBrowser = false, userChosen = emptySet()))
        assertTrue(AdultTextExemptions.skipsText("com.otra", isBrowser = false, userChosen = setOf("com.otra")))
    }

    @Test fun unNavegadorNuncaSeSaltaAunqueEsteEnLaLista() {
        assertFalse(AdultTextExemptions.skipsText("md.obsidian", isBrowser = true, userChosen = setOf("md.obsidian")))
        assertFalse(AdultTextExemptions.skipsText("com.android.chrome", isBrowser = true, userChosen = setOf("com.android.chrome")))
    }

    @Test fun laListaDeSerieNoTrae_navegadoresNiRedes() {
        val risky = listOf("chrome", "firefox", "instagram", "tiktok", "twitter", "reddit", "telegram", "whatsapp", "youtube", "facebook")
        assertTrue(AdultTextExemptions.DEFAULT.none { pkg -> risky.any { it in pkg } })
    }
}
