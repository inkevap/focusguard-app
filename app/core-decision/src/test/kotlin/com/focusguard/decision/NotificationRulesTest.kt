package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationRulesTest {

    private val on = NotificationSettings(captureBlocked = true, hideBackgroundNotice = true)

    private fun capture(
        settings: NotificationSettings = on, blocked: Boolean = true, ongoing: Boolean = false,
        summary: Boolean = false, category: String? = "msg", own: Boolean = false,
    ) = NotificationRules.shouldCapture(settings, own, ongoing, summary, category) { blocked }

    @Test
    fun soloSeCapturaConElInterruptorYLaAppBloqueada() {
        assertTrue(capture())
        assertFalse(capture(settings = NotificationSettings()))
        assertFalse(capture(blocked = false))
    }

    @Test
    fun nuncaSeQuitanLlamadasAlarmasServiciosResumenesNiLasPropias() {
        assertFalse(capture(category = "call"))
        assertFalse(capture(category = "alarm"))
        assertFalse(capture(ongoing = true))
        assertFalse(capture(summary = true))
        assertFalse(capture(own = true))
    }

    @Test
    fun noSePreguntaSiEstaBloqueadaCuandoYaSeDescarta() {
        var asked = false
        NotificationRules.shouldCapture(NotificationSettings(), own = false, ongoing = false, groupSummary = false, category = null) { asked = true; true }
        NotificationRules.shouldCapture(on, own = false, ongoing = true, groupSummary = false, category = null) { asked = true; true }
        assertFalse(asked)
    }

    @Test
    fun elAvisoDelSistemaSeReconocePorPaqueteYNombre() {
        assertTrue(NotificationRules.isBackgroundNotice(on, "android", { listOf("FocusGuard se ejecuta en segundo plano") }, "FocusGuard"))
        assertFalse(NotificationRules.isBackgroundNotice(on, "com.otra", { listOf("FocusGuard") }, "FocusGuard"))
        assertFalse(NotificationRules.isBackgroundNotice(on, "android", { listOf("Otra app") }, "FocusGuard"))
        assertFalse(NotificationRules.isBackgroundNotice(NotificationSettings(), "android", { listOf("FocusGuard") }, "FocusGuard"))
    }

    @Test
    fun elTextoSeRecortaYSeLimpia() {
        assertEquals("", NotificationRules.clip(null))
        assertEquals("hola", NotificationRules.clip("  hola \n"))
        assertEquals(NotificationRules.MAX_TEXT, NotificationRules.clip("x".repeat(1_000)).length)
    }
}
