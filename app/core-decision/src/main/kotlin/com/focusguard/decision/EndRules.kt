package com.focusguard.decision

import com.focusguard.model.DeviceContext
import com.focusguard.model.EndPolicy
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind

/**
 * Cuándo un «Bloquear ya» elegido con salida por condiciones se levanta solo. A diferencia de las condiciones que activan un perfil, aquí lo que
 * el teléfono no sabe cuenta como **no cumplido**: sin permiso de ubicación o de Bluetooth, o sin posición todavía, el bloqueo sigue.
 * Dar una salida por buena sin saberlo dejaría quitar el bloqueo retirando un permiso.
 */
object EndRules {

    /** ¿Este perfil se levanta con lo que lee el teléfono? Solo un Bloquear ya con salida por condiciones. */
    fun watches(profile: Profile): Boolean =
        profile.kind == ProfileKind.QUICK_BLOCK && profile.endPolicy == EndPolicy.CONDITIONS && profile.endConditions.readsDevice

    /** ¿Se cumple alguna salida que lee el teléfono? La espera no cuenta: no levanta el bloqueo, solo permite pedirlo. */
    fun met(profile: Profile, context: DeviceContext): Boolean {
        if (!watches(profile)) return false
        val ends = profile.endConditions
        if (ends.charging && context.charging) return true
        if (ends.place?.let { placeMet(it.latitude, it.longitude, it.radiusMeters, context) } == true) return true
        if (ends.wifiNames.isNotEmpty() && wifiMet(ends.wifiNames, context)) return true
        if (ends.bluetoothNames.isNotEmpty() && bluetoothMet(ends.bluetoothNames, context)) return true
        return false
    }

    /** Lo que ya se cumple al empezar, con el nombre que se le enseña al usuario: así el bloqueo no acabaría en el mismo instante. */
    fun alreadyMet(profile: Profile, context: DeviceContext): List<String> {
        if (!watches(profile)) return emptyList()
        val ends = profile.endConditions
        return buildList {
            if (ends.charging && context.charging) add("el cargador conectado")
            if (ends.place?.let { placeMet(it.latitude, it.longitude, it.radiusMeters, context) } == true) add("estar en ese lugar")
            if (ends.wifiNames.isNotEmpty() && wifiMet(ends.wifiNames, context)) add("la red Wi-Fi")
            if (ends.bluetoothNames.isNotEmpty() && bluetoothMet(ends.bluetoothNames, context)) add("el dispositivo Bluetooth")
        }
    }

    fun watchesBluetooth(profiles: List<Profile>): Boolean = profiles.any { watches(it) && it.endConditions.bluetoothNames.isNotEmpty() }

    fun watchesPlace(profiles: List<Profile>): Boolean = profiles.any { watches(it) && it.endConditions.place != null }

    private fun placeMet(latitude: Double, longitude: Double, radiusMeters: Int, context: DeviceContext): Boolean {
        if (!context.locationAvailable) return false
        val lat = context.latitude ?: return false
        val lon = context.longitude ?: return false
        return Conditions.distanceMeters(latitude, longitude, lat, lon) <= radiusMeters
    }

    private fun wifiMet(names: Set<String>, context: DeviceContext): Boolean {
        if (!context.locationAvailable) return false
        val current = context.wifiName?.let(Conditions::normalizeName) ?: return false
        return names.any { Conditions.normalizeName(it) == current }
    }

    private fun bluetoothMet(names: Set<String>, context: DeviceContext): Boolean {
        if (!context.bluetoothAvailable) return false
        val connected = context.bluetoothDevices.map(Conditions::normalizeName).toSet()
        return names.any { Conditions.normalizeName(it) in connected }
    }
}
