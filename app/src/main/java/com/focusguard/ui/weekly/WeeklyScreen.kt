package com.focusguard.ui.weekly

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.decision.DayFigures
import com.focusguard.ui.Days
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.StatusCard
import com.focusguard.ui.components.ValueRow
import com.focusguard.ui.components.StatusKind
import com.focusguard.ui.plural
import com.focusguard.ui.stats.InsightsText

@Composable
fun WeeklyScreen(viewModel: WeeklyViewModel, onBack: () -> Unit, onOpenPermissions: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.load() }
    Scaffold(
        topBar = {
            FgTopBar("Informe semanal", onBack = onBack)
        },
    ) { padding -> WeeklyContent(viewModel.ui, onOpenPermissions, Modifier.padding(padding)) }
}

@Composable
internal fun WeeklyContent(ui: WeeklyUi, onOpenPermissions: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val report = ui.report
        if (report == null && ui.loaded) {
            item {
                StatusCard(
                    title = "Falta el acceso de uso",
                    body = "Concédelo para ver cuánto usas el teléfono.",
                    icon = FgIcons.Info,
                    kind = StatusKind.Neutral,
                    actionLabel = "Dar acceso",
                    onAction = onOpenPermissions,
                )
            }
        }
        if (report != null) {
            item {
                AppCard {
                    SectionTitle("Uso del teléfono", FgIcons.Chart)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(InsightsText.minutes(report.thisWeek.averageUsageMs), style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                        Text("de media por día", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(InsightsText.change(report.usageChange, "la semana anterior"), style = MaterialTheme.typography.bodyMedium)
                    Hint("Total de la semana: ${InsightsText.minutes(report.thisWeek.usageMs)}.", Modifier.padding(top = 4.dp))
                }
            }
            item {
                AppCard {
                    SectionTitle("Pantalla y bloqueos")
                    ValueRow("Desbloqueos de pantalla", "${report.thisWeek.unlocks} (antes ${report.lastWeek.unlocks})")
                    ValueRow("Bloqueos", "${report.thisWeek.blocks} (antes ${report.lastWeek.blocks})")
                }
            }
            if (report.bestDay != null && report.worstDay != null && report.bestDay != report.worstDay) {
                item {
                    AppCard {
                        SectionTitle("Tus días")
                        ValueRow("Con menos uso", dayText(report.bestDay!!))
                        ValueRow("Con más uso", dayText(report.worstDay!!))
                    }
                }
            }
        }
        if (ui.loaded) item {
            AppCard {
                SectionTitle("Tu racha y tus impulsos", FgIcons.Flame)
                Text("Racha lograda ${plural(ui.streakDays, "día", "días")} de 7.", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Impulsos: ${ui.urgesPassed} dejados pasar, ${ui.urgesGaveIn} en los que cediste.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (ui.loaded) item { Hint("Se calcula en el teléfono con tus eventos de uso. Nada de esto sale a ningún servidor.") }
    }
}

private fun dayText(day: DayFigures) = "${Days.word(day.day.dayOfWeek)} ${day.day.dayOfMonth}: ${InsightsText.minutes(day.usageMs)}"
