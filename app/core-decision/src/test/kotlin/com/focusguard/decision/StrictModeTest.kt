package com.focusguard.decision

import com.focusguard.model.ExitContext
import com.focusguard.model.ExitMethod
import com.focusguard.model.ExitRequest
import com.focusguard.model.ExitRequirement
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.StrictAction
import com.focusguard.model.StrictConfig
import com.focusguard.model.StrictProtection
import com.focusguard.model.StrictState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StrictModeTest {

    private val now = Instant.parse("2026-10-26T12:00:00Z")
    private val local = LocalDateTime.of(2026, 10, 26, 12, 0) // lunes
    private fun later(minutes: Long) = now.plusSeconds(minutes * 60)
    private val idle = ExitContext(charging = false, pinCorrect = null)

    private fun config(vararg methods: ExitMethod, intervals: List<ScheduleInterval> = emptyList()) = StrictConfig(
        enabled = true,
        intervals = intervals,
        protections = setOf(StrictProtection.EDIT_LOCK, StrictProtection.SETTINGS_GUARD),
        exitMethods = methods.toList(),
    )

    @Test
    fun estadosSegunConfiguracionHorarioYPausa() {
        assertEquals(StrictState.INACTIVE, StrictMode.state(StrictConfig(), local, now))
        assertEquals(StrictState.ACTIVE, StrictMode.state(config(), local, now))
        val mondayMorning = listOf(ScheduleInterval(DayOfWeek.MONDAY, 8 * 60, 10 * 60))
        assertEquals(StrictState.WAITING, StrictMode.state(config(intervals = mondayMorning), local, now))
        val paused = config().copy(pausedUntil = later(5))
        assertEquals(StrictState.PAUSED, StrictMode.state(paused, local, now))
        assertEquals(StrictState.ACTIVE, StrictMode.state(paused, local, later(5)))
    }

    @Test
    fun quitarElMenuDeApagadoOElPanelDebilita() {
        val old = config().copy(protections = setOf(StrictProtection.POWER_MENU, StrictProtection.NOTIFICATION_SHADE))
        assertTrue(StrictMode.isWeakening(old, old.copy(protections = setOf(StrictProtection.POWER_MENU))))
        assertFalse(StrictMode.isWeakening(old, old.copy(protections = old.protections + StrictProtection.RECENTS)))
    }

    @Test
    fun isActiveSoloEsVerdaderoEnEstadoActivo() {
        assertTrue(StrictMode.isActive(config(), local, now))
        assertFalse(StrictMode.isActive(StrictConfig(), local, now))
        assertFalse(StrictMode.isActive(config().copy(pausedUntil = later(5)), local, now))
    }

    @Test
    fun soloProtegeEnEstadoActivoYSiLaProteccionEstaElegida() {
        val c = config()
        assertTrue(StrictMode.isProtected(c, StrictProtection.EDIT_LOCK, local, now))
        assertFalse(StrictMode.isProtected(c, StrictProtection.RECENTS, local, now))
        assertFalse(StrictMode.isProtected(c.copy(pausedUntil = later(5)), StrictProtection.EDIT_LOCK, local, now))
        assertFalse(StrictMode.isProtected(StrictConfig(), StrictProtection.EDIT_LOCK, local, now))
    }

    @Test
    fun laConfiguracionSoloSeEditaSiNoEstaActivo() {
        assertFalse(StrictMode.canEditConfig(config(), local, now))
        assertTrue(StrictMode.canEditConfig(config().copy(pausedUntil = later(5)), local, now))
        assertTrue(StrictMode.canEditConfig(StrictConfig(), local, now))
    }

    private fun request(at: Instant = now, action: StrictAction = StrictAction.Disable) = ExitRequest(action, at)

    @Test
    fun temporizadorEsperaElTiempoFijo() {
        val c = config(ExitMethod.Timer(10))
        val early = StrictMode.evaluateExit(c, request(), idle, local, later(9))
        assertFalse(early.satisfied)
        assertEquals(listOf<Any>(ExitRequirement.WaitUntil(later(10))), early.pending)
        assertTrue(StrictMode.evaluateExit(c, request(), idle, local, later(10)).satisfied)
    }

    @Test
    fun cargadorYPinSePiden() {
        val c = config(ExitMethod.Charger, ExitMethod.Pin)
        val status = StrictMode.evaluateExit(c, request(), idle, local, now)
        assertEquals(listOf<Any>(ExitRequirement.NeedCharger, ExitRequirement.NeedPin), status.pending)
        val ok = ExitContext(charging = true, pinCorrect = true)
        assertTrue(StrictMode.evaluateExit(c, request(), ok, local, now).satisfied)
        val wrongPin = ExitContext(charging = true, pinCorrect = false)
        assertEquals(listOf<Any>(ExitRequirement.NeedPin), StrictMode.evaluateExit(c, request(), wrongPin, local, now).pending)
    }

    @Test
    fun seguirHorariosSoloDejaFueraDelHorario() {
        val inside = config(ExitMethod.FollowSchedule)
        assertEquals(
            listOf<Any>(ExitRequirement.WaitForScheduleEnd),
            StrictMode.evaluateExit(inside, request(), idle, local, now).pending,
        )
        val outside = config(ExitMethod.FollowSchedule, intervals = listOf(ScheduleInterval(DayOfWeek.MONDAY, 8 * 60, 10 * 60)))
        assertTrue(StrictMode.evaluateExit(outside, request(), idle, local, now).satisfied)
    }

    @Test
    fun esperaConcedeTrasElTiempoYCaducaSiNoSeUsa() {
        val c = config(ExitMethod.Cooldown(5))
        assertEquals(listOf<Any>(ExitRequirement.WaitUntil(later(5))), StrictMode.evaluateExit(c, request(), idle, local, later(1)).pending)
        assertTrue(StrictMode.evaluateExit(c, request(), idle, local, later(5)).satisfied)
        assertTrue(StrictMode.evaluateExit(c, request(), idle, local, later(14)).satisfied)
        assertEquals(listOf<Any>(ExitRequirement.RequestExpired), StrictMode.evaluateExit(c, request(), idle, local, later(15)).pending)
    }

    @Test
    fun losMetodosCombinadosExigenTodos() {
        val c = config(ExitMethod.Timer(5), ExitMethod.Charger)
        val onlyCharger = ExitContext(charging = true, pinCorrect = null)
        assertFalse(StrictMode.evaluateExit(c, request(), onlyCharger, local, later(1)).satisfied)
        assertTrue(StrictMode.evaluateExit(c, request(), onlyCharger, local, later(5)).satisfied)
    }

    @Test
    fun elCompaneroApruebaLaSalida() {
        val c = config(ExitMethod.Partner)
        val linked = ExitContext(charging = false, pinCorrect = null, partnerLinked = true)
        assertEquals(listOf<Any>(ExitRequirement.NeedPartner), StrictMode.evaluateExit(c, request(), linked, local, now).pending)
        val approved = request().copy(partnerApproved = true)
        assertTrue(StrictMode.evaluateExit(c, approved, linked, local, now).satisfied)
        // Lo aprobado vale aunque el compañero se desvincule después.
        assertTrue(StrictMode.evaluateExit(c, approved, idle, local, now).satisfied)
    }

    @Test
    fun sinCompaneroVinculadoLaSalidaPorCompaneroNoSePuede() {
        val c = config(ExitMethod.Partner)
        assertEquals(listOf<Any>(ExitRequirement.NoPartner), StrictMode.evaluateExit(c, request(), idle, local, now).pending)
    }

    @Test
    fun laAprobacionDelCompaneroNoSaltaLosDemasMetodos() {
        val c = config(ExitMethod.Partner, ExitMethod.Timer(10))
        val approved = request().copy(partnerApproved = true)
        assertEquals(listOf<Any>(ExitRequirement.WaitUntil(later(10))), StrictMode.evaluateExit(c, approved, idle, local, now).pending)
        assertTrue(StrictMode.evaluateExit(c, approved, idle, local, later(10)).satisfied)
    }

    @Test
    fun quitarAlCompaneroDeLosMetodosDebilita() {
        val old = config(ExitMethod.Pin, ExitMethod.Partner)
        assertTrue(StrictMode.isWeakening(old, old.copy(exitMethods = listOf(ExitMethod.Pin))))
        assertFalse(StrictMode.isWeakening(old.copy(exitMethods = listOf(ExitMethod.Pin)), old))
    }

    @Test
    fun sinMetodosNoFaltaNada() {
        assertTrue(StrictMode.evaluateExit(config(), request(), idle, local, now).satisfied)
    }

    @Test
    fun relajarLaConfiguracionYaActivadaEsDebilitar() {
        val base = StrictConfig(
            enabled = true,
            protections = setOf(StrictProtection.EDIT_LOCK, StrictProtection.RECENTS),
            exitMethods = listOf(ExitMethod.Timer(10), ExitMethod.Pin),
            intervals = listOf(ScheduleInterval(DayOfWeek.MONDAY, 0, 60)),
        )
        assertTrue(StrictMode.isWeakening(base, base.copy(enabled = false)))
        assertTrue(StrictMode.isWeakening(base, base.copy(protections = setOf(StrictProtection.EDIT_LOCK))))
        assertTrue(StrictMode.isWeakening(base, base.copy(exitMethods = listOf(ExitMethod.Timer(10)))))
        assertTrue(StrictMode.isWeakening(base, base.copy(exitMethods = listOf(ExitMethod.Timer(5), ExitMethod.Pin))))
        assertTrue(StrictMode.isWeakening(base, base.copy(intervals = listOf(ScheduleInterval(DayOfWeek.TUESDAY, 0, 60)))))
        assertTrue(StrictMode.isWeakening(base.copy(intervals = emptyList()), base))
    }

    @Test
    fun endurecerOCrearLaConfiguracionNoEsDebilitar() {
        val base = StrictConfig(
            enabled = true,
            protections = setOf(StrictProtection.EDIT_LOCK),
            exitMethods = listOf(ExitMethod.Timer(10)),
            intervals = listOf(ScheduleInterval(DayOfWeek.MONDAY, 0, 60)),
        )
        val stronger = base.copy(
            protections = base.protections + StrictProtection.RECENTS,
            exitMethods = listOf(ExitMethod.Timer(30), ExitMethod.Pin),
            intervals = base.intervals + ScheduleInterval(DayOfWeek.TUESDAY, 0, 60),
        )
        assertFalse(StrictMode.isWeakening(base, stronger))
        assertFalse(StrictMode.isWeakening(StrictConfig(), base)) // todavía sin activar: configurar es libre
        assertFalse(StrictMode.isWeakening(base, base))
    }

    @Test
    fun aplicarPausaYDesactivar() {
        val c = config(ExitMethod.Pin)
        val paused = StrictMode.apply(c, StrictAction.Pause(10), now)
        assertEquals(later(10), paused.pausedUntil)
        assertTrue(paused.enabled)
        val disabled = StrictMode.apply(paused, StrictAction.Disable, now)
        assertFalse(disabled.enabled)
        assertNull(disabled.pausedUntil)
    }
}

