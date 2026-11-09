package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.ConditionMode
import com.focusguard.model.DeviceContext
import com.focusguard.model.ForegroundState
import com.focusguard.model.GeoFence
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionsTest {

    private val monday10 = LocalDateTime.of(2026, 10, 26, 10, 0)
    private val inOffice = ScheduleInterval(DayOfWeek.MONDAY, 9 * 60, 17 * 60)
    private val outOfOffice = ScheduleInterval(DayOfWeek.MONDAY, 18 * 60, 20 * 60)
    private val madrid = DeviceContext(latitude = 40.4168, longitude = -3.7038)

    private fun met(profile: Profile, context: DeviceContext = DeviceContext()) = Conditions.triggersMet(profile, monday10, context)

    @Test
    fun laDistanciaEntreDosPuntosConocidosEsRazonable() {
        // Puerta del Sol a Plaza de Cibeles: unos 1,1 km.
        val d = Conditions.distanceMeters(40.4169, -3.7035, 40.4193, -3.6931)
        assertTrue(d in 800.0..1_400.0, "distancia $d")
        assertEquals(0.0, Conditions.distanceMeters(10.0, 20.0, 10.0, 20.0), 0.001)
    }

    @Test
    fun sinCondicionesElPerfilEstaSiempreActivo() {
        assertTrue(met(Profile(1, "p")))
        assertEquals(0, Conditions.count(Profile(1, "p")))
    }

    @Test
    fun bluetoothCoincideConUnDispositivoConectadoSinImportarMayusculas() {
        val p = Profile(1, "p", bluetoothNames = setOf("Auriculares Ana"))
        assertTrue(met(p, DeviceContext(bluetoothDevices = setOf("auriculares ana", "Coche"))))
        assertFalse(met(p, DeviceContext(bluetoothDevices = setOf("Coche"))))
        assertFalse(met(p, DeviceContext()))
    }

    @Test
    fun losEspaciosDeMasNoImpidenQueCoincida() {
        val p = Profile(1, "p", bluetoothNames = setOf("Mi  Coche "))
        assertTrue(met(p, DeviceContext(bluetoothDevices = setOf("mi coche"))))
        assertEquals("mi coche", Conditions.normalizeName(" Mi \u00A0 Coche "))
    }

    @Test
    fun sinPermisoDeBluetoothLaCondicionSeDaPorCumplida() {
        val p = Profile(1, "p", bluetoothNames = setOf("Coche"))
        assertTrue(met(p, DeviceContext(bluetoothAvailable = false)))
    }

    @Test
    fun bluetoothCuentaComoCondicionDelTelefono() {
        val p = Profile(1, "p", bluetoothNames = setOf("Coche"))
        assertEquals(1, Conditions.count(p))
        assertTrue(Conditions.needsDevice(listOf(p)))
        val enOficinaConCoche = Profile(1, "p", intervals = listOf(inOffice), bluetoothNames = setOf("Coche"), conditionMode = ConditionMode.ANY)
        assertTrue(met(enOficinaConCoche, DeviceContext(bluetoothDevices = setOf("Coche"))))
    }

    @Test
    fun wifiCoincideSinImportarMayusculasNiComillas() {
        val p = Profile(1, "p", wifiNames = setOf("Casa"))
        assertTrue(met(p, DeviceContext(wifiName = "\"casa\"")))
        assertFalse(met(p, DeviceContext(wifiName = "Oficina")))
        assertFalse(met(p, DeviceContext(wifiName = null))) // sin red legible, no se bloquea
    }

    @Test
    fun cargadorSoloSeCumpleMientrasCarga() {
        val p = Profile(1, "p", requireCharging = true)
        assertTrue(met(p, DeviceContext(charging = true)))
        assertFalse(met(p, DeviceContext(charging = false)))
    }

    @Test
    fun lugarDentroYFueraDelRadio() {
        val fence = GeoFence(40.4168, -3.7038, radiusMeters = 100)
        val p = Profile(1, "p", geofence = fence)
        assertTrue(met(p, madrid))
        assertFalse(met(p, DeviceContext(latitude = 40.4268, longitude = -3.7038))) // ~1,1 km al norte
    }

    @Test
    fun lugarInvertidoSeCumpleAlEstarFuera() {
        val p = Profile(1, "p", geofence = GeoFence(40.4168, -3.7038, 100, inverted = true))
        assertFalse(met(p, madrid))
        assertTrue(met(p, DeviceContext(latitude = 41.0, longitude = -3.7)))
    }

    @Test
    fun sinPosicionNoSeCumpleNiSiquieraInvertido() {
        val inverted = Profile(1, "p", geofence = GeoFence(40.4168, -3.7038, 100, inverted = true))
        assertFalse(met(inverted, DeviceContext()))
        assertFalse(met(Profile(2, "p", geofence = GeoFence(40.4168, -3.7038, 100)), DeviceContext()))
    }

    @Test
    fun conTodasHacenFaltaTodasLasDefinidas() {
        val p = Profile(1, "p", intervals = listOf(inOffice), wifiNames = setOf("Oficina"), conditionMode = ConditionMode.ALL)
        assertTrue(met(p, DeviceContext(wifiName = "Oficina")))
        assertFalse(met(p, DeviceContext(wifiName = "Casa")))
        assertFalse(met(p.copy(intervals = listOf(outOfOffice)), DeviceContext(wifiName = "Oficina")))
    }

    @Test
    fun conAlgunaBastaUnaCondicion() {
        val p = Profile(1, "p", intervals = listOf(outOfOffice), wifiNames = setOf("Oficina"), conditionMode = ConditionMode.ANY)
        assertTrue(met(p, DeviceContext(wifiName = "Oficina"))) // el horario no, pero el Wi-Fi sí
        assertFalse(met(p, DeviceContext(wifiName = "Casa")))
        assertEquals(2, Conditions.count(p))
    }

    @Test
    fun elPerfilDeshabilitadoOEnPausaNuncaSeActiva() {
        val now = Instant.parse("2026-10-26T10:00:00Z")
        assertFalse(ProfileActivation.isActive(Profile(1, "p", enabled = false), monday10, now))
        assertFalse(ProfileActivation.isActive(Profile(1, "p", pausedUntil = now.plusSeconds(60)), monday10, now))
        assertTrue(ProfileActivation.isActive(Profile(1, "p"), monday10, now))
    }

    @Test
    fun sinPermisoDeUbicacionLasCondicionesDeRedYLugarSeDanPorCumplidas() {
        val profile = Profile(1, "p", wifiNames = setOf("Casa"), geofence = GeoFence(40.0, -3.0))
        val noPermission = DeviceContext(locationAvailable = false)
        assertTrue(Conditions.triggersMet(profile, monday10, noPermission))
        assertFalse(Conditions.triggersMet(profile, monday10, DeviceContext()))
    }

    @Test
    fun cuentaLasCondicionesDefinidasSinEvaluarlas() {
        val base = Profile(1, "p")
        assertEquals(0, Conditions.count(base))
        assertEquals(4, Conditions.count(base.copy(intervals = listOf(inOffice), wifiNames = setOf("Casa"), requireCharging = true, geofence = GeoFence(40.0, -3.0))))
    }

    @Test
    fun soloLeeElTelefonoSiAlgunPerfilDependeDeEl() {
        val plain = Profile(1, "p", intervals = listOf(inOffice))
        assertFalse(Conditions.needsDevice(listOf(plain)))
        assertTrue(Conditions.needsDevice(listOf(plain, plain.copy(requireCharging = true))))
        assertTrue(Conditions.needsDevice(listOf(plain.copy(wifiNames = setOf("Casa")))))
        assertTrue(Conditions.needsDevice(listOf(plain.copy(geofence = GeoFence(40.0, -3.0)))))
    }

    @Test
    fun elDecisorUsaElContextoDelTelefono() {
        val decider = BlockDecider(neverBlock = emptySet(), zone = ZoneOffset.UTC)
        val profile = Profile(1, "p", apps = setOf("com.x"), wifiNames = setOf("Casa"))
        val now = Instant.parse("2026-10-26T10:00:00Z")
        val state = ForegroundState("com.x")
        assertEquals(BlockDecision.Allow, decider.decide(state, listOf(profile), now, context = DeviceContext(wifiName = "Oficina")))
        assertTrue(decider.decide(state, listOf(profile), now, context = DeviceContext(wifiName = "Casa")) is BlockDecision.Block)
    }
}
