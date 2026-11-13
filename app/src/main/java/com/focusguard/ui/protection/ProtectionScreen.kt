package com.focusguard.ui.protection

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.EmbeddedBrowserPolicy
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.model.ApprovalMode
import com.focusguard.model.StrictState
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.formatLocal
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.label
import com.focusguard.ui.profiles.GroupStack
import com.focusguard.ui.rememberTrustedNowMs
import java.time.Instant

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
    onOpenImages: () -> Unit,
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
        onOpenImages = onOpenImages,
        onAdultBlockChange = viewModel::setAdultBlock,
        onNewAppsChange = viewModel::setNewApps,
        onEmbeddedChange = viewModel::setEmbeddedBrowsers,
        onFoldFloatingChange = viewModel::setFoldFloating,
        onRequestAdminOff = viewModel::requestAdminOff,
    )
}

/** Las subpantallas de Protección: lo que no cabe en una fila. */
private enum class ProtectionPage(val title: String) {
    UNINSTALL("Contra la desinstalación"),
    NEW_APPS("Apps recién instaladas"),
    EMBEDDED("Navegadores dentro de apps"),
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
    onOpenImages: () -> Unit,
    onAdultBlockChange: (Boolean) -> Unit,
    onNewAppsChange: (NewAppsPolicy) -> Unit,
    onEmbeddedChange: (EmbeddedBrowserPolicy) -> Unit,
    onFoldFloatingChange: (Boolean) -> Unit,
    onRequestAdminOff: () -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf<ProtectionPage?>(null) }
    val adminGranted = state.adminGrantUntilMs > state.nowMs
    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen("Protección", large = true, snackbar = snackbar) {
                item { PermissionsHero(state.missingPermissions, onOpenPermissions) }
                item {
                    FgGroup(header = "Compromiso") {
                        FgRow("Modo estricto", icon = FgIcons.Lock, value = if (state.strictState == StrictState.INACTIVE) "Inactivo" else state.strictState.label(), onClick = onOpenStrict)
                        FgRow(
                            "Aprobaciones", icon = FgIcons.CheckCircle, value = state.approvalMode.label(),
                            subtitle = when (state.pendingApprovals) {
                                0 -> null
                                1 -> "1 solicitud espera una respuesta"
                                else -> "${state.pendingApprovals} solicitudes esperan una respuesta"
                            },
                            onClick = onOpenApprovals,
                        )
                        FgRow("Contra la desinstalación", icon = FgIcons.Shield, value = if (adminGranted) "Permiso abierto" else "Protegido", onClick = { page = ProtectionPage.UNINSTALL })
                    }
                }
                item {
                    FgGroup(header = "Filtros") {
                        FgSwitchRow(
                            "Contenido adulto", state.adultBlock, onAdultBlockChange,
                            subtitle = "Si lo apagas con una aprobación, se enciende solo pasado un día.",
                        )
                        FgRow("Listas, palabras y excepciones", subtitle = "Webs, búsquedas y páginas para adultos", onClick = onOpenAdult)
                        FgRow("Filtro DNS", icon = FgIcons.Globe, onClick = onOpenDns)
                        FgRow("Detección de imágenes", icon = FgIcons.Block, onClick = onOpenImages)
                    }
                }
                item {
                    FgGroup(header = "Más bloqueos") {
                        FgRow("Apps recién instaladas", value = if (state.newApps.enabled) formatAmountOfTime(state.newApps.hours * 60L) else "No", onClick = { page = ProtectionPage.NEW_APPS })
                        FgRow(
                            "Navegadores dentro de apps",
                            value = when {
                                !state.embedded.enabled -> "No"
                                state.embedded.allApps -> "Todas"
                                else -> "Solo algunas"
                            },
                            onClick = { page = ProtectionPage.EMBEDDED },
                        )
                        FgSwitchRow(
                            "Cerrar ventanas flotantes", state.foldFloating, onFoldFloatingChange,
                            subtitle = "Al bloquear una app, intenta cerrar también su ventana de imagen en imagen. Depende del teléfono y puede no funcionar.",
                        )
                    }
                }
                item {
                    FgGroup {
                        FgRow("¿Algo no bloquea?", icon = FgIcons.Info, subtitle = "Revisa permisos, batería y el propio servicio", onClick = onOpenTroubleshooter)
                        FgRow("Permisos", value = if (state.missingPermissions > 0) "Faltan ${state.missingPermissions}" else "Todo listo", onClick = onOpenPermissions)
                    }
                }
            }
        },
        sub = { current ->
            FgScreen(current.title, onBack = { page = null }, snackbar = snackbar) {
                item {
                    when (current) {
                        ProtectionPage.UNINSTALL -> UninstallGroups(adminGranted, state.adminGrantUntilMs, onRequestAdminOff)
                        ProtectionPage.NEW_APPS -> NewAppsCard(state.newApps, onNewAppsChange)
                        ProtectionPage.EMBEDDED -> EmbeddedBrowsersCard(state.embedded, state.detectedWebViewApps, onEmbeddedChange)
                    }
                }
            }
        },
    )
}

/** Lo que falta o «Todo en orden»; si faltan permisos, un toque los abre. */
@Composable
private fun PermissionsHero(missing: Int, onOpenPermissions: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (missing > 0) {
        FgHero(
            if (missing == 1) "Falta 1 permiso" else "Faltan $missing permisos",
            body = "Sin ellos FocusGuard no puede bloquear nada.", pill = "Dar permisos",
            container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer, onClick = onOpenPermissions,
        )
    } else {
        FgHero("Todo en orden", body = "FocusGuard tiene los permisos que necesita para bloquear.", accent = true)
    }
}

@Composable
private fun UninstallGroups(granted: Boolean, grantUntilMs: Long, onRequestAdminOff: () -> Unit) {
    GroupStack {
        FgNote(
            "Mientras el administrador del dispositivo esté activo, Android no deja desinstalar FocusGuard, y sin el servicio de accesibilidad nada vigila esa pantalla. " +
                "Desactivar cualquiera de los dos pide permiso: una aprobación, o tu compañero.",
        )
        if (granted) {
            FgNote("Tienes permiso para desactivarlos en los ajustes de Android hasta las ${formatLocal(Instant.ofEpochMilli(grantUntilMs))}.")
        } else {
            FgGroup { FgActionRow("Pedir permiso para desactivarlos", onRequestAdminOff) }
        }
    }
}
