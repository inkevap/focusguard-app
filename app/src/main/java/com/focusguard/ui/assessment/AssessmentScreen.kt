package com.focusguard.ui.assessment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.AssessmentResultEntity
import com.focusguard.data.SavedResult
import com.focusguard.decision.Assessment
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgRadioGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatLocal
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.profiles.BottomAction
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

private const val TITLE = "Test de adicción"

@Composable
fun AssessmentScreen(viewModel: AssessmentViewModel, onBack: () -> Unit, onProgram: () -> Unit, onUrge: () -> Unit) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    val actions = AssessmentActions(viewModel::answer, viewModel::finish, viewModel::upload, viewModel::again, viewModel::clearHistory, onProgram, onUrge)
    val saved = viewModel.result
    if (saved == null) {
        AssessmentQuestions(viewModel.answers, viewModel.busy, actions, onBack = onBack, snackbar = snackbar)
    } else {
        AssessmentResult(saved, history, signedIn, viewModel.busy, actions, onBack = onBack, snackbar = snackbar)
    }
}

@Composable
internal fun AssessmentQuestions(
    answers: List<Int?>,
    busy: Boolean,
    actions: AssessmentActions,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    snackbar: SnackbarHostState? = null,
) {
    val answered = answers.count { it != null }
    FgScreen(
        TITLE, modifier, onBack = onBack, snackbar = snackbar,
        bottomBar = {
            BottomAction { PrimaryButton("Ver mi resultado", actions.finish, Modifier.fillMaxWidth(), enabled = answered == Assessment.QUESTIONS.size && !busy) }
        },
    ) {
        item {
            FgHero(
                "Diez preguntas sobre el último mes",
                body = "Contesta con sinceridad: nadie más ve tus respuestas. ${Assessment.DISCLAIMER}",
                pill = "$answered de ${Assessment.QUESTIONS.size} contestadas",
                extra = { FgProgress({ answered.toFloat() / Assessment.QUESTIONS.size }, Modifier.fillMaxWidth().padding(top = 8.dp)) },
            )
        }
        Assessment.QUESTIONS.forEachIndexed { index, question ->
            item(key = "q$index") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${index + 1}. $question", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
                    FgRadioGroup(
                        options = Assessment.OPTIONS.mapIndexed { value, label -> Triple<Int?, String, String?>(value, label, null) },
                        selected = answers[index],
                        onSelect = { value -> value?.let { actions.answer(index, it) } },
                    )
                }
            }
        }
    }
}

private enum class ResultPage(val title: String) { HISTORY("Resultados anteriores") }

@Composable
internal fun AssessmentResult(
    saved: SavedResult,
    history: List<AssessmentResultEntity>,
    signedIn: Boolean,
    busy: Boolean,
    actions: AssessmentActions,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    snackbar: SnackbarHostState? = null,
) {
    val band = Assessment.band(saved.score)
    val uploaded = history.firstOrNull { it.id == saved.id }?.uploaded == true
    var confirmClear by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf<ResultPage?>(null) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar los resultados?") },
            text = { Text("Se borran los de este teléfono y, si tienes sesión, también los de tu cuenta. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; actions.clearHistory() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen("Tu resultado", modifier, onBack = onBack, snackbar = snackbar) {
                item {
                    FgHero(
                        title = "Nivel ${band.label.lowercase()}",
                        number = "${saved.score} de ${Assessment.MAX_SCORE}",
                        body = listOfNotNull(band.advice, Assessment.comparison(saved.previous, saved.score)).joinToString(" "),
                    )
                }
                item { FgNote(Assessment.DISCLAIMER, Modifier.padding(horizontal = 16.dp)) }
                item {
                    FgGroup(header = "¿Y ahora?") {
                        FgRow("Programa de recuperación", icon = FgIcons.Leaf, onClick = actions.openProgram)
                        FgRow("Tengo un impulso ahora", icon = FgIcons.Wind, onClick = actions.openUrge)
                    }
                }
                item {
                    when {
                        uploaded -> FgGroup(header = "En tu cuenta") { FgRow("Guardado en tu cuenta") }
                        signedIn -> FgGroup(
                            header = "En tu cuenta",
                            footer = "Si quieres, guarda la puntuación en tu cuenta. Solo la puntuación y la fecha: tus respuestas no salen de este teléfono y tu compañero no la ve.",
                        ) { FgActionRow("Guardar en mi cuenta", actions.upload, enabled = !busy) }
                        else -> FgGroup(
                            header = "En tu cuenta",
                            footer = "Con una cuenta podrías guardar la puntuación en tu servidor. Es opcional: aquí ya queda en este teléfono.",
                        ) {}
                    }
                }
                item {
                    FgGroup {
                        if (history.size > 1) FgRow(ResultPage.HISTORY.title, value = history.size.toString(), onClick = { page = ResultPage.HISTORY })
                        FgActionRow("Hacerlo de nuevo", actions.again)
                        FgActionRow("Borrar mis resultados", { confirmClear = true }, danger = true)
                    }
                }
            }
        },
        sub = { p ->
            FgScreen(p.title, modifier, onBack = { page = null }, snackbar = snackbar) {
                item {
                    FgGroup {
                        history.forEach { FgRow(formatLocal(Instant.ofEpochMilli(it.takenMs), withDay = true), value = "${it.score} de ${Assessment.MAX_SCORE}") }
                    }
                }
            }
        },
    )
}
