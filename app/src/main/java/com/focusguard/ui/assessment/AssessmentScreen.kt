package com.focusguard.ui.assessment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.AssessmentResultEntity
import com.focusguard.data.SavedResult
import com.focusguard.decision.Assessment
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.BrandHero
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.StatusChip
import com.focusguard.ui.components.ValueRow
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatLocal
import com.focusguard.ui.icons.FgIcons
import java.time.Instant

class AssessmentActions(
    val answer: (question: Int, value: Int) -> Unit,
    val finish: () -> Unit,
    val upload: () -> Unit,
    val again: () -> Unit,
    val clearHistory: () -> Unit,
    val openProgram: () -> Unit,
    val openUrge: () -> Unit,
)

@Composable
fun AssessmentScreen(viewModel: AssessmentViewModel, onBack: () -> Unit, onProgram: () -> Unit, onUrge: () -> Unit) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    val actions = AssessmentActions(viewModel::answer, viewModel::finish, viewModel::upload, viewModel::again, viewModel::clearHistory, onProgram, onUrge)
    Scaffold(topBar = { FgTopBar("Test de adicción", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val saved = viewModel.result
        if (saved == null) {
            AssessmentQuestions(viewModel.answers, viewModel.busy, actions, Modifier.padding(padding))
        } else {
            AssessmentResult(saved, history, signedIn, viewModel.busy, actions, Modifier.padding(padding))
        }
    }
}

@Composable
internal fun AssessmentQuestions(answers: List<Int?>, busy: Boolean, actions: AssessmentActions, modifier: Modifier = Modifier) {
    val answered = answers.count { it != null }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BrandHero(
                "Diez preguntas sobre el último mes",
                "Contesta con sinceridad: nadie más ve tus respuestas. ${Assessment.DISCLAIMER}",
                icon = FgIcons.Chart,
            )
        }
        item {
            AppCard {
                Text("$answered de ${Assessment.QUESTIONS.size} contestadas", style = MaterialTheme.typography.titleMedium)
                FgProgress({ answered.toFloat() / Assessment.QUESTIONS.size }, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        itemsIndexed(Assessment.QUESTIONS) { index, question ->
            AppCard {
                Text("${index + 1}. $question", style = MaterialTheme.typography.bodyLarge)
                ChipChoices(
                    options = Assessment.OPTIONS.mapIndexed { value, label -> value to label },
                    selected = answers[index] ?: -1,
                    onSelect = { value -> actions.answer(index, value) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { PrimaryButton("Ver mi resultado", actions.finish, Modifier.fillMaxWidth(), enabled = answered == Assessment.QUESTIONS.size && !busy) }
    }
}

@Composable
internal fun AssessmentResult(
    saved: SavedResult,
    history: List<AssessmentResultEntity>,
    signedIn: Boolean,
    busy: Boolean,
    actions: AssessmentActions,
    modifier: Modifier = Modifier,
) {
    val band = Assessment.band(saved.score)
    val uploaded = history.firstOrNull { it.id == saved.id }?.uploaded == true
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar los resultados?") },
            text = { Text("Se borran los de este teléfono y, si tienes sesión, también los de tu cuenta. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; actions.clearHistory() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AppCard {
                SectionTitle("Tu resultado", FgIcons.Chart)
                Text("${saved.score} de ${Assessment.MAX_SCORE}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                StatusChip("Nivel ${band.label.lowercase()}")
                Text(band.advice, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
                Assessment.comparison(saved.previous, saved.score)?.let { Hint(it) }
                Hint(Assessment.DISCLAIMER)
            }
        }
        item {
            AppCard {
                SectionTitle("¿Y ahora?", FgIcons.Leaf)
                PrimaryButton("Programa de recuperación", actions.openProgram, Modifier.fillMaxWidth().padding(top = 8.dp))
                SecondaryButton("Tengo un impulso ahora", actions.openUrge, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        item {
            AppCard {
                SectionTitle("En tu cuenta", FgIcons.Shield)
                when {
                    uploaded -> StatusChip("Guardado en tu cuenta")
                    signedIn -> {
                        Hint("Si quieres, guarda la puntuación en tu cuenta. Solo la puntuación y la fecha: tus respuestas no salen de este teléfono y tu compañero no la ve.")
                        SecondaryButton("Guardar en mi cuenta", actions.upload, Modifier.fillMaxWidth(), enabled = !busy)
                    }
                    else -> Hint("Con una cuenta podrías guardar la puntuación en tu servidor. Es opcional: aquí ya queda en este teléfono.")
                }
            }
        }
        if (history.size > 1) {
            item {
                AppCard {
                    SectionTitle("Tus resultados")
                    history.forEach { ValueRow(formatLocal(Instant.ofEpochMilli(it.takenMs), withDay = true), "${it.score} de ${Assessment.MAX_SCORE}") }
                }
            }
        }
        item { SecondaryButton("Hacerlo de nuevo", actions.again, Modifier.fillMaxWidth()) }
        item { SecondaryButton("Borrar mis resultados", { confirmClear = true }, Modifier.fillMaxWidth()) }
    }
}
