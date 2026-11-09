package com.focusguard.ui.profiles

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
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
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.rememberMessageSnackbar

/** Atajo «Quitar bloqueos» (F5): pide apagar todos los perfiles, con las mismas reglas y aprobaciones que apagarlos uno a uno. */
@Composable
fun UnlockAllScreen(viewModel: ProfilesViewModel, onBack: () -> Unit) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val on = remember(profiles) { profiles.filter { it.enabled } }

    Scaffold(
        snackbarHost = { SnackbarHost(rememberMessageSnackbar(message, viewModel::clearMessage)) },
        topBar = {
            FgTopBar("Quitar bloqueos", onBack = onBack)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    SectionTitle("Perfiles encendidos")
                    Hint(
                        if (on.isEmpty()) "No hay ninguno: no hay nada que quitar."
                        else "Apagar un perfil pasa por las mismas aprobaciones que hacerlo desde Inicio. Con el modo estricto activo no se puede.",
                    )
                    on.forEach { Text("${it.emoji} ${it.title}".trim(), modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
            item { PrimaryButton("Pedir apagarlos todos", { confirm = true }, Modifier.fillMaxWidth(), enabled = on.isNotEmpty()) }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("¿Quitar todos los bloqueos?") },
            text = { Text("Se apagarán los perfiles encendidos o se pedirá aprobación para cada uno.") },
            confirmButton = { TextButton(onClick = { confirm = false; viewModel.disableAll() }) { Text("Pedir") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}
