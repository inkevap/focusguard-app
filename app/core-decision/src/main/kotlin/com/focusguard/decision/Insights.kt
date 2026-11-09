package com.focusguard.decision

import com.focusguard.model.BlockEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Cómo va el día frente a la media de los anteriores a la misma hora (K6). */
enum class Trend { BETTER, SAME, WORSE, NO_DATA }

/** Cuánto ha cambiado una cifra respecto a la anterior: el porcentaje redondeado, o null si no hay con qué comparar. */
data class Change(val percent: Int?)

/** Lo que dicen del día los ratos de uso: el más largo seguido, el descanso más largo y las sesiones de bloqueo (K3). */
data class DayInsight(val longestUseMs: Long, val longestRestMs: Long, val blockSessions: Int)

/** Lo que cuentan de un día el informe semanal y la comparación. Un día sin uso ni desbloqueos se toma como «sin datos» (el sistema ya no lo guarda). */
data class DayFigures(val day: LocalDate, val usageMs: Long, val unlocks: Int, val blocks: Int) {
    val hasData: Boolean get() = usageMs > 0 || unlocks > 0
}

data class WeekTotals(val usageMs: Long, val unlocks: Int, val blocks: Int, val days: Int) {
    val averageUsageMs: Long get() = if (days == 0) 0 else usageMs / days
}

/** Informe semanal (K4): los siete días ya terminados frente a los siete anteriores. */
data class WeeklyReport(
    val thisWeek: WeekTotals,
    val lastWeek: WeekTotals,
    /** Sin cambio si de la semana anterior hay menos de [Insights.MIN_HISTORY_DAYS] días con datos. */
    val usageChange: Change,
    val bestDay: DayFigures?,
    val worstDay: DayFigures?,
)

/** Todo lo de Estadísticas de un vistazo. [computed] son las cifras de los días terminados calculadas con eventos, para guardarlas. */
data class OverviewData(val trend: Trend, val todayMs: Long, val dayChange: Change, val computed: List<DayFigures>, val report: WeeklyReport)

/**
 * Insights de uso (K3, K4, K6). Todo sale de datos que ya se guardan en el teléfono: nada de esto va al servidor.
 */
object Insights {
    /** Dos ratos de uso con menos de esto entre medias se cuentan como uno seguido (mirar el teléfono, soltarlo un segundo y volver). */
    const val SAME_USE_GAP_MS = 60_000L

    /** Dos bloqueos con menos de esto entre medias son una misma sesión de bloqueo. */
    const val SAME_SESSION_GAP_MS = 10 * 60_000L

    /** Menos de esta diferencia con la media se considera igual que siempre. */
    private const val TREND_MARGIN = 0.10

    /** Con menos días con uso que esto no hay media que valga. */
    const val MIN_HISTORY_DAYS = 3

    private const val WEEK = 7L

    /** Se mira un rato antes de la medianoche para saber qué app seguía abierta. */
    const val LOOKBACK_MS = 6 * 3_600_000L

    /** Une los ratos que están a menos de [SAME_USE_GAP_MS]. */
    fun merge(spans: List<UseSpan>): List<UseSpan> {
        val merged = mutableListOf<UseSpan>()
        for (span in spans.sortedBy { it.startMs }) {
            val last = merged.lastOrNull()
            if (last != null && span.startMs - last.endMs < SAME_USE_GAP_MS) merged[merged.lastIndex] = UseSpan(last.startMs, maxOf(last.endMs, span.endMs))
            else merged += span
        }
        return merged
    }

    /**
     * Del día: el rato de uso seguido más largo, el descanso más largo (entre el primer y el último uso: la noche no cuenta como descanso)
     * y las sesiones de bloqueo.
     */
    fun day(spans: List<UseSpan>, blocks: List<BlockEvent>, startMs: Long, endMs: Long): DayInsight {
        val merged = merge(spans)
        return DayInsight(
            longestUseMs = merged.maxOfOrNull { it.endMs - it.startMs } ?: 0,
            longestRestMs = merged.zipWithNext { a, b -> b.startMs - a.endMs }.maxOrNull() ?: 0,
            blockSessions = blockSessions(blocks, startMs, endMs),
        )
    }

    fun blockSessions(blocks: List<BlockEvent>, startMs: Long, endMs: Long): Int {
        var sessions = 0
        var lastMs: Long? = null
        for (at in blocks.map { it.at.toEpochMilli() }.filter { it in startMs until endMs }.sorted()) {
            if (lastMs == null || at - lastMs >= SAME_SESSION_GAP_MS) sessions++
            lastMs = at
        }
        return sessions
    }

    fun change(current: Long, previous: Long): Change =
        if (previous <= 0) Change(null) else Change(Math.round((current - previous) * 100.0 / previous).toInt())

