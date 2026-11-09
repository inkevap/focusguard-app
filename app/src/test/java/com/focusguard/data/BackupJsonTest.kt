package com.focusguard.data

import com.focusguard.decision.BackupSettings
import com.focusguard.decision.BackupSnapshot
import com.focusguard.decision.EmbeddedBrowserPolicy
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.model.ExitMethod
import com.focusguard.model.GeoFence
import com.focusguard.model.LockStyle
import com.focusguard.model.Profile
import com.focusguard.model.StrictConfig
import com.focusguard.model.StrictProtection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupJsonTest {
    private val profile = Profile(
        id = 7, title = "Trabajo", apps = setOf("com.red.social"), wifiNames = setOf("CasaWifi"),
        geofence = GeoFence(40.0, -3.7, 150, inverted = false),
    )
    private val snapshot = BackupSnapshot(
        settings = BackupSettings(
            dayStartMinute = 240, adultBlock = true, adultWords = setOf("palabra", "otra"),
            newApps = NewAppsPolicy(true, 72), embeddedBrowsers = EmbeddedBrowserPolicy(true, false, setOf("com.app")), foldFloating = true,
        ),
        lockStyle = LockStyle(message = "Tú puedes", emoji = "💪", appMessages = mapOf("com.red.social" to "Ahora no")),
        strict = StrictConfig(enabled = true, protections = setOf(StrictProtection.SETTINGS_GUARD), exitMethods = listOf(ExitMethod.Pin)),
        profiles = listOf(profile),
    )

    @Test fun laCopiaIdaYVueltaSinPerderNada() {
        val back = BackupJson.fromJson(BackupJson.toJson(snapshot, includePlaces = true, exportedAtMs = 0).toString())
        assertEquals(snapshot.settings, back.settings)
        assertEquals(snapshot.lockStyle, back.lockStyle)
        assertEquals(snapshot.strict, back.strict)
        assertEquals(snapshot.profiles, back.profiles)
    }

    @Test fun sinLugaresNoSaleLaWifiNiLaUbicacion() {
        val text = BackupJson.toJson(snapshot, includePlaces = false, exportedAtMs = 0).toString()
        assertTrue("CasaWifi" !in text)
        val back = BackupJson.fromJson(text)
        assertTrue(back.profiles.single().wifiNames.isEmpty())
        assertNull(back.profiles.single().geofence)
    }

    @Test fun unaCopiaDeLaVersionUnoSigueLeyendose() {
        val v1 = """{"version":1,"settings":{"dayStartMinute":60,"adultBlock":true},"profiles":[]}"""
        val back = BackupJson.fromJson(v1)
        assertEquals(60, back.settings.dayStartMinute)
        assertTrue(back.settings.adultBlock)
        assertNull(back.strict)
        assertNull(back.lockStyle)
    }

    @Test fun unaVersionDesconocidaOUnTextoRotoSeRechazan() {
        assertFailsWith<IllegalStateException> { BackupJson.fromJson("""{"version":99,"profiles":[]}""") }
        assertFailsWith<IllegalStateException> { BackupJson.fromJson("no es json") }
        assertFailsWith<IllegalStateException> { BackupJson.fromJson("""{"version":2}""") }
    }

    @Test fun unPerfilRotoNoDejaAplicarNada() {
        assertFailsWith<IllegalStateException> { BackupJson.fromJson("""{"version":2,"profiles":[{"id":"x"}]}""") }
    }
}
