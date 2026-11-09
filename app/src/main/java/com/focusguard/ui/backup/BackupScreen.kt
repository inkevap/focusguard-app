package com.focusguard.ui.backup

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import com.focusguard.ui.components.PasswordField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SecureWindowEffect
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.StatusChip
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatLocal
import java.time.Instant

class BackupActions(
    val save: (String, String) -> Unit,
    val restore: (String) -> Unit,
    val deleteAll: () -> Unit,
    val enableSync: (String, String) -> Unit,
    val syncNow: () -> Unit,
    val disableSync: () -> Unit,
    val deleteSyncRemote: () -> Unit,
)

@Composable
fun BackupScreen(viewModel: BackupViewModel, onBack: () -> Unit) {
    val last by viewModel.lastBackupMs.collectAsStateWithLifecycle()
    val syncEnabled by viewModel.syncEnabled.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSyncMs.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    // Pide una frase: sin capturas de pantalla ni vista previa en apps recientes.
    SecureWindowEffect()
    Scaffold(
        topBar = {
            FgTopBar("Copia de seguridad", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        BackupContent(
            last, syncEnabled, lastSync, viewModel.busy,
            BackupActions(viewModel::save, viewModel::restore, viewModel::deleteAll, viewModel::enableSync, viewModel::syncNow, viewModel::disableSync, viewModel::deleteSyncRemote),
            Modifier.padding(padding),
        )
    }
}

@Composable
internal fun BackupContent(lastBackupMs: Long?, syncEnabled: Boolean, lastSyncMs: Long?, busy: Boolean, actions: BackupActions, modifier: Modifier = Modifier) {
    // Con `remember`, no `rememberSaveable`: la frase no debe acabar en el estado guardado de la actividad.
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppCard {
                SectionTitle("Tu configuración, a salvo", FgIcons.Shield)
                Hint(
                    "Guarda tus perfiles, lugares, ajustes, pantalla de bloqueo y modo estricto en tu servidor, cifrados con una frase que solo " +
                        "sabes tú: el servidor no puede leerlos. Si olvidas la frase, la copia no se puede recuperar. No incluye el PIN ni el historial.",
                )
                Hint(lastBackupMs?.let { "Última copia: ${formatLocal(Instant.ofEpochMilli(it), withDay = true)}." } ?: "Todavía no hay copia.")
            }
        }
        item {
            AppCard {
                SectionTitle("Guardar o restaurar")
                PasswordField("Frase de la copia", passphrase, { passphrase = it }, Modifier.padding(top = 8.dp))
                PasswordField("Repite la frase (solo al guardar)", repeat, { repeat = it }, Modifier.padding(top = 8.dp))
                PrimaryButton("Guardar ahora", { actions.save(passphrase, repeat) }, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !busy)
                SecondaryButton("Restaurar la última copia", { actions.restore(passphrase) }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
                Hint("Restaurar añade tus perfiles y nunca quita ni afloja una protección que ya tengas.")
            }
        }
        item { SyncCard(syncEnabled, lastSyncMs, busy, actions) }
        item {
            AppCard {
                SectionTitle("Borrar")
                Hint("Quita del servidor todas tus copias. Lo que tienes en este teléfono no cambia.")
                SecondaryButton("Borrar mis copias", actions.deleteAll, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
            }
        }
    }
}

/** La sincronización entre dispositivos (N4): activarla con una frase, o ver cuándo fue la última y dejarla. */
@Composable
internal fun SyncCard(syncEnabled: Boolean, lastSyncMs: Long?, busy: Boolean, actions: BackupActions) {
    // Con `remember`, no `rememberSaveable`: la frase no debe acabar en el estado guardado de la actividad.
    var syncPassphrase by remember { mutableStateOf("") }
    var syncRepeat by remember { mutableStateOf("") }
    AppCard {
        SectionTitle("Sincronizar con otros dispositivos", FgIcons.Apps)
        Hint(
            "Mantiene entre tus teléfonos las palabras propias del filtro y los interruptores de contenido adulto, apps nuevas y navegadores " +
                "integrados, cifrados con una frase que solo sabes tú. Solo suma: lo que quitas en un teléfono no se quita en los otros, y nada afloja " +
                "una protección. Los perfiles no se sincronizan: usa la copia de seguridad.",
        )
        if (syncEnabled) {
            StatusChip("Sincronización activada")
            Hint(lastSyncMs?.let { "Última vez: ${formatLocal(Instant.ofEpochMilli(it), withDay = true)}." } ?: "Todavía no se ha sincronizado.")
            PrimaryButton("Sincronizar ahora", actions.syncNow, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
            SecondaryButton("Dejar de sincronizar en este teléfono", actions.disableSync, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
            SecondaryButton("Borrar lo sincronizado del servidor", actions.deleteSyncRemote, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !busy)
        } else {
            Hint("En el primer dispositivo elige una frase; en los demás escribe la misma. Se guarda cifrada en este teléfono para sincronizar sola cada pocas horas. La primera vez hay que repetirla.")
            PasswordField("Frase de la sincronización", syncPassphrase, { syncPassphrase = it }, Modifier.padding(top = 8.dp))
            PasswordField("Repite la frase (solo en el primer dispositivo)", syncRepeat, { syncRepeat = it }, Modifier.padding(top = 8.dp))
            PrimaryButton("Activar", { actions.enableSync(syncPassphrase, syncRepeat) }, Modifier.fillMaxWidth().padding(top = 12.dp), enabled = !busy)
        }
    }
}
