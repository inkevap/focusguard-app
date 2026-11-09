package com.focusguard.ui.adult

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.HandTypedField
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.RemovableAppRow
import com.focusguard.ui.components.RemovableRow
import com.focusguard.ui.components.rememberAppLabels
import com.focusguard.ui.profiles.AppPickerDialog
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatLocal
import com.focusguard.ui.plural
import com.focusguard.decision.AllowPhrase
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
    val browsers = androidx.compose.runtime.remember { com.focusguard.service.BrowserTable.fromAssets(ctx) }
    Scaffold(
        topBar = {
            FgTopBar("Contenido adulto", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AdultContentBody(
            state = state,
            syncing = viewModel.syncing,
            actions = AdultActions(viewModel::refreshLists, viewModel::addWord, viewModel::removeWord, viewModel::allow, viewModel::disallow, { picking = true }, viewModel::readAgain),
            modifier = Modifier.padding(padding),
        )
    }
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

@Composable
internal fun AdultContentBody(state: AdultUiState, syncing: Boolean, actions: AdultActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.offUntilMs?.let { until ->
            item {
                AppCard {
                    SectionTitle("Apagado por un rato")
                    Hint("Se enciende solo ${formatLocal(Instant.ofEpochMilli(until), withDay = true)}.")
                }
            }
        }
        item {
            AppCard {
                SectionTitle("Listas del servidor", FgIcons.Globe)
                if (state.domains == 0 && state.words == 0) {
                    Hint("Aún no se han descargado. Se bajan solas cuando hay internet; el bloqueo por palabras y por dominios funciona con lo que haya en el teléfono.")
                } else {
                    Hint("${plural(state.domains, "dominio", "dominios")} y ${plural(state.words, "palabra", "palabras")} (versión ${state.version}). Funcionan sin conexión.")
                }
                SecondaryButton(if (syncing) "Actualizando…" else "Actualizar ahora", actions.refreshLists, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !syncing)
            }
        }
        item { WordsCard(state.userWords, actions) }
        item { AllowedCard(state.allowed, actions) }
        item { ExemptAppsCard(state.exemptApps, actions) }
    }
}

@Composable
private fun WordsCard(words: List<String>, actions: AdultActions) {
    var entry by rememberSaveable { mutableStateOf("") }
    AppCard {
        SectionTitle("Tus palabras y webs", FgIcons.Block)
        Hint("Se suman a las del servidor. Añadir es libre; quitar una pide aprobación.")
        OutlinedTextField(value = entry, onValueChange = { entry = it }, label = { Text("Palabra o web") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        PrimaryButton("Añadir", { if (actions.addWord(entry)) entry = "" }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = entry.isNotBlank())
        words.forEach { word -> RemovableRow(word, "Quitar $word") { actions.removeWord(word) } }
    }
}

@Composable
private fun AllowedCard(allowed: List<String>, actions: AdultActions) {
    var entry by rememberSaveable { mutableStateOf("") }
    var phrase by rememberSaveable { mutableStateOf("") }
    AppCard {
        SectionTitle("Dejar pasar")
        Hint("Para algo que se bloquea sin ser contenido adulto. Para confirmarlo, escribe a mano la frase:")
        Text(AllowPhrase.PHRASE, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(value = entry, onValueChange = { entry = it }, label = { Text("Web o palabra a permitir") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        HandTypedField(phrase, { phrase = it }, Modifier.padding(top = 8.dp))
        PrimaryButton("Permitir", { if (actions.allow(entry, phrase)) { phrase = ""; entry = "" } }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = entry.isNotBlank() && phrase.isNotBlank())
        allowed.forEach { item -> RemovableRow(item, "Dejar de permitir $item") { actions.disallow(item) } }
    }
}

@Composable
private fun ExemptAppsCard(apps: List<String>, actions: AdultActions) {
    AppCard {
        SectionTitle("Apps cuyo texto no se lee", FgIcons.Shield)
        Hint("En estas apps el filtro no lee lo que sale en pantalla, así que escribir ahí sobre el problema que tienes no las bloquea. De serie están las de notas, documentos, agenda, tareas, reloj y calculadora (Obsidian, Keep, Notion, Docs…). Los navegadores nunca: sus webs se miran siempre. Añadir una app pide aprobación; volver a leerla, no.")
        val labels = rememberAppLabels(apps)
        apps.forEach { app -> RemovableAppRow(app, labels[app] ?: app, "Volver a leer ${labels[app] ?: app}", { actions.readAgain(app) }) }
        SecondaryButton("Elegir apps", actions.chooseExemptApps, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}
