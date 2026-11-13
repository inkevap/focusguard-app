package com.focusguard.ui.progress

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.Streaks
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.streak.StreakText
import com.focusguard.ui.streak.StreakViewModel

/** La pestaña Progreso: la racha arriba y, debajo, cómo vas y las herramientas para aguantar. */
@Composable
fun ProgressScreen(
    streakViewModel: StreakViewModel,
    onStreak: () -> Unit,
    onStats: () -> Unit,
    onWeekly: () -> Unit,
    onUrgeReport: () -> Unit,
    onJournal: () -> Unit,
    onRecovery: () -> Unit,
    onAssessment: () -> Unit,
    onHabits: () -> Unit,
) {
    val records by streakViewModel.records.collectAsStateWithLifecycle()
    val config by streakViewModel.config.collectAsStateWithLifecycle()
    val today = remember(records) { streakViewModel.today() }
    val streak = remember(records, config, today) { Streaks.current(records, today, config) }
    ProgressContent(streak, onStreak, onStats, onWeekly, onUrgeReport, onJournal, onRecovery, onAssessment, onHabits)
}

@Composable
internal fun ProgressContent(
    streak: Int,
    onStreak: () -> Unit,
    onStats: () -> Unit,
    onWeekly: () -> Unit,
    onUrgeReport: () -> Unit,
    onJournal: () -> Unit,
    onRecovery: () -> Unit,
    onAssessment: () -> Unit,
    onHabits: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    FgScreen("Progreso", large = true) {
        item {
            FgHero(
                if (streak == 1) "día seguido" else "días seguidos",
                number = streak.toString(),
                body = if (streak == 0) StreakText.achieveRule() else StreakText.nextMilestone(streak),
                onClick = onStreak,
            )
        }
        item {
            FgGroup(header = "Cómo vas") {
                FgRow("Estadísticas", icon = FgIcons.Chart, onClick = onStats)
                FgRow("Informe semanal", icon = FgIcons.Timer, iconColor = scheme.secondary, onClick = onWeekly)
                FgRow("Informe de impulsos", icon = FgIcons.Wind, iconColor = scheme.tertiary, onClick = onUrgeReport)
            }
        }
        item {
            FgGroup(header = "Herramientas") {
                FgRow("Diario", icon = FgIcons.Book, onClick = onJournal)
                FgRow("Programa de recuperación", icon = FgIcons.Leaf, onClick = onRecovery)
                FgRow("Test de adicción", icon = FgIcons.Check, iconColor = scheme.secondary, onClick = onAssessment)
                FgRow("Hábitos y recordatorios", icon = FgIcons.Bell, iconColor = scheme.tertiary, onClick = onHabits)
            }
        }
    }
}
