package com.focusguard.ui.backup

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.PasswordField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SecureWindowEffect
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatLocal
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.profiles.BottomAction
import com.focusguard.ui.profiles.GroupStack
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
    BackupContent(
        last, syncEnabled, lastSync, viewModel.busy,
        BackupActions(viewModel::save, viewModel::restore, viewModel::deleteAll, viewModel::enableSync, viewModel::syncNow, viewModel::disableSync, viewModel::deleteSyncRemote),
        onBack = onBack, snackbar = snackbar,
    )
}

/** Las subpantallas de la copia de seguridad. */
private enum class BackupPage(val title: String) {
    SAVE("Guardar o restaurar"),
    SYNC("Sincronización"),
}

@Composable
internal fun BackupContent(
    lastBackupMs: Long?, syncEnabled: Boolean, lastSyncMs: Long?, busy: Boolean, actions: BackupActions, modifier: Modifier = Modifier,
    onBack: () -> Unit = {}, snackbar: SnackbarHostState? = null,
) {
    var page by rememberSaveable { mutableStateOf<BackupPage?>(null) }
    // Con `remember`, no `rememberSaveable`: la frase no debe acabar en el estado guardado de la actividad.
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val lastBackup = lastBackupMs?.let { "Última copia: ${formatLocal(Instant.ofEpochMilli(it), withDay = true)}." } ?: "Todavía no hay copia."
    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen("Copia de seguridad", modifier = modifier, onBack = onBack, snackbar = snackbar) {
                item {
                    FgHero(
                        "Tu configuración, a salvo",
                        body = "Guarda tus perfiles, lugares, ajustes, pantalla de bloqueo y modo estricto en tu servidor, cifrados con una frase que solo " +
                            "sabes tú: el servidor no puede leerlos. Si olvidas la frase, la copia no se puede recuperar. No incluye el PIN ni el historial.",
                        pill = lastBackup,
                    )
                }
                item {
                    FgGroup {
                        FgRow("Guardar o restaurar", icon = FgIcons.Shield, onClick = { page = BackupPage.SAVE })
                        FgRow("Sincronizar con otros dispositivos", icon = FgIcons.Apps, value = if (syncEnabled) "Activada" else "No", onClick = { page = BackupPage.SYNC })
                    }
                }
                item {
                    FgGroup(footer = "Quita del servidor todas tus copias. Lo que tienes en este teléfono no cambia.") {
                        FgActionRow("Borrar mis copias", actions.deleteAll, danger = true, enabled = !busy)
                    }
                }
            }
        },
        sub = { current ->
            when (current) {
                BackupPage.SAVE -> FgScreen(
                    current.title, onBack = { page = null }, snackbar = snackbar,
                    bottomBar = {
                        BottomAction {
                            PrimaryButton("Guardar ahora", { actions.save(passphrase, repeat) }, Modifier.fillMaxWidth(), enabled = !busy)
                            SecondaryButton("Restaurar la última copia", { actions.restore(passphrase) }, Modifier.fillMaxWidth(), enabled = !busy)
                        }
                    },
                ) {
                    item {
                        FgGroup(footer = "Restaurar añade tus perfiles y nunca quita ni afloja una protección que ya tengas.") {
                            FgCell { PasswordField("Frase de la copia", passphrase, { passphrase = it }) }
                            FgCell { PasswordField("Repite la frase (solo al guardar)", repeat, { repeat = it }) }
                        }
                    }
                }
                BackupPage.SYNC -> FgScreen(current.title, onBack = { page = null }, snackbar = snackbar) {
                    item { SyncCard(syncEnabled, lastSyncMs, busy, actions) }
                }
            }
        },
    )
}

private const val SYNC_EXPLANATION =
    "Mantiene entre tus teléfonos las palabras propias del filtro y los interruptores de contenido adulto, apps nuevas y navegadores " +
        "integrados, cifrados con una frase que solo sabes tú. Solo suma: lo que quitas en un teléfono no se quita en los otros, y nada afloja " +
        "una protección. Los perfiles no se sincronizan: usa la copia de seguridad."

/** La sincronización entre dispositivos (N4): activarla con una frase, o ver cuándo fue la última y dejarla. */
@Composable
internal fun SyncCard(syncEnabled: Boolean, lastSyncMs: Long?, busy: Boolean, actions: BackupActions) {
    // Con `remember`, no `rememberSaveable`: la frase no debe acabar en el estado guardado de la actividad.
    var syncPassphrase by remember { mutableStateOf("") }
    var syncRepeat by remember { mutableStateOf("") }
    GroupStack {
        if (syncEnabled) {
            FgGroup(footer = SYNC_EXPLANATION) {
                FgRow(
                    "Sincronización activada",
                    subtitle = lastSyncMs?.let { "Última vez: ${formatLocal(Instant.ofEpochMilli(it), withDay = true)}." } ?: "Todavía no se ha sincronizado.",
                )
                FgActionRow("Sincronizar ahora", actions.syncNow, enabled = !busy)
            }
            FgGroup {
                FgActionRow("Dejar de sincronizar en este teléfono", actions.disableSync, danger = true, enabled = !busy)
                FgActionRow("Borrar lo sincronizado del servidor", actions.deleteSyncRemote, danger = true, enabled = !busy)
            }
        } else {
            FgGroup(
                footer = "$SYNC_EXPLANATION En el primer dispositivo elige una frase; en los demás escribe la misma. " +
                    "Se guarda cifrada en este teléfono para sincronizar sola cada pocas horas. La primera vez hay que repetirla.",
            ) {
                FgCell { PasswordField("Frase de la sincronización", syncPassphrase, { syncPassphrase = it }) }
                FgCell { PasswordField("Repite la frase (solo en el primer dispositivo)", syncRepeat, { syncRepeat = it }) }
            }
            PrimaryButton("Activar", { actions.enableSync(syncPassphrase, syncRepeat) }, Modifier.fillMaxWidth(), enabled = !busy)
        }
    }
}
