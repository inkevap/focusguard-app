package com.focusguard.ui.streak

import com.focusguard.decision.LossReason
import com.focusguard.decision.Streaks
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.plural
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Los textos de la pantalla de racha, aparte para probarlos sin Android. */
internal object StreakText {
    private val spanish = Locale("es")

    fun days(count: Int) = plural(count, "día", "días")

    fun lossReason(reason: LossReason) = when (reason) {
        LossReason.TOO_LITTLE -> "no llegó a ${hour()} de bloqueo activo"
        LossReason.LONG_PAUSE -> "hubo una pausa de más de ${Streaks.MAX_PAUSE_MINUTES} minutos"
        LossReason.MANY_PAUSES -> "hubo más de ${Streaks.MAX_PAUSES} pausas"
    }

    private fun hour() = formatAmountOfTime(Streaks.MIN_PROTECTED_MS / 60_000)

    /** Cuándo se logra un día. */
    fun achieveRule() = "Un día se logra con al menos ${hour()} de bloqueo activo."

    /** Cuándo se logra un día y cuándo se pierde. */
    fun rules() = "${achieveRule()} Se pierde con una pausa de más de ${Streaks.MAX_PAUSE_MINUTES} minutos o con más de ${Streaks.MAX_PAUSES} pausas."

    /** «27 de octubre». */
    fun date(day: LocalDate) = "${day.dayOfMonth} de ${monthName(day.month)}"

    private fun monthName(month: Month) = month.getDisplayName(TextStyle.FULL, spanish)

    /** «octubre 2026». */
    fun monthTitle(month: YearMonth) = "${monthName(month.month)} ${month.year}"

    fun todayProgress(protectedMs: Long): String {
        val done = minOf(protectedMs, Streaks.MIN_PROTECTED_MS) / 60_000
        return "Hoy llevas ${formatAmountOfTime(done)} de ${hour()} de bloqueo activo."
    }

    fun nextMilestone(current: Int): String {
        val next = Streaks.nextMilestone(current) ?: return "Has superado todos los hitos. ¡Enhorabuena!"
        val missing = next - current
        return "Próximo hito: ${days(next)} (te ${if (missing == 1) "falta 1" else "faltan $missing"})."
    }

    fun goalProgress(current: Int, goal: Int) =
        if (current >= goal) "Meta cumplida: $current de ${days(goal)}." else "$current de ${days(goal)}."
}
