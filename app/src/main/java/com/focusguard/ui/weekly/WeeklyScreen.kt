package com.focusguard.ui.weekly

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.focusguard.decision.DayFigures
import com.focusguard.ui.Days
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.plural
import com.focusguard.ui.stats.InsightsText

@Composable
fun WeeklyScreen(viewModel: WeeklyViewModel, onBack: () -> Unit, onOpenPermissions: () -> Unit) {
    LaunchedEffect(Unit) { viewModel.load() }
    WeeklyContent(viewModel.ui, onOpenPermissions, onBack = onBack)
}

@Composable
internal fun WeeklyContent(ui: WeeklyUi, onOpenPermissions: () -> Unit, modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    FgScreen("Informe semanal", modifier, onBack = onBack) {
        val report = ui.report
        if (report == null && ui.loaded) {
            item {
                FgGroup(header = "Falta el acceso de uso", footer = "Concédelo para ver cuánto usas el teléfono.") {
                    FgActionRow("Dar acceso", onOpenPermissions)
                }
            }
        }
        if (report != null) {
            item {
                FgHero(
                    title = "de media por día",
                    number = InsightsText.minutes(report.thisWeek.averageUsageMs),
                    body = InsightsText.change(report.usageChange, "la semana anterior"),
                    pill = "Total de la semana: ${InsightsText.minutes(report.thisWeek.usageMs)}",
                )
            }
            item {
                FgGroup(header = "Pantalla y bloqueos") {
                    FgRow("Desbloqueos de pantalla", value = "${report.thisWeek.unlocks} (antes ${report.lastWeek.unlocks})")
                    FgRow("Bloqueos", value = "${report.thisWeek.blocks} (antes ${report.lastWeek.blocks})")
                }
            }
            if (report.bestDay != null && report.worstDay != null && report.bestDay != report.worstDay) {
                item {
                    FgGroup(header = "Tus días") {
                        FgRow("Con menos uso", value = dayText(report.bestDay!!))
                        FgRow("Con más uso", value = dayText(report.worstDay!!))
                    }
                }
            }
        }
        if (ui.loaded) item {
            FgGroup(header = "Tu racha y tus impulsos") {
                FgRow("Racha lograda", value = "${plural(ui.streakDays, "día", "días")} de 7")
                FgRow("Impulsos dejados pasar", value = ui.urgesPassed.toString())
                FgRow("Impulsos en los que cediste", value = ui.urgesGaveIn.toString())
            }
        }
        if (ui.loaded) item { FgNote("Se calcula en el teléfono con tus eventos de uso. Nada de esto sale a ningún servidor.") }
    }
}

private fun dayText(day: DayFigures) = "${Days.word(day.day.dayOfWeek)} ${day.day.dayOfMonth}: ${InsightsText.minutes(day.usageMs)}"
