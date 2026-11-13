package com.focusguard.ui.profiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.rememberMessageSnackbar

/** Atajo «Quitar bloqueos» (F5): pide apagar todos los perfiles, con las mismas reglas y aprobaciones que apagarlos uno a uno. */
@Composable
fun UnlockAllScreen(viewModel: ProfilesViewModel, onBack: () -> Unit) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    val on = remember(profiles) { profiles.filter { it.enabled } }

    FgScreen(
        "Quitar bloqueos",
        onBack = onBack,
        snackbar = rememberMessageSnackbar(message, viewModel::clearMessage),
        bottomBar = {
            BottomAction { PrimaryButton("Pedir apagarlos todos", { confirm = true }, Modifier.fillMaxWidth(), enabled = on.isNotEmpty()) }
        },
    ) {
        item {
            if (on.isEmpty()) {
                FgNote("No hay ninguno: no hay nada que quitar.", Modifier.padding(horizontal = 16.dp))
            } else {
                FgGroup(
                    header = "Perfiles encendidos",
                    footer = "Apagar un perfil pasa por las mismas aprobaciones que hacerlo desde Inicio. Con el modo estricto activo no se puede.",
                ) {
                    on.forEach { FgRow("${it.emoji} ${it.title}".trim()) }
                }
            }
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
