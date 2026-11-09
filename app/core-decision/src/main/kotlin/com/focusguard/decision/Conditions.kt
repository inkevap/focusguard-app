package com.focusguard.decision

import com.focusguard.model.ConditionMode
import com.focusguard.model.DeviceContext
import com.focusguard.model.GeoFence
import com.focusguard.model.Profile
import java.time.LocalDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Condiciones de activación de un perfil: horario, Wi-Fi, cargador y lugar. Solo cuentan las definidas;
 * con ninguna, el perfil está siempre activo. Si el teléfono no sabe algo (sin posición, sin Wi-Fi legible),
 * esa condición no se cumple; pero sin permiso de ubicación se da por cumplida, para que retirarlo no apague el perfil.
 */
object Conditions {

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Distancia entre dos puntos, en metros (fórmula de haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    /** Compara nombres de redes y de dispositivos sin importar mayúsculas ni comillas, que Android añade a veces alrededor del nombre. */
    fun normalizeName(name: String) = name.trim().trim('"').replace(WHITESPACE, " ").lowercase()

    private val WHITESPACE = Regex("[\\s\\p{Z}]+")

    private fun wifiMet(names: Set<String>, context: DeviceContext): Boolean {
        if (!context.locationAvailable) return true
        val current = context.wifiName?.let(::normalizeName) ?: return false
        return names.any { normalizeName(it) == current }
    }

    /** Sin permiso de Bluetooth, o antes de saber qué hay conectado, la condición se da por cumplida: solo lleva a bloquear de más, nunca a dejar de bloquear. */
    private fun bluetoothMet(names: Set<String>, context: DeviceContext): Boolean {
        if (!context.bluetoothAvailable) return true
        val connected = context.bluetoothDevices.map(::normalizeName).toSet()
        return names.any { normalizeName(it) in connected }
    }

    private fun locationMet(fence: GeoFence, context: DeviceContext): Boolean {
        if (!context.locationAvailable) return true
        val lat = context.latitude ?: return false
        val lon = context.longitude ?: return false
        val inside = distanceMeters(fence.latitude, fence.longitude, lat, lon) <= fence.radiusMeters
        return inside != fence.inverted
    }

    /** Resultado de cada condición definida del perfil, en el orden horario, Wi-Fi, cargador, lugar. */
    private fun results(profile: Profile, localNow: LocalDateTime, context: DeviceContext): List<Boolean> = buildList {
        if (profile.intervals.isNotEmpty()) add(ScheduleEvaluator.isWithin(profile.intervals, localNow))
        if (profile.wifiNames.isNotEmpty()) add(wifiMet(profile.wifiNames, context))
        if (profile.bluetoothNames.isNotEmpty()) add(bluetoothMet(profile.bluetoothNames, context))
        if (profile.requireCharging) add(context.charging)
        profile.geofence?.let { add(locationMet(it, context)) }
    }

    fun triggersMet(profile: Profile, localNow: LocalDateTime, context: DeviceContext): Boolean {
        if (count(profile) == 0) return true
        val results = results(profile, localNow, context)
        return when (profile.conditionMode) {
            ConditionMode.ALL -> results.all { it }
            ConditionMode.ANY -> results.any { it }
        }
    }

    /** Cuántas condiciones tiene definidas el perfil (para decidir si tiene sentido elegir todas o alguna). */
    fun count(profile: Profile): Int =
        listOf(profile.intervals.isNotEmpty(), profile.wifiNames.isNotEmpty(), profile.bluetoothNames.isNotEmpty(), profile.requireCharging, profile.geofence != null)
            .count { it }

    /** ¿Algún perfil depende de la red, el cargador o el lugar? Si no, no hace falta leer el estado del teléfono. */
    fun needsDevice(profiles: List<Profile>): Boolean =
        profiles.any { it.wifiNames.isNotEmpty() || it.bluetoothNames.isNotEmpty() || it.requireCharging || it.geofence != null || EndRules.watches(it) }
}
