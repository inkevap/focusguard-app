package com.focusguard.ui.stats

import com.focusguard.decision.Change
import com.focusguard.decision.Trend
import com.focusguard.ui.formatAmountOfTime

/** Los textos de los insights, aparte para probarlos sin Android. */
internal object InsightsText {

    fun trend(trend: Trend) = when (trend) {
        Trend.BETTER -> "Menos que tu media a esta hora"
        Trend.SAME -> "Como siempre a esta hora"
        Trend.WORSE -> "Más que tu media a esta hora"
        Trend.NO_DATA -> "Aún sin datos para comparar"
    }

    /** «12 % menos que ayer», «5 % más que…», «Igual que…»; «Sin datos de…» sin con qué comparar. */
    fun change(change: Change, than: String): String {
        val percent = change.percent ?: return "Sin datos de $than"
        return when {
            percent == 0 -> "Igual que $than"
            percent < 0 -> "${-percent} % menos que $than"
            else -> "$percent % más que $than"
        }
    }

    fun minutes(ms: Long) = formatAmountOfTime(ms / 60_000)
}