class SensitiveScreensTest {

    private fun screen(pkg: String, vararg texts: String, ids: Set<String> = emptySet()) =
        ScreenInfo(pkg, texts.toList(), ids)

    @Test
    fun ajustesQueNombranLaAppSeProtegen() {
        val accessibility = screen("com.android.settings", "Accesibilidad", "FocusGuard", "Desactivado")
        assertEquals(StrictProtection.SETTINGS_GUARD, SensitiveScreens.classify(accessibility, "FocusGuard"))
        val uninstall = screen("com.google.android.packageinstaller", "¿Quieres desinstalar esta app?", "focusguard")
        assertEquals(StrictProtection.SETTINGS_GUARD, SensitiveScreens.classify(uninstall, "FocusGuard"))
    }

    @Test
    fun otrosAjustesNoSeProtegen() {
        val wifi = screen("com.android.settings", "Wi-Fi", "Redes guardadas")
        assertNull(SensitiveScreens.classify(wifi, "FocusGuard"))
    }

    @Test
    fun elNombreDeLaAppFueraDeAjustesNoCuenta() {
        assertNull(SensitiveScreens.classify(screen("com.example.chat", "FocusGuard es genial"), "FocusGuard"))
    }

    @Test
    fun restablecerDeFabricaSeProtegeEnVariosIdiomas() {
        assertEquals(
            StrictProtection.SETTINGS_GUARD,
            SensitiveScreens.classify(screen("com.android.settings", "Restablecer datos de fábrica"), "FocusGuard"),
        )
        assertEquals(
            StrictProtection.SETTINGS_GUARD,
            SensitiveScreens.classify(screen("com.android.settings", "Factory data reset"), "FocusGuard"),
        )
    }

