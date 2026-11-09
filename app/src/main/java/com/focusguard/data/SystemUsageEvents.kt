package com.focusguard.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.focusguard.decision.UsageEvent
import com.focusguard.decision.UsageEventKind

/** Eventos de uso que guarda el sistema (abrir y dejar apps, apagar la pantalla, desbloquear), ya traducidos. */
class SystemUsageEvents(private val context: Context) {

    /** El sistema solo registra los desbloqueos desde Android 9. */
    val unlocksSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** Eventos entre [startMs] y [endMs] por orden; null si falta el acceso de uso. */
    fun between(startMs: Long, endMs: Long): List<UsageEvent>? {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val raw = manager.queryEvents(startMs, endMs)
        val events = mutableListOf<UsageEvent>()
        val event = UsageEvents.Event()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(event)
            val kind = when (event.eventType) {
                RESUMED -> UsageEventKind.APP_RESUMED
                PAUSED -> UsageEventKind.APP_PAUSED
                SCREEN_NON_INTERACTIVE -> UsageEventKind.SCREEN_OFF
                KEYGUARD_HIDDEN -> UsageEventKind.UNLOCK
                else -> continue
            }
            events += UsageEvent(kind, event.packageName, event.timeStamp)
        }
        // Sin acceso, el sistema devuelve vacío igual que un día sin uso: se distingue pidiendo el agregado.
        if (events.isEmpty() && manager.queryAndAggregateUsageStats(endMs - ACCESS_PROBE_MS, endMs).isEmpty()) return null
        return events
    }

    private companion object {
        /** Ventana con la que se comprueba que hay acceso de uso: el agregado de la última semana no está vacío si lo hay. */
        const val ACCESS_PROBE_MS = 7 * 86_400_000L

        // Los mismos números que MOVE_TO_FOREGROUND, MOVE_TO_BACKGROUND, SCREEN_NON_INTERACTIVE y KEYGUARD_HIDDEN,
        // que solo existen en las versiones recientes del SDK.
        const val RESUMED = 1
        const val PAUSED = 2
        const val SCREEN_NON_INTERACTIVE = 16
        const val KEYGUARD_HIDDEN = 18
    }
}
