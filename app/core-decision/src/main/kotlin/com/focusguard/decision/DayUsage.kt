package com.focusguard.decision

/** Lo que importa de los eventos de uso del sistema para las estadísticas (K1). */
enum class UsageEventKind { APP_RESUMED, APP_PAUSED, SCREEN_OFF, UNLOCK }

data class UsageEvent(val kind: UsageEventKind, val packageName: String?, val atMs: Long)

data class AppDayUsage(val packageName: String, val timeMs: Long, val opens: Int)

/** Uso de un día: por app, tiempo y aperturas, y cuántas veces se desbloqueó la pantalla. */
data class DayUsage(val apps: List<AppDayUsage>, val unlocks: Int) {
    /** Tiempo total de uso del día, sumando las apps. */
    val totalMs: Long get() = apps.sumOf { it.timeMs }
}

/** Un rato de uso del teléfono, de [startMs] a [endMs]. */
data class UseSpan(val startMs: Long, val endMs: Long)

/** El uso de un día con los ratos en que alguna app estuvo abierta, para los insights (K3). */
data class DayUsageDetail(val usage: DayUsage, val spans: List<UseSpan>)

object DayUsageCalculator {

    /**
     * Calcula el uso entre [startMs] y [endMs] con los [events] ordenados por hora, que pueden empezar antes
     * de [startMs] para saber qué app estaba abierta a medianoche. Lo que sigue abierto cuenta hasta [nowMs].
     * Una apertura cuenta si pasaron al menos [UsageAccumulator.OPEN_GAP_MS] desde que se dejó la app, igual que en los límites.
     */
    fun of(events: List<UsageEvent>, startMs: Long, endMs: Long, nowMs: Long, ignored: Set<String> = emptySet()): DayUsage =
        detail(events, startMs, endMs, nowMs, ignored).usage

    /** Como [of], con los ratos de uso (sin unir): cada vez que una app estuvo abierta. */
    fun detail(events: List<UsageEvent>, startMs: Long, endMs: Long, nowMs: Long, ignored: Set<String> = emptySet()): DayUsageDetail {
        val spans = mutableListOf<UseSpan>()
        val time = mutableMapOf<String, Long>()
        val opens = mutableMapOf<String, Int>()
        val openSince = mutableMapOf<String, Long>()
        val lastLeft = mutableMapOf<String, Long>()
        var unlocks = 0

        fun close(pkg: String, at: Long) {
            val since = openSince.remove(pkg) ?: return
            val span = minOf(at, endMs) - maxOf(since, startMs)
            if (span > 0) {
                time.merge(pkg, span, Long::plus)
                spans += UseSpan(maxOf(since, startMs), minOf(at, endMs))
            }
            lastLeft[pkg] = at
        }

        fun closeAll(at: Long) = openSince.keys.toList().forEach { close(it, at) }

        for (event in events) {
            if (event.atMs > endMs) break
            val pkg = event.packageName?.takeIf { it !in ignored }
            when (event.kind) {
                UsageEventKind.APP_RESUMED -> if (pkg != null && pkg !in openSince) {
                    val left = lastLeft[pkg]
                    if (event.atMs >= startMs && (left == null || event.atMs - left >= UsageAccumulator.OPEN_GAP_MS)) opens.merge(pkg, 1, Int::plus)
                    openSince[pkg] = event.atMs
                }
                UsageEventKind.APP_PAUSED -> if (pkg != null) close(pkg, event.atMs)
                UsageEventKind.SCREEN_OFF -> closeAll(event.atMs)
                UsageEventKind.UNLOCK -> if (event.atMs in startMs until endMs) unlocks++
            }
        }
        closeAll(minOf(nowMs, endMs))

        val apps = (time.keys + opens.keys).map { AppDayUsage(it, time[it] ?: 0, opens[it] ?: 0) }
            .filter { it.timeMs > 0 || it.opens > 0 }
            .sortedByDescending { it.timeMs }
        return DayUsageDetail(DayUsage(apps, unlocks), spans)
    }
}
