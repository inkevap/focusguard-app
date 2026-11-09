package com.focusguard.ui.stats

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import com.focusguard.data.UsageOverview
import com.focusguard.ui.Days
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.AppIcon
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.ScreenHeader
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionLabel
import com.focusguard.ui.components.StatusChip
import com.focusguard.ui.components.ValueRow
import com.focusguard.ui.components.StatusCard
import com.focusguard.ui.components.StatusKind
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.plural
import java.time.LocalDate

@Composable
fun StatsScreen(viewModel: StatsViewModel, onOpenPermissions: () -> Unit, onWeekly: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.load() }
    StatsContent(viewModel.state, onOpenPermissions, onDay = viewModel::selectDay, onWeekly = onWeekly)
}

@Composable
internal fun StatsContent(state: StatsState, onOpenPermissions: () -> Unit, onDay: (LocalDate) -> Unit = {}, onWeekly: () -> Unit = {}) {
    val total = state.perDay.sumOf { it.second }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Estadísticas", "Lo que has bloqueado y cuánto usas el teléfono", icon = FgIcons.Chart) }

        state.overview?.let { overview ->
            item { SectionLabel("Cómo vas hoy") }
            item { OverviewCard(overview, onWeekly) }
        }

        item { SectionLabel("Bloqueos de los últimos 7 días") }
        item {
            AppCard {
                Text(if (total == 0) "Todavía no hay bloqueos" else plural(total, "bloqueo", "bloqueos"), style = MaterialTheme.typography.titleMedium)
                val max = (state.perDay.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                state.perDay.forEach { (day, count) -> DayBar(day, count, max) }
            }
        }

        item { SectionLabel("Lo que más se bloqueó") }
        item {
            AppCard {
                if (state.topBlocked.isEmpty()) {
                    Text("Nada en estos días.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.topBlocked.forEach { ValueRow(it.label, plural(it.count, "vez", "veces"), it.packageName) }
            }
        }

        item { SectionLabel("Uso del teléfono") }
        if (state.day != null) {
            item {
                ChipChoices(
                    options = state.days.map { it to dayLabel(it, state.days.last()) },
                    selected = state.day,
                    onSelect = onDay,
                )
            }
        }
        if (!state.usage.usageAvailable) {
            item {
                StatusCard(
                    title = "Falta el acceso de uso",
                    body = "Concédelo para ver cuánto tiempo pasas en cada app.",
                    icon = FgIcons.Info,
                    kind = StatusKind.Neutral,
                    actionLabel = "Dar acceso",
                    onAction = onOpenPermissions,
                )
            }
        } else {
            item {
                AppCard {
                    state.usage.unlocks?.let { ValueRow("Desbloqueos de pantalla", it.toString()) }
                    state.usage.insight?.let { insight ->
                        ValueRow("Rato más largo seguido", InsightsText.minutes(insight.longestUseMs))
                        ValueRow("Descanso más largo", InsightsText.minutes(insight.longestRestMs))
                        ValueRow("Sesiones de bloqueo", insight.blockSessions.toString())
                    }
                    if (state.usage.topApps.isEmpty()) Text("No hay uso registrado ese día.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.usage.topApps.forEach { ValueRow(it.label, "${formatAmountOfTime(it.minutes)} · ${plural(it.opens, "apertura", "aperturas")}", it.packageName) }
                }
            }
        }
        state.usage.webs?.let { webs ->
            item { SectionLabel("Webs de ese día") }
            item {
                AppCard {
                    if (webs.isEmpty()) Text("No hay webs guardadas ese día.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    webs.forEach { ValueRow(it.host, "${formatAmountOfTime(it.minutes)} · ${plural(it.visits, "visita", "visitas")}") }
                }
            }
        }
        state.usage.incognitoMinutes?.let { minutes ->
            item { AppCard { ValueRow("Navegación en incógnito", formatAmountOfTime(minutes)) } }
        }
    }
}

@Composable
private fun OverviewCard(overview: UsageOverview, onWeekly: () -> Unit) {
    AppCard {
        StatusChip(InsightsText.trend(overview.trend))
        // El número del día es lo que se mira primero: va grande y en el color de la marca.
        Column(Modifier.padding(top = 12.dp, bottom = 8.dp).semantics(mergeDescendants = true) {}) {
            Text("Uso de hoy hasta ahora", style = MaterialTheme.typography.bodyLarge)
            Text(InsightsText.minutes(overview.todayMs), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text(InsightsText.change(overview.dayChange, "ayer a esta hora"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val week = overview.report
        ValueRow("Media por día esta semana", InsightsText.minutes(week.thisWeek.averageUsageMs))
        Text(InsightsText.change(week.usageChange, "la semana anterior"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SecondaryButton("Ver el informe semanal", onWeekly, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate) = if (day == today) "Hoy" else Days.short(day.dayOfWeek)

@Composable
private fun DayBar(day: LocalDate, count: Int, max: Int) {
    val name = Days.short(day.dayOfWeek)
    // La barra es visual; el lector de pantalla lee solo el resumen.
    Row(
        modifier = Modifier.padding(top = 10.dp).semantics(mergeDescendants = true) {
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
