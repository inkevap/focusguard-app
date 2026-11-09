package com.focusguard.data

import android.content.Context
import com.focusguard.decision.EmergencyAvailability
import com.focusguard.decision.EmergencyConfig
import com.focusguard.decision.EmergencyUnlock
import com.focusguard.decision.ProfileActivation
import com.focusguard.decision.StrictMode
import com.focusguard.model.Profile
import java.time.Duration
import java.time.Instant
import org.json.JSONArray

/**
 * Usos del desbloqueo de emergencia. Aquí se hace cumplir el cupo, el modo estricto y el pánico: quien conceda
 * un desbloqueo tiene que pasar por [grant], no por la pantalla.
 */
class EmergencyRepository(
    context: Context,
    private val strict: StrictRepository,
    private val clock: TrustedClock,
) {

    private val prefs = context.getSharedPreferences("emergency", Context.MODE_PRIVATE)

    /** El cupo es el de la especificación; hacerlo editable queda para más adelante (docs/PENDIENTES.md). */
    val config = EmergencyConfig()

    @Volatile
    private var grants: List<Instant> = readGrants()

    /** Hasta cuándo dura el último desbloqueo concedido. */
    @Volatile
    var activeUntil: Instant? = grants.maxOrNull()?.let { EmergencyUnlock.activeUntil(it, config) }
        private set

    /** Si el archivo no se puede leer se cuenta el cupo como gastado: ante la duda, más fricción y no menos. */
    private fun readGrants(): List<Instant> = runCatching {
        val array = JSONArray(prefs.getString(KEY_GRANTS, "[]"))
        (0 until array.length()).map { Instant.ofEpochMilli(array.getLong(it)) }
    }.getOrElse { List(config.perWeek) { clock.now() } }

    fun availability(now: Instant, profiles: List<Profile>): EmergencyAvailability = EmergencyUnlock.availability(
        grants, now, config,
        strictActive = StrictMode.isActive(strict.config.value, clock.localAt(now), now),
        panicActive = ProfileActivation.isPanicActive(profiles, now),
    )

    /** Registra un uso y devuelve hasta cuándo queda todo desbloqueado; null si ahora no se puede conceder. */
    @Synchronized
    fun grant(now: Instant, profiles: List<Profile>): Instant? {
        if (availability(now, profiles) !is EmergencyAvailability.Available) return null
        grants = grants.filter { it.isAfter(now - RETENTION) } + now
        prefs.edit().putString(KEY_GRANTS, JSONArray(grants.map { it.toEpochMilli() }).toString()).apply()
        return EmergencyUnlock.activeUntil(now, config).also { activeUntil = it }
    }

    private companion object {
        const val KEY_GRANTS = "grants"
        val RETENTION: Duration = Duration.ofDays(14)
    }
}
