package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UninstallGuardTest {

    private val now = 1_000_000L

    @Test fun soloSeCierraConElAdministradorActivoYSinPermiso() {
        assertTrue(UninstallGuard.shouldGuard(adminActive = true, grantUntilMs = 0, nowMs = now))
        assertFalse(UninstallGuard.shouldGuard(adminActive = false, grantUntilMs = 0, nowMs = now), "sin administrador no hay nada que proteger (y hay que poder activarlo)")
        assertFalse(UninstallGuard.shouldGuard(adminActive = true, grantUntilMs = now + 1, nowMs = now))
        assertTrue(UninstallGuard.shouldGuard(adminActive = true, grantUntilMs = now, nowMs = now), "el permiso vale hasta su fin, sin incluirlo")
    }

    @Test fun elPermisoDuraDiezMinutos() {
        assertEquals(now + 10 * 60_000L, UninstallGuard.grantUntil(now))
    }

    private fun screen(pkg: String, vararg texts: String) = ScreenInfo(pkg, texts.toList(), emptySet())

    @Test fun reconoceLaPantallaDondeSeDesactivaElAdministrador() {
        assertTrue(SensitiveScreens.isDeviceAdminScreen(screen("com.android.settings", "FocusGuard", "Desactivar esta aplicación de administración del dispositivo"), "FocusGuard"))
        assertTrue(SensitiveScreens.isDeviceAdminScreen(screen("com.android.settings", "Device admin apps", "FocusGuard"), "FocusGuard"))
        assertTrue(SensitiveScreens.isDeviceAdminScreen(screen("com.samsung.android.settings", "Administradores del dispositivo", "focusguard"), "FocusGuard"))
    }

    @Test fun reconoceLaPantallaDondeSeApagaLaAccesibilidad() {
        assertTrue(SensitiveScreens.isAccessibilityScreen(screen("com.android.settings", "FocusGuard", "Usar FocusGuard", "Accesibilidad"), "FocusGuard"))
        assertTrue(SensitiveScreens.isAccessibilityScreen(screen("com.android.settings", "Accessibility", "Downloaded apps", "FocusGuard"), "FocusGuard"))
        assertTrue(SensitiveScreens.isAccessibilityScreen(screen("com.samsung.android.settings", "Accesibilidad", "focusguard"), "FocusGuard"))
    }

    @Test fun laAccesibilidadSinFocusGuardNiLaFichaNormalCuentan() {
        assertFalse(SensitiveScreens.isAccessibilityScreen(screen("com.android.settings", "Accesibilidad", "TalkBack"), "FocusGuard"))
        assertFalse(SensitiveScreens.isAccessibilityScreen(screen("com.android.settings", "FocusGuard", "Notificaciones", "Desinstalar"), "FocusGuard"))
        assertFalse(SensitiveScreens.isAccessibilityScreen(screen("com.otra", "FocusGuard", "Accesibilidad"), "FocusGuard"))
    }

    @Test fun nadaMasSeConfundeConElla() {
        // La ficha normal de la app no menciona la administración del dispositivo.
        assertFalse(SensitiveScreens.isDeviceAdminScreen(screen("com.android.settings", "FocusGuard", "Notificaciones", "Desinstalar"), "FocusGuard"))
        // La lista de administradores sin FocusGuard tampoco.
        assertFalse(SensitiveScreens.isDeviceAdminScreen(screen("com.android.settings", "Device admin apps", "Find My Device"), "FocusGuard"))
        // Ni otra app que hable de administración.
        assertFalse(SensitiveScreens.isDeviceAdminScreen(screen("com.otra", "FocusGuard", "Device admin"), "FocusGuard"))
    }
}
