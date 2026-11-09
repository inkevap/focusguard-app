package com.focusguard.decision

import java.time.Duration
import java.time.Instant

/** Qué hay que arreglar para que FocusGuard bloquee de verdad. La pantalla lo traduce en un botón. */
enum class Fix { ACCESSIBILITY, RESTART_SERVICE, USAGE_STATS, OVERLAY, BATTERY, ADMIN, LOCATION, BLUETOOTH, NOTIFICATIONS, AUTOSTART }

enum class Severity { REQUIRED, RECOMMENDED }

/** Qué falta y cuánto importa; los textos los pone la pantalla. */
data class Problem(val fix: Fix, val severity: Severity)

/** Lo que el teléfono dice ahora sobre permisos y sobre el propio servicio. */
data class HealthSnapshot(
    val accessibilityEnabled: Boolean,
    val usageStats: Boolean,
    val overlay: Boolean,
    val batteryUnrestricted: Boolean,
    val adminActive: Boolean,
    val notificationsEnabled: Boolean,
    /** Algún perfil depende de una red Wi-Fi o de un lugar. */
    val locationNeeded: Boolean,
    val locationGranted: Boolean,
    /** Algún perfil depende de un dispositivo Bluetooth conectado. */
    val bluetoothNeeded: Boolean = false,
    val bluetoothGranted: Boolean = true,
    /** Última señal de vida del servicio; null si nunca ha dado una. */
    val lastHeartbeat: Instant?,
    val now: Instant,
    val manufacturer: String,
)

/** Solucionador de problemas (O2): la lista de lo que falta, de lo imprescindible a lo recomendable. */
object Troubleshooter {

    /** El servicio da señal cada 15 s; pasado un minuto sin señal se considera parado. */
    val HEARTBEAT_STALE: Duration = Duration.ofSeconds(60)

    /** Fabricantes que matan las apps en segundo plano salvo que se les dé el inicio automático. */
    private val AGGRESSIVE_MAKERS = listOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "vivo", "oneplus", "asus", "tecno", "infinix")

    fun problems(s: HealthSnapshot): List<Problem> = buildList {
        if (!s.accessibilityEnabled) {
            add(Problem(Fix.ACCESSIBILITY, Severity.REQUIRED))
        } else if (s.lastHeartbeat == null || Duration.between(s.lastHeartbeat, s.now) > HEARTBEAT_STALE) {
            add(Problem(Fix.RESTART_SERVICE, Severity.REQUIRED))
        }
        if (!s.usageStats) add(Problem(Fix.USAGE_STATS, Severity.REQUIRED))
        if (!s.overlay) add(Problem(Fix.OVERLAY, Severity.REQUIRED))
        if (!s.batteryUnrestricted) add(Problem(Fix.BATTERY, Severity.RECOMMENDED))
        if (s.locationNeeded && !s.locationGranted) add(Problem(Fix.LOCATION, Severity.RECOMMENDED))
        if (s.bluetoothNeeded && !s.bluetoothGranted) add(Problem(Fix.BLUETOOTH, Severity.RECOMMENDED))
        if (!s.adminActive) add(Problem(Fix.ADMIN, Severity.REQUIRED))
        if (!s.notificationsEnabled) add(Problem(Fix.NOTIFICATIONS, Severity.RECOMMENDED))
        if (AGGRESSIVE_MAKERS.any { s.manufacturer.contains(it, ignoreCase = true) }) add(Problem(Fix.AUTOSTART, Severity.RECOMMENDED))
    }.sortedBy { it.severity }
}
