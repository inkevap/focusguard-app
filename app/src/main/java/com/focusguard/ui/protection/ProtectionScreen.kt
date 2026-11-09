package com.focusguard.ui.protection

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.EmbeddedBrowserPolicy
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.model.ApprovalMode
import com.focusguard.model.StrictState
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.MissingPermissionsCard
import com.focusguard.ui.components.ScreenHeader
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.label
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.StatusChip
import com.focusguard.ui.components.SwitchCard
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.formatLocal
import com.focusguard.ui.rememberTrustedNowMs

/** Todo lo que Protección necesita para dibujarse. */
data class ProtectionUiState(
    val adultBlock: Boolean,
    val newApps: NewAppsPolicy,
    val embedded: EmbeddedBrowserPolicy,
    val detectedWebViewApps: Set<String>,
    val foldFloating: Boolean,
    val approvalMode: ApprovalMode,
    val pendingApprovals: Int,
    val strictState: StrictState,
    val missingPermissions: Int,
    /** Hasta cuándo se puede desactivar el administrador del dispositivo (ms, hora de confianza); 0 si no hay permiso. */
    val adminGrantUntilMs: Long = 0,
    val nowMs: Long = 0,
)

@Composable
fun ProtectionScreen(
    viewModel: ProtectionViewModel,
    missingPermissions: Int,
    onOpenPermissions: () -> Unit,
    onOpenStrict: () -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenTroubleshooter: () -> Unit,
    onOpenAdult: () -> Unit,
    onOpenDns: () -> Unit,
) {
    val adultBlock by viewModel.adultBlockEnabled.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val newApps by viewModel.newApps.collectAsStateWithLifecycle()
    val embedded by viewModel.embeddedBrowsers.collectAsStateWithLifecycle()
    val detected by viewModel.detectedWebViewApps.collectAsStateWithLifecycle()
    val foldFloating by viewModel.foldFloating.collectAsStateWithLifecycle()
    val approvalMode by viewModel.approvalMode.collectAsStateWithLifecycle()
    val pending by viewModel.pendingApprovals.collectAsStateWithLifecycle()
    // El estado del modo estricto solo cambia en los límites de su horario.
    val nowMs = rememberTrustedNowMs(10_000L)
    val strictState = remember(nowMs) { viewModel.strictState(nowMs) }
    val adminGrantUntilMs by viewModel.adminOffGrantUntilMs.collectAsStateWithLifecycle()

    ProtectionContent(
        state = ProtectionUiState(adultBlock, newApps, embedded, detected, foldFloating, approvalMode, pending.size, strictState, missingPermissions, adminGrantUntilMs, nowMs),
        snackbar = rememberMessageSnackbar(message, viewModel::clearMessage),
        onOpenPermissions = onOpenPermissions,
        onOpenStrict = onOpenStrict,
        onOpenApprovals = onOpenApprovals,
        onOpenTroubleshooter = onOpenTroubleshooter,
        onOpenAdult = onOpenAdult,
        onOpenDns = onOpenDns,
        onAdultBlockChange = viewModel::setAdultBlock,
        onNewAppsChange = viewModel::setNewApps,
        onEmbeddedChange = viewModel::setEmbeddedBrowsers,
        onFoldFloatingChange = viewModel::setFoldFloating,
        onRequestAdminOff = viewModel::requestAdminOff,
    )
}

