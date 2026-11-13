package com.focusguard.ui.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.model.Profile
import com.focusguard.ui.components.EmptyState
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgTextAction
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.rememberTrustedNowMs

/** La pestaña Perfiles: la lista con su interruptor; tocar uno abre su ficha. */
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onTemplates: () -> Unit,
    onUnlockAll: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val nowMs = rememberTrustedNowMs(5_000L)
    val locked = remember(nowMs) { viewModel.isEditLocked(nowMs) }
    ProfilesContent(profiles, locked, rememberMessageSnackbar(message, viewModel::clearMessage), viewModel::setEnabled, onOpen, onNew, onTemplates, onUnlockAll)
}

@Composable
internal fun ProfilesContent(
    profiles: List<Profile>,
    locked: Boolean,
    snackbar: SnackbarHostState,
    onToggle: (Long, Boolean) -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onTemplates: () -> Unit,
    onUnlockAll: () -> Unit,
) {
    FgScreen("Perfiles", large = true, snackbar = snackbar, actions = { FgTextAction("Nuevo", onNew, enabled = !locked) }) {
        if (profiles.isEmpty()) {
            item {
                EmptyState(
                    title = "Aún no tienes perfiles",
                    body = "Un perfil dice qué apps y webs bloquear y cuándo. Crea el primero en menos de un minuto.",
                    actionLabel = "Crear mi primer perfil",
                    onAction = onNew,
                )
            }
        } else {
            item {
                FgGroup(footer = if (locked) "El modo estricto está activo: puedes ver los perfiles, pero no cambiarlos ni apagarlos." else null) {
                    profiles.forEach { profile ->
                        FgRow(
                            "${profile.emoji} ${profile.title}".trim(),
                            subtitle = profileLine(profile),
                            titleColor = if (profile.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onOpen(profile.id) },
                            trailing = {
                                Switch(
                                    checked = profile.enabled,
                                    onCheckedChange = { onToggle(profile.id, it) },
                                    enabled = !(locked && profile.enabled),
                                    modifier = Modifier.semantics { contentDescription = "Activar ${profile.title}" },
                                )
                            },
                        )
                    }
                }
            }
        }
        item {
            FgGroup {
                FgRow("Plantillas", subtitle = "Redes, vídeo, juegos, descanso nocturno…", icon = FgIcons.Apps, iconColor = MaterialTheme.colorScheme.secondary, onClick = onTemplates)
                FgRow("Quitar bloqueos", subtitle = "Pide apagar todos los perfiles", icon = FgIcons.Lock, iconColor = MaterialTheme.colorScheme.outline, onClick = onUnlockAll)
            }
        }
    }
}

/** Una línea que dice cuándo actúa el perfil y sobre qué. */
internal fun profileLine(profile: Profile): String {
    val conditions = conditionsSummary(profile)
    // «Siempre activo» engañaría si el perfil depende de una red, del cargador o de un lugar.
    val schedule = if (profile.intervals.isNotEmpty() || conditions == null) scheduleSummary(profile.intervals) else null
    return listOfNotNull(schedule, conditions, limitSummary(profile.limit), contentSummary(profile)).joinToString(" · ")
}