    /**
     * Hoy hasta ahora frente a la media de los días anteriores hasta esta misma hora ([sameTimePrevious], un valor por día con datos).
     * Una diferencia de menos del 10 % es «como siempre».
     */
    fun trend(todaySoFarMs: Long, sameTimePrevious: List<Long>): Trend {
        if (sameTimePrevious.size < MIN_HISTORY_DAYS) return Trend.NO_DATA
        val average = sameTimePrevious.average()
        if (average <= 0.0) return Trend.NO_DATA
        return when {
            todaySoFarMs < average * (1 - TREND_MARGIN) -> Trend.BETTER
            todaySoFarMs > average * (1 + TREND_MARGIN) -> Trend.WORSE
            else -> Trend.SAME
        }
    }

    /**
     * Las cifras de [days] (solo los días con datos) a [today]: los siete días terminados contra los siete anteriores. Hoy no entra
     * porque aún no ha acabado. Se comparan los promedios por día con datos, y no hay cambio si de la semana anterior hay pocos.
     */
    fun weekly(days: List<DayFigures>, today: LocalDate): WeeklyReport {
        val withData = days.filter { it.hasData }
        val thisWeek = withData.filter { it.day >= today.minusDays(WEEK) && it.day < today }
        val lastWeek = withData.filter { it.day >= today.minusDays(2 * WEEK) && it.day < today.minusDays(WEEK) }
        val current = totals(thisWeek)
        val previous = totals(lastWeek)
        return WeeklyReport(
            current, previous,
            usageChange = if (previous.days < MIN_HISTORY_DAYS) Change(null) else change(current.averageUsageMs, previous.averageUsageMs),
            bestDay = thisWeek.filter { it.usageMs > 0 }.minByOrNull { it.usageMs }, worstDay = thisWeek.maxByOrNull { it.usageMs }?.takeIf { it.usageMs > 0 },
        )
    }

    /**
     * Estadísticas de un vistazo con los eventos de uso del sistema (que ya no guardan más de una semana más o menos), los bloqueos por
     * día y las cifras de días más antiguos guardadas antes ([stored]). Todo con la misma hora, [now], y el mismo día natural.
     */
    fun overview(
        events: List<UsageEvent>,
        blocksPerDay: Map<LocalDate, Int>,
        stored: Map<LocalDate, DayFigures>,
        now: Instant,
        zone: ZoneId,
        ignored: Set<String>,
        unlocksSupported: Boolean,
    ): OverviewData {
        val today = now.atZone(zone).toLocalDate()
        val nowMs = now.toEpochMilli()
        fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
        fun usageOf(day: LocalDate, endMs: Long) =
            DayUsageCalculator.of(slice(events, startOf(day) - LOOKBACK_MS, endMs), startOf(day), endMs, minOf(endMs, nowMs), ignored)

        val computed = (0 until 2 * WEEK.toInt() + 1).map { today.minusDays(it.toLong()) }.map { day ->
            val usage = usageOf(day, startOf(day.plusDays(1)))
            DayFigures(day, usage.totalMs, usage.unlocks.takeIf { unlocksSupported } ?: 0, blocksPerDay[day] ?: 0)
        }.filter { it.hasData }
        val finished = computed.filter { it.day < today }

        // Hoy hasta ahora frente a cada uno de los días anteriores hasta esta misma hora del reloj.
        val timeOfDay = now.atZone(zone).toLocalTime()
        fun soFar(day: LocalDate) = usageOf(day, day.atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()).totalMs
        val todayMs = computed.firstOrNull { it.day == today }?.usageMs ?: 0
        val recent = finished.filter { it.day >= today.minusDays(WEEK) }
        return OverviewData(
            trend = trend(todayMs, recent.map { soFar(it.day) }),
            todayMs = todayMs,
            dayChange = change(todayMs, soFar(today.minusDays(1))),
            computed = finished,
            report = weekly(mergeFigures(stored, finished), today),
        )
    }

    /**
     * Las cifras guardadas y las calculadas ahora. Si de un día hay de las dos, vale la mayor: el día más antiguo de la ventana puede
     * tener solo la parte final de sus eventos (el sistema ya olvidó el resto) y su total parcial no debe pisar el completo guardado.
     */
    fun mergeFigures(stored: Map<LocalDate, DayFigures>, computed: List<DayFigures>): List<DayFigures> {
        val merged = stored.toMutableMap()
        computed.forEach { new -> if ((merged[new.day]?.usageMs ?: -1L) < new.usageMs) merged[new.day] = new }
        return merged.values.toList()
    }

    /** Los eventos entre [fromMs] y [toMs] de una lista ordenada por hora, sin recorrerla entera. */
    private fun slice(events: List<UsageEvent>, fromMs: Long, toMs: Long): List<UsageEvent> {
        fun firstAtOrAfter(ms: Long): Int {
            var low = 0
            var high = events.size
            while (low < high) {
                val mid = (low + high) ushr 1
                if (events[mid].atMs < ms) low = mid + 1 else high = mid
            }
            return low
        }
        return events.subList(firstAtOrAfter(fromMs), firstAtOrAfter(toMs + 1))
    }

    private fun totals(days: List<DayFigures>) = WeekTotals(days.sumOf { it.usageMs }, days.sumOf { it.unlocks }, days.sumOf { it.blocks }, days.size)
}
