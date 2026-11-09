package com.focusguard.decision

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Un impulso ya terminado, lo que hace falta para el informe (L7). */
data class UrgeRow(val startedMs: Long, val outcome: UrgeOutcome, val trigger: UrgeTrigger?)

/** Una semana (de lunes a domingo) con sus impulsos y cuántos se pasaron. */
data class UrgeWeek(val start: LocalDate, val total: Int, val passed: Int)

/** El informe de impulsos (L7): cuándo llegan, cómo acaban y qué los desencadena. Solo cuenta los terminados. */
data class UrgeReport(
    val total: Int,
    val passed: Int,
    val gaveIn: Int,
    val abandoned: Int,
    /** Impulsos por hora del día (0 a 23). */
    val byHour: List<Int>,
    /** Impulsos por día de la semana, de lunes (0) a domingo (6). */
    val byWeekday: List<Int>,
    /** Lo que el usuario dijo que los desencadenó, de más a menos; los que no se contestaron no cuentan. */
    val triggers: List<Pair<UrgeTrigger, Int>>,
    /** Las últimas semanas, de la más antigua a la más reciente. */
    val weeks: List<UrgeWeek>,
) {
    /** La hora más frecuente, o `null` si aún hay pocos impulsos para hablar de un patrón. */
    val peakHour: Int? get() = byHour.takeIf { total >= UrgeReports.MIN_FOR_PATTERNS }?.let { counts -> counts.indices.maxByOrNull { counts[it] * 100 - it } }

    val peakWeekday: DayOfWeek? get() = byWeekday.takeIf { total >= UrgeReports.MIN_FOR_PATTERNS }?.let { counts -> DayOfWeek.of(counts.indices.maxByOrNull { counts[it] * 100 - it }!! + 1) }

    val topTrigger: UrgeTrigger? get() = triggers.firstOrNull()?.first

    /** Qué parte de los impulsos se pasaron (de 0 a 1); `null` sin impulsos. */
    val passRate: Double? get() = if (total == 0) null else passed.toDouble() / total
}

object UrgeReports {
    /** Con menos impulsos que estos no se habla de «la hora más frecuente»: tres puntos no son un patrón. */
    const val MIN_FOR_PATTERNS = 3

    /** Cuántas semanas enseña la evolución. */
    const val WEEKS_SHOWN = 8

    /** Hasta dónde mira el informe hacia atrás: las semanas que enseña. */
    fun sinceDay(today: LocalDate): LocalDate = mondayOf(today).minusWeeks((WEEKS_SHOWN - 1).toLong())

    fun mondayOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** El informe de [rows] hasta [today] (inclusive). Los impulsos en curso o de fuera del periodo no cuentan. */
    fun build(rows: List<UrgeRow>, today: LocalDate, zone: ZoneId): UrgeReport {
        val first = sinceDay(today)
        val inRange = rows.filter { it.outcome != UrgeOutcome.ACTIVE }.map { it to localDay(it.startedMs, zone) }.filter { (_, day) -> !day.isBefore(first) && !day.isAfter(today) }
        val byHour = IntArray(24)
        val byWeekday = IntArray(7)
        val triggers = HashMap<UrgeTrigger, Int>()
        val weeks = (0 until WEEKS_SHOWN).map { first.plusWeeks(it.toLong()) }.associateWith { intArrayOf(0, 0) }
        var passed = 0
        var gaveIn = 0
        var abandoned = 0
        for ((row, day) in inRange) {
            byHour[Instant.ofEpochMilli(row.startedMs).atZone(zone).hour]++
            byWeekday[day.dayOfWeek.value - 1]++
            row.trigger?.let { triggers.merge(it, 1, Int::plus) }
            when (row.outcome) {
                UrgeOutcome.PASSED -> passed++
                UrgeOutcome.GAVE_IN -> gaveIn++
                else -> abandoned++
            }
            weeks.getValue(mondayOf(day)).let { counts ->
                counts[0]++
                if (row.outcome == UrgeOutcome.PASSED) counts[1]++
            }
        }
        return UrgeReport(
            total = inRange.size, passed = passed, gaveIn = gaveIn, abandoned = abandoned,
            byHour = byHour.toList(), byWeekday = byWeekday.toList(),
            triggers = triggers.entries.sortedWith(compareByDescending<Map.Entry<UrgeTrigger, Int>> { it.value }.thenBy { it.key.ordinal }).map { it.key to it.value },
            weeks = weeks.map { (start, counts) -> UrgeWeek(start, counts[0], counts[1]) },
        )
    }

    /** Si la semana que empieza el [monday] aún no se ha contado al compañero; [lastCounted] es el día (época) del lunes de la última que sí. */
    fun due(lastCounted: Long?, monday: LocalDate): Boolean = lastCounted == null || lastCounted < monday.toEpochDay()

    /** El lunes de la última semana completa (la anterior a la de [today]). */
    fun lastFullWeek(today: LocalDate): LocalDate = mondayOf(today).minusWeeks(1)

    /**
     * Lo que se cuenta al compañero de una semana, si el usuario lo permite: `total,pasados,cedidos,hora,desencadenante`. La hora y el
     * desencadenante van vacíos si aún no hay un patrón. Nunca la fecha de cada impulso ni nada escrito. `null` si esa semana no hubo impulsos.
     */
    fun weeklyLabel(report: UrgeReport): String? =
        if (report.total == 0) null else "${report.total},${report.passed},${report.gaveIn},${report.peakHour ?: ""},${if (report.total >= MIN_FOR_PATTERNS) report.topTrigger?.name ?: "" else ""}"

    private fun localDay(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
