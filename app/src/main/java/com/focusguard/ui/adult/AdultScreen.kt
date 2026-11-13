package com.focusguard.ui.adult

import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.AllowPhrase
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.HandTypedField
import com.focusguard.ui.components.RemovableAppRow
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberAppLabels
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatLocal
import com.focusguard.ui.plural
import com.focusguard.ui.profiles.AppPickerDialog
import com.focusguard.ui.profiles.GroupStack
import com.focusguard.ui.profiles.RemovableFgRow
import java.time.Instant

class AdultActions(
    val refreshLists: () -> Unit,
    val addWord: (String) -> Boolean,
    val removeWord: (String) -> Unit,
    val allow: (String, String) -> Boolean,
    val disallow: (String) -> Unit,
    val chooseExemptApps: () -> Unit = {},
    val readAgain: (String) -> Unit = {},
)

@Composable
fun AdultScreen(viewModel: AdultViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    var picking by rememberSaveable { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val browsers = remember { com.focusguard.service.BrowserTable.fromAssets(ctx) }
    AdultContentBody(
        state = state,
        syncing = viewModel.syncing,
        actions = AdultActions(viewModel::refreshLists, viewModel::addWord, viewModel::removeWord, viewModel::allow, viewModel::disallow, { picking = true }, viewModel::readAgain),
        onBack = onBack,
        snackbar = snackbar,
    )
    if (picking) {
        AppPickerDialog(
            selected = state.exemptApps.toSet(),
            onConfirm = { chosen ->
                viewModel.exemptApps(chosen, browsers::isBrowser)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** Las subpantallas de Contenido adulto. */
private enum class AdultPage(val title: String) {
    WORDS("Palabras y webs"),
    ALLOWED("Dejar pasar"),
    EXEMPT("Apps cuyo texto no se lee"),
}

@Composable
internal fun AdultContentBody(
    state: AdultUiState,
    syncing: Boolean,
    actions: AdultActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
) {
    var page by rememberSaveable { mutableStateOf<AdultPage?>(null) }
    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen("Contenido adulto", modifier, onBack = onBack, snackbar = snackbar) {
                state.offUntilMs?.let { until ->
                    item {
                        FgGroup(header = "Apagado por un rato") {
                            FgRow("Se enciende solo", value = formatLocal(Instant.ofEpochMilli(until), withDay = true))
                        }
                    }
                }
                item {
                    FgGroup(header = "Lo tuyo") {
                        FgRow("Tus palabras y webs", value = state.userWords.size.toString(), onClick = { page = AdultPage.WORDS })
                        FgRow("Dejar pasar", value = state.allowed.size.toString(), onClick = { page = AdultPage.ALLOWED })
                        FgRow("Apps cuyo texto no se lee", value = state.exemptApps.size.toString(), onClick = { page = AdultPage.EXEMPT })
                    }
                }
                item {
                    FgGroup(
                        header = "Listas del servidor",
                        footer = if (state.domains == 0 && state.words == 0) {
                            "Aún no se han descargado. Se bajan solas cuando hay internet; el bloqueo por palabras y por dominios funciona con lo que haya en el teléfono."
                        } else {
                            "${plural(state.domains, "dominio", "dominios")} y ${plural(state.words, "palabra", "palabras")} (versión ${state.version}). Funcionan sin conexión."
                        },
                    ) {
                        FgActionRow(if (syncing) "Actualizando…" else "Actualizar ahora", actions.refreshLists, enabled = !syncing)
                    }
                }
            }
        },
        sub = { current ->
            FgScreen(current.title, modifier, onBack = { page = null }, snackbar = snackbar) {
                item {
                    when (current) {
                        AdultPage.WORDS -> WordsGroups(state.userWords, actions)
                        AdultPage.ALLOWED -> AllowedGroups(state.allowed, actions)
                        AdultPage.EXEMPT -> ExemptAppsGroups(state.exemptApps, actions)
                    }
                }
            }
        },
    )
}

@Composable
private fun WordsGroups(words: List<String>, actions: AdultActions) {
    var entry by rememberSaveable { mutableStateOf("") }
    GroupStack {
        FgGroup(footer = "Se suman a las del servidor. Añadir es libre; quitar una pide aprobación.") {
            FgCell { OutlinedTextField(value = entry, onValueChange = { entry = it }, label = { Text("Palabra o web") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            FgActionRow("Añadir", { if (actions.addWord(entry)) entry = "" }, enabled = entry.isNotBlank())
        }
        if (words.isNotEmpty()) {
            FgGroup { words.forEach { word -> RemovableFgRow(word, "Quitar $word") { actions.removeWord(word) } } }
        }
    }
}

@Composable
private fun AllowedGroups(allowed: List<String>, actions: AdultActions) {
    var entry by rememberSaveable { mutableStateOf("") }
    var phrase by rememberSaveable { mutableStateOf("") }
    GroupStack {
        FgGroup(footer = "Para algo que se bloquea sin ser contenido adulto. Para confirmarlo, escribe a mano la frase.") {
            FgCell {
                Text(AllowPhrase.PHRASE)
                OutlinedTextField(value = entry, onValueChange = { entry = it }, label = { Text("Web o palabra a permitir") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                HandTypedField(phrase, { phrase = it })
            }
            FgActionRow("Permitir", { if (actions.allow(entry, phrase)) { phrase = ""; entry = "" } }, enabled = entry.isNotBlank() && phrase.isNotBlank())
        }
        if (allowed.isNotEmpty()) {
            FgGroup { allowed.forEach { item -> RemovableFgRow(item, "Dejar de permitir $item") { actions.disallow(item) } } }
        }
    }
}

@Composable
private fun ExemptAppsGroups(apps: List<String>, actions: AdultActions) {
    GroupStack {
        if (apps.isNotEmpty()) {
            val labels = rememberAppLabels(apps)
            FgGroup {
                apps.forEach { app -> FgCell { RemovableAppRow(app, labels[app] ?: app, "Volver a leer ${labels[app] ?: app}", { actions.readAgain(app) }) } }
            }
        }
        FgGroup(
            footer = "En estas apps el filtro no lee lo que sale en pantalla, así que escribir ahí sobre el problema que tienes no las bloquea. De serie están las de notas, documentos, agenda, " +
                "tareas, reloj y calculadora (Obsidian, Keep, Notion, Docs…). Los navegadores nunca: sus webs se miran siempre. Añadir una app pide aprobación; volver a leerla, no.",
        ) {
            FgActionRow("Elegir apps", actions.chooseExemptApps)
        }
    }
}
