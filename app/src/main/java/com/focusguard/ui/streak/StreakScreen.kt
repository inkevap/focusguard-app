package com.focusguard.ui.streak

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.DayOutcome
import com.focusguard.decision.DayRecord
import com.focusguard.decision.LostDay
import com.focusguard.decision.Restorable
import com.focusguard.decision.StreakConfig
import com.focusguard.decision.StreakState
import com.focusguard.decision.Streaks
import com.focusguard.ui.Days
import com.focusguard.ui.components.IconBadge
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.rememberTrustedNowMs
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StreakScreen(viewModel: StreakViewModel, onBack: () -> Unit) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    // El día cambia a medianoche (o al inicio del día elegido): se vuelve a mirar con el reloj de confianza.
    val nowMs = rememberTrustedNowMs(30_000L)
    val today = remember(nowMs) { viewModel.today() }
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    Scaffold(
        topBar = {
            FgTopBar("Tu racha", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        StreakContent(
            records = records, config = config, today = today,
            onGoal = viewModel::setGoal, onReset = viewModel::reset, onRestore = viewModel::restore,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun StreakContent(
    records: Map<LocalDate, DayRecord>,
    config: StreakConfig,
    today: LocalDate,
    onGoal: (Int) -> Unit,
    onReset: () -> Unit,
    onRestore: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember(records, config, today) { Streaks.state(records, today, config) }
    val restorable = remember(records, config, today) { Streaks.restorable(records, today, config) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { CurrentCard(state) }
        item { GoalCard(state.current, config.goalDays, onGoal) }
        item { CalendarCard(records, config, today) }
        state.lastLoss?.let { loss -> item { LossCard(loss, restorable, onRestore) } }
        item { ResetCard(onReset) }
    }
}

@Composable
private fun CurrentCard(state: StreakState) {
    val scheme = MaterialTheme.colorScheme
    // La racha va en el color de la brasa, como en Inicio.
    AppCard(containerColor = scheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconBadge(FgIcons.Flame, size = 56.dp, container = scheme.tertiary, content = scheme.onTertiary)
            Column {
                Text("Días seguidos", style = MaterialTheme.typography.titleMedium, color = scheme.onTertiaryContainer)
                Text(
                    state.current.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = scheme.onTertiaryContainer,
                    modifier = Modifier.semantics { contentDescription = "Racha actual: ${StreakText.days(state.current)}" },
                )
            }
        }
        Text("Mejor racha: ${StreakText.days(state.best)}.", style = MaterialTheme.typography.bodyMedium, color = scheme.onTertiaryContainer, modifier = Modifier.padding(top = 12.dp))
        Text(
            StreakText.nextMilestone(state.current),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onTertiaryContainer,
            modifier = Modifier.padding(top = 4.dp),
        )
        when (val today = state.today) {
            DayOutcome.Achieved, DayOutcome.Forgiven -> Text("Hoy ya lo lograste.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            DayOutcome.InProgress -> {
                Text(StreakText.todayProgress(state.todayProtectedMs), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                FgProgress(
                    progress = { (state.todayProtectedMs.toFloat() / Streaks.MIN_PROTECTED_MS).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = scheme.tertiary,
                    trackColor = scheme.onTertiaryContainer.copy(alpha = 0.15f),
                )
            }
            is DayOutcome.Lost -> Text("Hoy se perdió: ${StreakText.lossReason(today.reason)}.", style = MaterialTheme.typography.titleSmall, color = scheme.onTertiaryContainer, modifier = Modifier.padding(top = 8.dp))
            DayOutcome.NotCounted -> Hint("Aún no hay datos. ${StreakText.achieveRule()} Crea un perfil para empezar.", Modifier.padding(top = 8.dp))
        }
        Hint(StreakText.rules(), Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun GoalCard(current: Int, goal: Int, onGoal: (Int) -> Unit) {
    AppCard {
        SectionTitle("Meta")
        val options = listOf(0 to "Sin meta") + Streaks.GOAL_PRESETS.map { it to "$it días" }
        ChipChoices(options = options, selected = goal, onSelect = onGoal, modifier = Modifier.padding(top = 8.dp))
        if (goal > 0) {
            Text(StreakText.goalProgress(current, goal), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            FgProgress(progress = { (current.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun CalendarCard(records: Map<LocalDate, DayRecord>, config: StreakConfig, today: LocalDate) {
    // Meses hacia atrás desde el de hoy: así el mes mostrado sigue a «hoy» sin volver a leer ni guardar fechas.
    var monthsBack by rememberSaveable { mutableIntStateOf(0) }
    val shown = YearMonth.from(today).minusMonths(monthsBack.toLong())
    val resolved = remember(records, config) { Streaks.resolve(records, config) }
    val earliest = resolved.startDay?.let { YearMonth.from(it) } ?: YearMonth.from(today)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthsBack++ }, enabled = shown.isAfter(earliest)) {
                Icon(FgIcons.ChevronLeft, contentDescription = "Mes anterior")
            }
            Text(StreakText.monthTitle(shown), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { monthsBack-- }, enabled = monthsBack > 0) {
                Icon(FgIcons.ChevronRight, contentDescription = "Mes siguiente")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            DayOfWeek.values().forEach { day ->
                Text(Days.letter(day), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
        val offset = shown.atDay(1).dayOfWeek.value - 1
        val cells = List(offset) { null } + (1..shown.lengthOfMonth()).map { shown.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) DayCell(day, Streaks.outcome(day, today, records, resolved), day == today)
                    }
                }
            }
        }
        Hint("Lleno: día logrado. Rojo: día perdido. Con aro: hoy.", Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun DayCell(day: LocalDate, outcome: DayOutcome, isToday: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val (background, content) = when (outcome) {
        DayOutcome.Achieved, DayOutcome.Forgiven -> scheme.primary to scheme.onPrimary
        is DayOutcome.Lost -> scheme.errorContainer to scheme.onErrorContainer
        DayOutcome.InProgress, DayOutcome.NotCounted -> Color.Transparent to scheme.onSurfaceVariant
    }
    val description = "${StreakText.date(day)}: " + when (outcome) {
        DayOutcome.Achieved -> "logrado"
        DayOutcome.Forgiven -> "logrado (restaurado)"
        is DayOutcome.Lost -> "perdido, ${StreakText.lossReason(outcome.reason)}"
        DayOutcome.InProgress -> "hoy, en curso"
        DayOutcome.NotCounted -> "sin contar"
    } + if (isToday && outcome != DayOutcome.InProgress) ", hoy" else ""
    val modifier = Modifier.fillMaxSize().background(background, CircleShape).then(if (isToday) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier)
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun LossCard(loss: LostDay, restorable: Restorable?, onRestore: (LocalDate) -> Unit) {
    AppCard {
        SectionTitle("Último día perdido")
        Text("${StreakText.date(loss.day)}: ${StreakText.lossReason(loss.reason)}.", style = MaterialTheme.typography.bodyMedium)
        if (restorable != null) {
            Hint(
                "Antes llevabas ${StreakText.days(restorable.runBefore)} seguidos. Puedes restaurarlo una vez cada ${Streaks.RESTORE_EVERY_DAYS} días, " +
                    "solo en los ${Streaks.RESTORE_WINDOW_DAYS} días siguientes, y pasa por tus aprobaciones.",
                Modifier.padding(top = 8.dp),
            )
            SecondaryButton("Restaurar ${StreakText.date(restorable.day)}", { onRestore(restorable.day) }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
private fun ResetCard(onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AppCard {
        SectionTitle("Reiniciar")
        Hint("Empieza la cuenta de nuevo desde hoy. Tu mejor racha se conserva.")
        SecondaryButton("Reiniciar racha", { confirm = true }, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Reiniciar la racha?") },
            text = { Text("Los días anteriores dejan de contar y la racha vuelve a cero. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirm = false; onReset() }) { Text("Reiniciar") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
