package com.focusguard.data

import android.content.pm.PackageManager

/**
 * Cuándo se instaló cada app por primera vez. Se pregunta al sistema una vez por paquete y se vacía cada
 * cierto tiempo por si una app se desinstala y se vuelve a instalar. Un fallo se recuerda solo unos segundos:
 * un paquete que el sistema no deja ver no debe repetir la consulta en cada evento, pero uno recién instalado
 * que aún no aparece tampoco debe quedar sin bloquear.
 */
class InstallTimes(private val packageManager: PackageManager) {

    private val known = mutableMapOf<String, Long>()
    private val failedAtMs = mutableMapOf<String, Long>()

    fun of(packageName: String, nowMs: Long): Long? {
        known[packageName]?.let { return it }
        failedAtMs[packageName]?.takeIf { nowMs - it < RETRY_MS }?.let { return null }
        val time = runCatching { packageManager.getPackageInfo(packageName, 0).firstInstallTime }.getOrNull()
        if (time == null) failedAtMs[packageName] = nowMs else known[packageName] = time
        return time
    }

    fun clear() {
        known.clear()
        failedAtMs.clear()
    }

    private companion object {
        const val RETRY_MS = 5_000L
    }
}
