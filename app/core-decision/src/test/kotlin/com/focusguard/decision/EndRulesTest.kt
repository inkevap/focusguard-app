package com.focusguard.decision

import com.focusguard.model.DeviceContext
import com.focusguard.model.EndConditions
import com.focusguard.model.EndPolicy
import com.focusguard.model.GeoFence
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EndRulesTest {

    private val now = Instant.parse("2026-10-29T10:00:00Z")
    private val home = GeoFence(14.6349, -90.5069, 150)

    private fun block(ends: EndConditions, policy: EndPolicy = EndPolicy.CONDITIONS, kind: ProfileKind = ProfileKind.QUICK_BLOCK) =
        Profile(1, "Bloquear ya", kind = kind, activeUntil = now.plusSeconds(3600), endPolicy = policy, endConditions = ends)

    @Test fun elCargadorLevantaElBloqueo() {
        val p = block(EndConditions(charging = true))
        assertFalse(EndRules.met(p, DeviceContext(charging = false)))
        assertTrue(EndRules.met(p, DeviceContext(charging = true)))
    }

    @Test fun llegarAlLugarLevantaElBloqueoSoloDentroDelRadio() {
        val p = block(EndConditions(place = home))
        assertFalse(EndRules.met(p, DeviceContext(latitude = 14.7, longitude = -90.5)))
        assertTrue(EndRules.met(p, DeviceContext(latitude = 14.6350, longitude = -90.5070)))
    }

    @Test fun sinPosicionOSinPermisoDeUbicacionElLugarNoSeDaPorCumplido() {
        val p = block(EndConditions(place = home))
        assertFalse(EndRules.met(p, DeviceContext(latitude = null, longitude = null)))
        // Con una condición de activación esto se daría por cumplido; aquí abriría el bloqueo al quitar el permiso.
        assertFalse(EndRules.met(p, DeviceContext(locationAvailable = false, latitude = 14.6349, longitude = -90.5069)))
    }

    @Test fun laRedWifiSeComparaSinMayusculasNiComillas() {
        val p = block(EndConditions(wifiNames = setOf("Casa Wifi")))
        assertTrue(EndRules.met(p, DeviceContext(wifiName = "\"casa wifi\"")))
        assertFalse(EndRules.met(p, DeviceContext(wifiName = "Cafe")))
        assertFalse(EndRules.met(p, DeviceContext(wifiName = null)))
        assertFalse(EndRules.met(p, DeviceContext(wifiName = "Casa Wifi", locationAvailable = false)))
    }

    @Test fun elBluetoothSinPermisoNoSeDaPorCumplido() {
        val p = block(EndConditions(bluetoothNames = setOf("Coche")))
        assertTrue(EndRules.met(p, DeviceContext(bluetoothDevices = setOf("coche"))))
        assertFalse(EndRules.met(p, DeviceContext(bluetoothDevices = setOf("Auriculares"))))
        assertFalse(EndRules.met(p, DeviceContext(bluetoothAvailable = false)))
    }

    @Test fun bastaConUnaCondicion() {
        val p = block(EndConditions(charging = true, wifiNames = setOf("Casa")))
        assertTrue(EndRules.met(p, DeviceContext(charging = false, wifiName = "Casa")))
        assertTrue(EndRules.met(p, DeviceContext(charging = true, wifiName = "Otra")))
        assertFalse(EndRules.met(p, DeviceContext(charging = false, wifiName = "Otra")))
    }

    @Test fun laEsperaSolaNoLevantaNada() {
        val p = block(EndConditions(waitUntil = now.plusSeconds(600)))
        assertFalse(EndRules.watches(p))
        assertFalse(EndRules.met(p, DeviceContext(charging = true)))
    }

    @Test fun soloSeVigilaUnBloquearYConSalidaPorCondiciones() {
        val ends = EndConditions(charging = true)
        assertFalse(EndRules.met(block(ends, EndPolicy.ANYTIME), DeviceContext(charging = true)))
        assertFalse(EndRules.met(block(ends, EndPolicy.TIME_ONLY), DeviceContext(charging = true)))
        // Ayúdame y Dormir nunca terminan antes, ni aunque llevaran condiciones.
        assertFalse(EndRules.met(block(ends, kind = ProfileKind.PANIC), DeviceContext(charging = true)))
    }

    @Test fun avisaDeLoQueYaSeCumpleAlEmpezar() {
        val p = block(EndConditions(charging = true, wifiNames = setOf("Casa"), place = home))
        val met = EndRules.alreadyMet(p, DeviceContext(charging = true, wifiName = "Casa"))
        assertEquals(listOf("el cargador conectado", "la red Wi-Fi"), met)
        assertTrue(EndRules.alreadyMet(p, DeviceContext(wifiName = "Otra")).isEmpty())
    }

    @Test fun losPerfilesVigiladosPidenLeerElTelefono() {
        val vigilado = block(EndConditions(charging = true))
        val libre = block(EndConditions(charging = true), EndPolicy.ANYTIME)
        assertTrue(Conditions.needsDevice(listOf(vigilado)))
        assertFalse(Conditions.needsDevice(listOf(libre)))
        assertTrue(EndRules.watchesPlace(listOf(block(EndConditions(place = home)))))
        assertFalse(EndRules.watchesPlace(listOf(vigilado)))
        assertTrue(EndRules.watchesBluetooth(listOf(block(EndConditions(bluetoothNames = setOf("Coche"))))))
    }

    @Test fun nadieMasQueElBloquearYSinSalidaEsUnBloqueoTotal() {
        val soloTiempo = block(EndConditions(), EndPolicy.TIME_ONLY)
        assertEquals(null, ProfileActivation.activePanic(listOf(soloTiempo), now))
        assertEquals("Dormir", ProfileActivation.activePanic(listOf(soloTiempo, Profile.panic(now.plusSeconds(60), "Dormir").copy(id = 2)), now)?.title)
    }

    @Test fun cadaPoliticaDiceSiSePuedeTerminarYCuando() {
        val libre = block(EndConditions(), EndPolicy.ANYTIME)
        assertTrue(libre.canEndEarly)
        assertTrue(libre.canEndNow(now))

        val soloTiempo = block(EndConditions(), EndPolicy.TIME_ONLY)
        assertFalse(soloTiempo.canEndEarly)
        assertFalse(soloTiempo.canEndNow(now.plusSeconds(3000)))

        // Con condiciones del teléfono y sin espera no hay botón: solo se levanta cuando se cumple una.
        val sinEspera = block(EndConditions(charging = true))
        assertFalse(sinEspera.canEndEarly)
        assertFalse(sinEspera.canEndNow(now.plusSeconds(3000)))

        // Con espera, «Terminar» existe pero solo pasado ese momento.
        val conEspera = block(EndConditions(waitUntil = now.plusSeconds(1800)))
        assertTrue(conEspera.canEndEarly)
        assertFalse(conEspera.canEndNow(now.plusSeconds(1799)))
        assertTrue(conEspera.canEndNow(now.plusSeconds(1800)))
    }

    @Test fun laPoliticaYLasCondicionesSeConservanAlEditarUnPerfil() {
        val p = block(EndConditions(charging = true), EndPolicy.CONDITIONS)
        val edited = p.withEditableFieldsFrom(p.copy(title = "Otro", endPolicy = EndPolicy.ANYTIME, endConditions = EndConditions()))
        assertEquals(EndPolicy.CONDITIONS, edited.endPolicy)
        assertTrue(edited.endConditions.charging)
    }
}
