package com.focusguard.decision

import com.focusguard.model.StrictProtection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemShellScreensTest {

    private fun screen(pkg: String, className: String? = null, vararg ids: String, texts: List<String> = emptyList()) =
        ScreenInfo(pkg, texts, ids.toSet(), className)

    @Test
    fun reconoceElMenuDeApagadoPorLaActividadOPorSusVistas() {
        val byClass = screen("com.android.systemui", "com.android.systemui.globalactions.GlobalActionsDialogLite\$ActionsDialog")
        val byId = screen("com.android.systemui", null, "com.android.systemui:id/globalactions_root")
        val xiaomi = screen("miui.systemui.plugin", null, "miui.systemui.plugin:id/touch_container")
        listOf(byClass, byId, xiaomi).forEach { assertEquals(StrictProtection.POWER_MENU, SensitiveScreens.classify(it, "FocusGuard")) }
    }

    @Test
    fun reconoceElPanelDeNotificacionesYSusAjustesRapidos() {
        val panel = screen("com.android.systemui", null, "com.android.systemui:id/notification_panel")
        val editing = screen("com.android.systemui", null, "com.android.systemui:id/tiles_edit")
        assertEquals(StrictProtection.NOTIFICATION_SHADE, SensitiveScreens.classify(panel, "FocusGuard"))
        assertEquals(StrictProtection.NOTIFICATION_SHADE, SensitiveScreens.classify(editing, "FocusGuard"))
    }

    @Test
    fun soloElSistemaPuedeSerElMenuDeApagado() {
        val fakeClass = screen("com.example", "com.example.globalactions.Dialog")
        val fakeId = screen("com.example", null, "com.example:id/globalactions_root")
        assertNull(SensitiveScreens.classify(fakeClass, "FocusGuard"))
        assertNull(SensitiveScreens.classify(fakeId, "FocusGuard"))
        assertTrue(SensitiveScreens.isPowerMenuClass("com.android.systemui.globalactions.GlobalActionsDialog"))
        assertFalse(SensitiveScreens.isPowerMenuClass(null))
    }

    @Test
    fun lasBusquedasPorIdentificadorCubrenElPanelYElMenuDeXiaomi() {
        assertTrue("com.android.systemui:id/notification_panel" in SensitiveScreens.shellViewIds)
        assertTrue("miui.systemui.plugin:id/touch_container" in SensitiveScreens.shellViewIds)
    }

    @Test
    fun laBarraDeEstadoSolaOOtrasAppsNoSeProtegen() {
        assertNull(SensitiveScreens.classify(screen("com.android.systemui", null, "com.android.systemui:id/clock"), "FocusGuard"))
        assertNull(SensitiveScreens.classify(screen("com.example", null, "com.example:id/notification_panel"), "FocusGuard"))
        assertTrue(SensitiveScreens.isSystemShell("com.android.systemui"))
        assertFalse(SensitiveScreens.isSystemShell("com.example"))
    }

    @Test
    fun ajustesQueNombranLaAppSiguenSiendoLaGuardiaDeAjustes() {
        val info = screen("com.android.settings", texts = listOf("FocusGuard", "Desinstalar"))
        assertEquals(StrictProtection.SETTINGS_GUARD, SensitiveScreens.classify(info, "FocusGuard"))
    }

    @Test
    fun soloLaConfirmacionDeDesinstalarEsUnIntentoDeDesinstalar() {
        val infoDeLaApp = screen("com.android.settings", texts = listOf("Información de la app", "FocusGuard", "Desinstalar", "Forzar detención"))
        val confirmar = screen("com.google.android.packageinstaller", texts = listOf("¿Quieres desinstalar esta aplicación?"))
        val otrosAjustes = screen("com.android.settings", texts = listOf("Batería", "FocusGuard"))
        val otraApp = screen("com.ejemplo.tienda", texts = listOf("Desinstalar"))
        val actualizaciones = screen("com.android.settings", texts = listOf("FocusGuard", "Desinstalar actualizaciones"))
        // La ficha de la app tiene siempre el botón: no es un intento de desinstalar.
        assertFalse(SensitiveScreens.isUninstallScreen(infoDeLaApp))
        assertTrue(SensitiveScreens.isUninstallScreen(confirmar))
        assertFalse(SensitiveScreens.isUninstallScreen(otrosAjustes))
        assertFalse(SensitiveScreens.isUninstallScreen(otraApp))
        assertFalse(SensitiveScreens.isUninstallScreen(actualizaciones))
    }

    @Test
    fun reconoceElAhorroExtremoDeBateria() {
        val samsung = screen("com.samsung.android.settings", texts = listOf("Ultra power saving mode", "Activar"))
        val espanol = screen("com.android.settings", texts = listOf("Ahorro de energía ultra"))
        val coreano = screen("com.samsung.android.settings", texts = listOf("초절전모드"))
        val ahorroNormal = screen("com.android.settings", texts = listOf("Ahorro de batería", "Activar ahora"))
        assertEquals(StrictProtection.BATTERY_SAVER, SensitiveScreens.classify(samsung, "FocusGuard"))
        assertEquals(StrictProtection.BATTERY_SAVER, SensitiveScreens.classify(espanol, "FocusGuard"))
        assertEquals(StrictProtection.BATTERY_SAVER, SensitiveScreens.classify(coreano, "FocusGuard"))
        assertNull(SensitiveScreens.classify(ahorroNormal, "FocusGuard"))
    }

    @Test
    fun cadaCadenaDeLaTablaDeAhorroExtremoSeReconoce() {
        // Las 17 cadenas de TABLAS_Y_ALGORITMO.md 4; las que otra contiene deben seguir reconocidas.
        val tabla = listOf(
            "Ultra battery saver", "초절전모드", "초절전", "סוללה חסכ'", "חיסכון גבוה במיוחד", "ウルトラ 省電力", "ウルトラ省電力の", "Ultrabatería",
            "ahorro de energía ultra", "Ultra-Akku", "Akkuopti", "Energiesparmodus", "Ultra-Stromsparen", "Gestion d'alim. Ultra",
            "gestion d'alimentation Ultra", "Режим Ультра", "ultra power saving",
        )
        for (titulo in tabla) {
            assertEquals(StrictProtection.BATTERY_SAVER, SensitiveScreens.classify(screen("com.samsung.android.settings", texts = listOf(titulo)), "FocusGuard"), titulo)
        }
    }

    @Test
    fun elAhorroExtremoGanaAlAdministradorDeTareasSiAparecenLosDos() {
        val ambos = screen("com.android.settings", texts = listOf("Servicios en ejecución", "Ultra battery saver"))
        assertEquals(StrictProtection.BATTERY_SAVER, SensitiveScreens.classify(ambos, "FocusGuard"))
    }

    @Test
    fun reconoceElAdministradorDeTareasYElCambioDeUsuario() {
        val servicios = screen("com.android.settings", texts = listOf("Servicios en ejecución"))
        val usuario = screen("com.android.settings", texts = listOf("Add guest", "Add user"))
        val otra = screen("com.android.settings", texts = listOf("Sonido", "Volumen"))
        assertEquals(StrictProtection.TASK_MANAGER, SensitiveScreens.classify(servicios, "FocusGuard"))
        assertEquals(StrictProtection.TASK_MANAGER, SensitiveScreens.classify(usuario, "FocusGuard"))
        assertNull(SensitiveScreens.classify(otra, "FocusGuard"))
    }

    @Test
    fun reconoceUnDialogoDeCompraDeGooglePlay() {
        val compra = screen("com.android.vending", "com.google.android.finsky.billing.acquire.AcquireActivity")
        val tienda = screen("com.android.vending", "com.google.android.finsky.activities.MainActivity")
        assertEquals(StrictProtection.PURCHASE_DIALOGS, SensitiveScreens.classify(compra, "FocusGuard"))
        assertNull(SensitiveScreens.classify(tienda, "FocusGuard"))
        assertTrue(SensitiveScreens.isBillingPackage("com.android.vending"))
    }
}
