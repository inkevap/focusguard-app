package com.focusguard.decision

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TroubleshooterTest {

    private val now = Instant.parse("2026-10-27T10:00:00Z")

    private val healthy = HealthSnapshot(
        accessibilityEnabled = true, usageStats = true, overlay = true, batteryUnrestricted = true, adminActive = true,
        notificationsEnabled = true, locationNeeded = false, locationGranted = false,
        lastHeartbeat = now.minusSeconds(10), now = now, manufacturer = "Google",
    )

    private fun fixes(s: HealthSnapshot) = Troubleshooter.problems(s).map { it.fix }

    @Test
    fun todoEnOrdenNoMuestraNada() {
        assertTrue(Troubleshooter.problems(healthy).isEmpty())
    }

    @Test
    fun sinAccesibilidadSoloPideActivarla() {
        assertEquals(listOf(Fix.ACCESSIBILITY), fixes(healthy.copy(accessibilityEnabled = false, lastHeartbeat = null)))
    }

    @Test
    fun activadoPeroSinSenalSePideReiniciarElServicio() {
        assertEquals(listOf(Fix.RESTART_SERVICE), fixes(healthy.copy(lastHeartbeat = now.minusSeconds(300))))
        assertEquals(listOf(Fix.RESTART_SERVICE), fixes(healthy.copy(lastHeartbeat = null)))
    }

    @Test
    fun loImprescindibleVaAntesQueLoRecomendable() {
        val problems = Troubleshooter.problems(healthy.copy(batteryUnrestricted = false, overlay = false, adminActive = false))
        assertEquals(listOf(Fix.OVERLAY, Fix.ADMIN, Fix.BATTERY), problems.map { it.fix })
        assertEquals(Severity.REQUIRED, problems.first().severity)
        // El administrador del dispositivo es lo que impide desinstalar la app: ya no es solo una recomendación.
        assertEquals(Severity.REQUIRED, problems.first { it.fix == Fix.ADMIN }.severity)
        assertEquals(Severity.RECOMMENDED, problems.first { it.fix == Fix.BATTERY }.severity)
    }

    @Test
    fun laUbicacionSoloSePideSiUnPerfilLaUsa() {
        assertTrue(Troubleshooter.problems(healthy).none { it.fix == Fix.LOCATION })
        assertEquals(listOf(Fix.LOCATION), fixes(healthy.copy(locationNeeded = true)))
        assertTrue(Troubleshooter.problems(healthy).none { it.fix == Fix.BLUETOOTH })
        assertEquals(listOf(Fix.BLUETOOTH), fixes(healthy.copy(bluetoothNeeded = true, bluetoothGranted = false)))
        assertTrue(fixes(healthy.copy(bluetoothNeeded = true, bluetoothGranted = true)).isEmpty())
        assertTrue(fixes(healthy.copy(locationNeeded = true, locationGranted = true)).isEmpty())
    }

    @Test
    fun ciertasMarcasPidenElInicioAutomatico() {
        assertEquals(listOf(Fix.AUTOSTART), fixes(healthy.copy(manufacturer = "Xiaomi")))
        assertEquals(listOf(Fix.AUTOSTART), fixes(healthy.copy(manufacturer = "HUAWEI")))
        assertTrue(fixes(healthy.copy(manufacturer = "samsung")).isEmpty())
    }
}