    @Test
    fun lasPantallasDeFechaYHoraSeReconocenEnVariosIdiomas() {
        assertTrue(SensitiveScreens.isDateTimeSettings(screen("com.android.settings", "Fecha y hora", "Zona horaria")))
        assertTrue(SensitiveScreens.isDateTimeSettings(screen("com.android.settings", "Date & time", "Set time automatically")))
        assertFalse(SensitiveScreens.isDateTimeSettings(screen("com.android.settings", "Wi-Fi", "Bluetooth")))
        assertFalse(SensitiveScreens.isDateTimeSettings(screen("com.example.reloj", "Fecha y hora")))
    }

    @Test
    fun laPaginaSistemaConUnaFilaDeFechaYHoraNoSeCierra() {
        assertFalse(SensitiveScreens.isDateTimeSettings(screen("com.android.settings", "Sistema", "Idiomas y entrada", "Fecha y hora", "Copia de seguridad")))
        assertFalse(SensitiveScreens.isDateTimeSettings(screen("com.android.settings", "Date & time")))
    }

    @Test
    fun fechaYHoraTambienSeReconoceONombreDeActividadSinDependerDelIdioma() {
        val inJapanese = ScreenInfo("com.android.settings", listOf("日付と時刻"), emptySet(), "com.android.settings.Settings\$DateTimeSettingsActivity")
        assertTrue(SensitiveScreens.isDateTimeSettings(inJapanese))
        assertFalse(SensitiveScreens.isDateTimeSettings(inJapanese.copy(className = "com.android.settings.Settings\$WifiSettingsActivity", texts = listOf("Wi-Fi"))))
    }

    @Test
    fun appsRecientesSeReconocenPorIdentificador() {
        val recents = screen("com.google.android.apps.nexuslauncher", ids = setOf("com.google.android.apps.nexuslauncher:id/overview_panel"))
        assertEquals(StrictProtection.RECENTS, SensitiveScreens.classify(recents, "FocusGuard"))
    }
}
