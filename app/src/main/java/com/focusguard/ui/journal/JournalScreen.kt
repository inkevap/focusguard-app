package com.focusguard.ui.journal

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.JournalEntryEntity
import com.focusguard.data.MoodEntity
import com.focusguard.data.PlannedActivityEntity
import com.focusguard.decision.Journal
import com.focusguard.decision.Mood
import com.focusguard.ui.Days
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.rememberTrustedNowMs
import com.focusguard.ui.streak.StreakText
import java.time.LocalDate

@Composable
fun JournalScreen(viewModel: JournalViewModel, onBack: () -> Unit) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val moods by viewModel.moods.collectAsStateWithLifecycle()
    val nowMs = rememberTrustedNowMs(30_000L)
    val today = remember(nowMs) { viewModel.today() }
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    Scaffold(
        topBar = {
            FgTopBar("Diario", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        JournalContent(
            entries = entries, activities = activities, moods = moods, today = today,
            onSave = viewModel::saveToday, onMood = viewModel::logMood, onPlan = viewModel::plan, onComplete = viewModel::complete,
            modifier = Modifier.padding(padding),
        )
    }
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
) {
    val todayEntry = entries.firstOrNull { it.epochDay == today.toEpochDay() }
    val past = entries.filter { it.epochDay != today.toEpochDay() }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MoodCard(moods, today, onMood) }
        item { PlannerCard("Hoy", today, today, activities, onPlan, onComplete) }
        item { PlannerCard("Mañana", today.plusDays(1), today, activities, onPlan, onComplete) }
        item { TodayEntryCard(todayEntry?.text.orEmpty(), onSave) }
        if (past.isNotEmpty()) item { SectionTitle("Días anteriores") }
        items(past, key = { it.epochDay }) { entry ->
            AppCard {
                Text(StreakText.date(LocalDate.ofEpochDay(entry.epochDay)), style = MaterialTheme.typography.titleSmall)
                Text(entry.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item { Hint("Lo que escribes aquí no sale del teléfono: ni a la copia de seguridad ni al servidor ni a tu compañero.") }
    }
}

@Composable
private fun PlannerCard(
    title: String,
    day: LocalDate,
    today: LocalDate,
    all: List<PlannedActivityEntity>,
    onPlan: (LocalDate, String) -> Unit,
    onComplete: (PlannedActivityEntity) -> Unit,
) {
    val planned = all.filter { it.epochDay == day.toEpochDay() }
    var text by rememberSaveable(day) { mutableStateOf("") }
    AppCard {
        SectionTitle("$title: qué vas a hacer en vez de usar el teléfono")
        planned.forEach { activity ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = activity.doneMs != null,
                    onCheckedChange = { if (activity.doneMs == null) onComplete(activity) },
                    enabled = activity.doneMs == null && Journal.canComplete(day, today),
                )
                Text(activity.text, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (planned.size < Journal.MAX_ACTIVITIES_PER_DAY) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(Journal.MAX_ACTIVITY_CHARS) },
                label = { Text("Una actividad") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            SecondaryButton(
                "Planificar",
                { onPlan(day, text); text = "" },
                Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = Journal.cleanActivity(text).isNotEmpty(),
            )
        }
        Hint("Hasta ${Journal.MAX_ACTIVITIES_PER_DAY} por día. Una vez planificada no se puede cambiar ni quitar: solo marcarla como hecha.", Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TodayEntryCard(saved: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(saved) { mutableStateOf(saved) }
    AppCard {
        SectionTitle("¿Qué podría haber hecho mejor hoy?")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(Journal.MAX_ENTRY_CHARS) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        SecondaryButton("Guardar", { onSave(text) }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = text.isNotBlank() && text.trim() != saved)
        Hint("Se puede cambiar hoy; mañana quedará como está.", Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MoodCard(moods: List<MoodEntity>, today: LocalDate, onMood: (Mood) -> Unit) {
    // Un ánimo por día, de la última semana (la lista viene del más reciente al más antiguo).
    val perDay = remember(moods, today) { moods.filter { it.epochDay > today.toEpochDay() - 7 } }
    val todayMood = perDay.firstOrNull { it.epochDay == today.toEpochDay() }?.let { Mood.of(it.level) }
    AppCard {
        SectionTitle("¿Cómo te sientes?", FgIcons.Leaf)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Mood.entries.forEach { mood ->
                SecondaryButton(mood.emoji, { onMood(mood) }, Modifier.weight(1f).semantics { contentDescription = mood.label })
            }
        }
        Hint(todayMood?.let { "Hoy: ${it.emoji} ${it.label}. Puedes cambiarlo cuando quieras." } ?: "Apúntalo cuando quieras: se ve cómo cambia con los días.", Modifier.padding(top = 4.dp))
        val before = perDay.filter { it.epochDay != today.toEpochDay() }
        if (before.isNotEmpty()) {
            Text(
                before.joinToString("   ") { row -> "${Days.letter(LocalDate.ofEpochDay(row.epochDay).dayOfWeek)} ${Mood.of(row.level)?.emoji.orEmpty()}" },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
