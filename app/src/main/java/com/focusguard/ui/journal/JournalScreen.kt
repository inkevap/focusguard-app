package com.focusguard.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.JournalEntryEntity
import com.focusguard.data.MoodEntity
import com.focusguard.data.PlannedActivityEntity
import com.focusguard.decision.Journal
import com.focusguard.decision.Mood
import com.focusguard.ui.Days
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgCheckRow
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgTextAction
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.rememberTrustedNowMs
import com.focusguard.ui.streak.StreakText
import java.time.LocalDate

/** Las subpantallas del diario. */
private enum class JournalPage(val title: String) {
    PLAN_TODAY("Plan de hoy"),
    PLAN_TOMORROW("Plan de mañana"),
    REFLECTION("¿Qué podría haber hecho mejor?"),
    HISTORY("Días anteriores"),
}

@Composable
fun JournalScreen(viewModel: JournalViewModel, onBack: () -> Unit) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val moods by viewModel.moods.collectAsStateWithLifecycle()
    val nowMs = rememberTrustedNowMs(30_000L)
    val today = remember(nowMs) { viewModel.today() }
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    JournalContent(
        entries = entries, activities = activities, moods = moods, today = today,
        onSave = viewModel::saveToday, onMood = viewModel::logMood, onPlan = viewModel::plan, onComplete = viewModel::complete,
        onBack = onBack, snackbar = snackbar,
    )
}

@Composable
internal fun JournalContent(
    entries: List<JournalEntryEntity>,
    activities: List<PlannedActivityEntity>,
    moods: List<MoodEntity>,
    today: LocalDate,
    onSave: (String) -> Unit,
    onMood: (Mood) -> Unit,
    onPlan: (LocalDate, String) -> Unit,
    onComplete: (PlannedActivityEntity) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    snackbar: SnackbarHostState? = null,
) {
    val todayEntry = entries.firstOrNull { it.epochDay == today.toEpochDay() }
    val past = entries.filter { it.epochDay != today.toEpochDay() }
    val tomorrow = today.plusDays(1)
    var page by rememberSaveable { mutableStateOf<JournalPage?>(null) }

    fun planSummary(day: LocalDate): String {
        val planned = activities.filter { it.epochDay == day.toEpochDay() }
        return if (planned.isEmpty()) "Vacío" else "${planned.count { it.doneMs != null }} de ${planned.size}"
    }

    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen("Diario", modifier, onBack = onBack, snackbar = snackbar) {
                item { MoodGroup(moods, today, onMood) }
                item {
                    FgGroup(footer = "Lo que escribes aquí no sale del teléfono: ni a la copia de seguridad ni al servidor ni a tu compañero.") {
                        FgRow(JournalPage.PLAN_TODAY.title, value = planSummary(today), onClick = { page = JournalPage.PLAN_TODAY })
                        FgRow(JournalPage.PLAN_TOMORROW.title, value = planSummary(tomorrow), onClick = { page = JournalPage.PLAN_TOMORROW })
                        FgRow(JournalPage.REFLECTION.title, value = if (todayEntry != null) "Escrito" else null, onClick = { page = JournalPage.REFLECTION })
                        FgRow(JournalPage.HISTORY.title, value = past.size.toString(), onClick = { page = JournalPage.HISTORY })
                    }
                }
            }
        },
        sub = { p ->
            when (p) {
                JournalPage.PLAN_TODAY, JournalPage.PLAN_TOMORROW -> {
                    val day = if (p == JournalPage.PLAN_TODAY) today else tomorrow
                    FgScreen(p.title, modifier, onBack = { page = null }, snackbar = snackbar) {
                        item { PlannerGroup(day, today, activities, onPlan, onComplete) }
                    }
                }
                JournalPage.REFLECTION -> ReflectionPage(todayEntry?.text.orEmpty(), onSave, modifier, onBack = { page = null }, snackbar = snackbar)
                JournalPage.HISTORY -> FgScreen(p.title, modifier, onBack = { page = null }, snackbar = snackbar) {
                    item {
                        FgGroup {
                            if (past.isEmpty()) FgRow("Todavía no hay días anteriores.")
                            past.forEach { entry -> FgRow(StreakText.date(LocalDate.ofEpochDay(entry.epochDay)), subtitle = entry.text) }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PlannerGroup(
    day: LocalDate,
    today: LocalDate,
    all: List<PlannedActivityEntity>,
    onPlan: (LocalDate, String) -> Unit,
    onComplete: (PlannedActivityEntity) -> Unit,
) {
    val planned = all.filter { it.epochDay == day.toEpochDay() }
    var text by rememberSaveable(day) { mutableStateOf("") }
    FgGroup(
        header = "Qué vas a hacer en vez de usar el teléfono",
        footer = "Hasta ${Journal.MAX_ACTIVITIES_PER_DAY} por día. Una vez planificada no se puede cambiar ni quitar: solo marcarla como hecha.",
    ) {
        planned.forEach { activity ->
            FgCheckRow(
                activity.text,
                checked = activity.doneMs != null,
                onCheckedChange = { if (activity.doneMs == null) onComplete(activity) },
                enabled = activity.doneMs == null && Journal.canComplete(day, today),
            )
        }
        if (planned.size < Journal.MAX_ACTIVITIES_PER_DAY) {
            FgCell {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(Journal.MAX_ACTIVITY_CHARS) },
                    label = { Text("Una actividad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    "Planificar",
                    { onPlan(day, text); text = "" },
                    Modifier.fillMaxWidth(),
                    enabled = Journal.cleanActivity(text).isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun ReflectionPage(saved: String, onSave: (String) -> Unit, modifier: Modifier, onBack: () -> Unit, snackbar: SnackbarHostState?) {
    var text by rememberSaveable(saved) { mutableStateOf(saved) }
    FgScreen(
        "Reflexión", modifier, onBack = onBack, snackbar = snackbar,
        actions = { FgTextAction("Guardar", { onSave(text) }, enabled = text.isNotBlank() && text.trim() != saved) },
    ) {
        item {
            FgGroup(header = "¿Qué podría haber hecho mejor hoy?", footer = "Se puede cambiar hoy; mañana quedará como está.") {
                FgCell {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(Journal.MAX_ENTRY_CHARS) },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodGroup(moods: List<MoodEntity>, today: LocalDate, onMood: (Mood) -> Unit) {
    // Un ánimo por día, de la última semana (la lista viene del más reciente al más antiguo).
    val perDay = remember(moods, today) { moods.filter { it.epochDay > today.toEpochDay() - 7 } }
    val todayMood = perDay.firstOrNull { it.epochDay == today.toEpochDay() }?.let { Mood.of(it.level) }
    FgGroup(
        header = "¿Cómo te sientes?",
        footer = todayMood?.let { "Hoy: ${it.emoji} ${it.label}. Puedes cambiarlo cuando quieras." } ?: "Apúntalo cuando quieras: se ve cómo cambia con los días.",
    ) {
        FgCell {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Mood.entries.forEach { mood ->
                    SecondaryButton(mood.emoji, { onMood(mood) }, Modifier.weight(1f).semantics { contentDescription = mood.label })
                }
            }
            val before = perDay.filter { it.epochDay != today.toEpochDay() }
            if (before.isNotEmpty()) {
                Text(
                    before.joinToString("   ") { row -> "${Days.letter(LocalDate.ofEpochDay(row.epochDay).dayOfWeek)} ${Mood.of(row.level)?.emoji.orEmpty()}" },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