@Composable
internal fun ProtectionContent(
    state: ProtectionUiState,
    snackbar: SnackbarHostState,
    onOpenPermissions: () -> Unit,
    onOpenStrict: () -> Unit,
    onOpenApprovals: () -> Unit,
    onOpenTroubleshooter: () -> Unit,
    onOpenAdult: () -> Unit,
    onOpenDns: () -> Unit,
    onAdultBlockChange: (Boolean) -> Unit,
    onNewAppsChange: (NewAppsPolicy) -> Unit,
    onEmbeddedChange: (EmbeddedBrowserPolicy) -> Unit,
    onFoldFloatingChange: (Boolean) -> Unit,
    onRequestAdminOff: () -> Unit = {},
) {
    Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader("Protección", "Lo que evita que te saltes tus propias reglas", icon = FgIcons.Shield) }
            if (state.missingPermissions > 0) item { MissingPermissionsCard(state.missingPermissions, onOpenPermissions) }
            item {
                AppCard {
                    SectionTitle("Modo estricto", FgIcons.Lock)
                    Hint("Impide editar tus perfiles y entrar en los ajustes que desactivan la app mientras está activo.")
                    StatusChip(state.strictState.label())
                    SecondaryButton("Configurar", onOpenStrict, Modifier.padding(top = 12.dp))
                }
            }
            item {
                AppCard {
                    SectionTitle("Aprobaciones", FgIcons.CheckCircle)
                    Hint("Para apagar algo tendrás que esperar, escribir una frase larga o caminar. Así no lo haces por impulso.")
                    StatusChip(state.approvalMode.label())
                    if (state.pendingApprovals > 0) {
                        Hint(if (state.pendingApprovals == 1) "1 solicitud espera una respuesta." else "${state.pendingApprovals} solicitudes esperan una respuesta.")
                    }
                    SecondaryButton(
                        text = if (state.pendingApprovals == 0) "Configurar" else "Ver y cancelar solicitudes (${state.pendingApprovals})",
                        onClick = onOpenApprovals,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            item {
                AppCard {
                    SectionTitle("Contra la desinstalación", FgIcons.Shield)
                    Hint("Mientras el administrador del dispositivo esté activo, Android no deja desinstalar FocusGuard, y sin el servicio de accesibilidad nada vigila esa pantalla. Desactivar cualquiera de los dos pide permiso: una aprobación, o tu compañero.")
                    if (state.adminGrantUntilMs > state.nowMs) {
                        Hint("Tienes permiso para desactivarlos en los ajustes de Android hasta las ${formatLocal(java.time.Instant.ofEpochMilli(state.adminGrantUntilMs))}.")
                    } else {
                        SecondaryButton("Pedir permiso para desactivarlos", onRequestAdminOff, Modifier.padding(top = 12.dp))
                    }
                }
            }
            item {
                AppCard {
                    SectionTitle("¿Algo no bloquea?", FgIcons.Info)
                    Hint("Revisa qué falta para que FocusGuard funcione bien: permisos, batería y el propio servicio.")
                    SecondaryButton("Revisar", onOpenTroubleshooter, Modifier.padding(top = 12.dp))
                }
            }
            item {
                AppCard {
                    SwitchRow(
                        title = "Contenido adulto",
                        subtitle = "Bloquea webs, búsquedas y páginas para adultos con las listas del servidor y las tuyas. Si lo apagas con una aprobación, se enciende solo pasado un día.",
                        checked = state.adultBlock,
                        onCheckedChange = onAdultBlockChange,
                    )
                    SecondaryButton("Listas, palabras y excepciones", onOpenAdult, Modifier.padding(top = 8.dp))
                }
            }
            item {
                AppCard {
                    SectionTitle("Filtro DNS", FgIcons.Globe)
                    Hint("Bloquea los dominios para adultos en todo el teléfono, también en apps, con tu servidor.")
                    SecondaryButton("Configurar", onOpenDns, Modifier.padding(top = 12.dp))
                }
            }
            item { NewAppsCard(state.newApps, onNewAppsChange) }
            item { EmbeddedBrowsersCard(state.embedded, state.detectedWebViewApps, onEmbeddedChange) }
            item {
                SwitchCard(
                    title = "Cerrar ventanas flotantes",
                    subtitle = "Al bloquear una app, intenta cerrar también su ventana de imagen en imagen. Depende del teléfono y puede no funcionar.",
                    checked = state.foldFloating,
                    onCheckedChange = onFoldFloatingChange,
                )
            }
        }
    }
}
