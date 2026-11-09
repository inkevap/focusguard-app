package com.focusguard.data

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.focusguard.decision.ClockAnchor
import com.focusguard.decision.ClockGuard
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Hora para esperas, retardos, horarios y límites. Descarta los saltos del reloj del teléfono
 * (ver [ClockGuard]) para que cambiar la fecha a mano no acorte una espera.
 */
class TrustedClock(private val context: Context) {

    private val prefs = context.getSharedPreferences("clock", Context.MODE_PRIVATE)
    private var anchor: ClockAnchor? = readAnchor()
    private var lastSavedElapsedMs = 0L
    private var autoTime = false
    private var lastAutoReadMs = -AUTO_READ_EVERY_MS

    @Synchronized
    fun nowMs(): Long {
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastAutoReadMs >= AUTO_READ_EVERY_MS) {
            lastAutoReadMs = elapsed
            autoTime = Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0) == 1
        }
        val next = ClockGuard.step(anchor, System.currentTimeMillis(), elapsed, bootId, autoTime)
        val previous = anchor
        anchor = next
        // Se guarda al cambiar el desfase y de vez en cuando, para sobrevivir a un reinicio.
        if (next.offsetMs != previous?.offsetMs || elapsed - lastSavedElapsedMs >= SAVE_EVERY_MS) {
            lastSavedElapsedMs = elapsed
            save(next)
        }
        return next.trustedMs
    }

    fun now(): Instant = Instant.ofEpochMilli(nowMs())

    fun localAt(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())

    // El contador de arranques no cambia mientras el proceso vive.
    private val bootId: Long by lazy { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0).toLong() }

    private fun readAnchor(): ClockAnchor? {
        if (!prefs.contains(KEY_WALL)) return null
        return ClockAnchor(
            wallMs = prefs.getLong(KEY_WALL, 0),
            elapsedMs = prefs.getLong(KEY_ELAPSED, 0),
            bootId = prefs.getLong(KEY_BOOT, 0),
            offsetMs = prefs.getLong(KEY_OFFSET, 0),
        )
    }

    private fun save(anchor: ClockAnchor) {
        prefs.edit()
            .putLong(KEY_WALL, anchor.wallMs).putLong(KEY_ELAPSED, anchor.elapsedMs)
            .putLong(KEY_BOOT, anchor.bootId).putLong(KEY_OFFSET, anchor.offsetMs)
            .apply()
    }

    private companion object {
        const val KEY_WALL = "wall"
        const val KEY_ELAPSED = "elapsed"
        const val KEY_BOOT = "boot"
        const val KEY_OFFSET = "offset"
        const val SAVE_EVERY_MS = 60_000L
        const val AUTO_READ_EVERY_MS = 1_000L
    }
}
