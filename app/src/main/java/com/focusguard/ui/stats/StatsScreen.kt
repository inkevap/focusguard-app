package com.focusguard.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.focusguard.data.UsageOverview
import com.focusguard.ui.Days
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.plural
import java.time.LocalDate

/** Las subpantallas de las estadísticas. */
private enum class StatsPage(val title: String) {
    BLOCKED("Lo que más se bloqueó"),
    USAGE("Uso del teléfono"),
    WEBS("Webs de ese día"),
}

@Composable
fun StatsScreen(viewModel: StatsViewModel, onOpenPermissions: () -> Unit, onWeekly: () -> Unit, onBack: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.load() }
    StatsContent(viewModel.state, onOpenPermissions, onDay = viewModel::selectDay, onWeekly = onWeekly, onBack = onBack)
}

@Composable
internal fun StatsContent(
    state: StatsState,
    onOpenPermissions: () -> Unit,
    onDay: (LocalDate) -> Unit = {},
    onWeekly: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf<StatsPage?>(null) }
    SubPageHost(
        page = page,
        onBack = { page = null },
        root = { StatsRoot(state, onOpenPermissions, onDay, onWeekly, onBack, onOpen = { page = it }) },
        sub = { p ->
            FgScreen(p.title, onBack = { page = null }) {
                when (p) {
                    StatsPage.BLOCKED -> blockedItems(state)
                    StatsPage.USAGE -> usageItems(state, onDay)
                    StatsPage.WEBS -> websItems(state)
                }
            }
        },
    )
}

@Composable
private fun StatsRoot(
    state: StatsState,
    onOpenPermissions: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onWeekly: () -> Unit,
    onBack: () -> Unit,
    onOpen: (StatsPage) -> Unit,
) {
    val total = state.perDay.sumOf { it.second }
    FgScreen("Estadísticas", onBack = onBack) {
        state.overview?.let { overview ->
            item { OverviewHero(overview) }
            item {
                FgGroup {
                    FgRow("Media por día esta semana", value = InsightsText.minutes(overview.report.thisWeek.averageUsageMs), subtitle = InsightsText.change(overview.report.usageChange, "la semana anterior"))
                    FgRow("Ver el informe semanal", onClick = onWeekly)
                }
            }
        }

        item {
            FgGroup(header = "Bloqueos de los últimos 7 días") {
                FgCell {
                    Text(if (total == 0) "Todavía no hay bloqueos" else plural(total, "bloqueo", "bloqueos"), style = MaterialTheme.typography.titleMedium)
                    val max = (state.perDay.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                    state.perDay.forEach { (day, count) -> DayBar(day, count, max) }
                }
            }
        }

        if (state.day != null) {
            item {
                FgGroup(header = "Día") {
                    FgCell { ChipChoices(options = state.days.map { it to dayLabel(it, state.days.last()) }, selected = state.day, onSelect = onDay) }
                }
            }
        }

        item {
            FgGroup {
                FgRow("Lo que más se bloqueó", value = state.topBlocked.firstOrNull()?.label ?: "Nada", onClick = { onOpen(StatsPage.BLOCKED) })
                if (state.usage.usageAvailable) {
                    FgRow("Uso del teléfono", value = state.usage.unlocks?.let { plural(it, "desbloqueo", "desbloqueos") }, onClick = { onOpen(StatsPage.USAGE) })
                } else {
                    FgRow("Falta el acceso de uso", subtitle = "Concédelo para ver cuánto tiempo pasas en cada app.")
                    FgActionRow("Dar acceso", onOpenPermissions)
                }
                state.usage.webs?.let { webs -> FgRow("Webs", value = webs.size.toString(), onClick = { onOpen(StatsPage.WEBS) }) }
                state.usage.incognitoMinutes?.let { FgRow("Navegación en incógnito", value = formatAmountOfTime(it)) }
            }
        }
    }
}

@Composable
private fun OverviewHero(overview: UsageOverview) {
    FgHero(
        title = "Uso de hoy hasta ahora",
        number = InsightsText.minutes(overview.todayMs),
        body = InsightsText.change(overview.dayChange, "ayer a esta hora"),
        pill = InsightsText.trend(overview.trend),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.blockedItems(state: StatsState) {
    item {
        FgGroup {
            if (state.topBlocked.isEmpty()) FgRow("Nada en estos días.")
            state.topBlocked.forEach { FgRow(it.label, value = plural(it.count, "vez", "veces")) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.usageItems(state: StatsState, onDay: (LocalDate) -> Unit) {
    if (state.day != null) {
        item { FgGroup { FgCell { ChipChoices(options = state.days.map { it to dayLabel(it, state.days.last()) }, selected = state.day, onSelect = onDay) } } }
    }
    item {
        FgGroup {
            state.usage.unlocks?.let { FgRow("Desbloqueos de pantalla", value = it.toString()) }
            state.usage.insight?.let { insight ->
                FgRow("Rato más largo seguido", value = InsightsText.minutes(insight.longestUseMs))
                FgRow("Descanso más largo", value = InsightsText.minutes(insight.longestRestMs))
                FgRow("Sesiones de bloqueo", value = insight.blockSessions.toString())
            }
        }
    }
    item {
        FgGroup(header = "Apps") {
            if (state.usage.topApps.isEmpty()) FgRow("No hay uso registrado ese día.")
            state.usage.topApps.forEach { FgRow(it.label, value = "${formatAmountOfTime(it.minutes)} · ${plural(it.opens, "apertura", "aperturas")}") }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.websItems(state: StatsState) {
    val webs = state.usage.webs.orEmpty()
    item {
        FgGroup {
            if (webs.isEmpty()) FgRow("No hay webs guardadas ese día.")
            webs.forEach { FgRow(it.host, value = "${formatAmountOfTime(it.minutes)} · ${plural(it.visits, "visita", "visitas")}") }
        }
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate) = if (day == today) "Hoy" else Days.short(day.dayOfWeek)

@Composable
private fun DayBar(day: LocalDate, count: Int, max: Int) {
    val name = Days.short(day.dayOfWeek)
    // La barra es visual; el lector de pantalla lee solo el resumen.
    Row(
        modifier = Modifier.padding(top = 4.dp).semantics(mergeDescendants = true) {
            contentDescription = "$name: ${plural(count, "bloqueo", "bloqueos")}"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(name, modifier = Modifier.widthIn(min = 40.dp).clearAndSetSemantics { }, style = MaterialTheme.typography.bodyMedium)
        FgProgress(progress = { count.toFloat() / max }, modifier = Modifier.weight(1f).clearAndSetSemantics { })
        Text("$count", modifier = Modifier.widthIn(min = 32.dp).clearAndSetSemantics { }, style = MaterialTheme.typography.bodyMedium)
    }
}
