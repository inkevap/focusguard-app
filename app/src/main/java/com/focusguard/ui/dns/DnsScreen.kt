package com.focusguard.ui.dns

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.dns.DnsStatus
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.RemovableAppRow
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberAppLabels
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.profiles.AppPickerDialog
import com.focusguard.ui.profiles.GroupStack

class DnsActions(
    val setEnabled: (Boolean) -> Unit,
    val exclude: () -> Unit,
    val include: (String) -> Unit,
    val openLog: () -> Unit,
)

@Composable
fun DnsScreen(viewModel: DnsViewModel, onBack: () -> Unit, onOpenLog: () -> Unit) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val excluded by viewModel.excluded.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    var picking by remember { mutableStateOf(false) }
    // El sistema pide el permiso de VPN con su propia pantalla; solo si la acepta se enciende.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.turnOn()
    }
    DnsContent(
        enabled = enabled,
        status = status,
        excluded = excluded.sorted(),
        actions = DnsActions(
            setEnabled = { on ->
                if (!on) viewModel.turnOff() else viewModel.permissionIntent()?.let(permission::launch) ?: viewModel.turnOn()
            },
            exclude = { picking = true },
            include = viewModel::include,
            openLog = onOpenLog,
        ),
        onBack = onBack,
        snackbar = snackbar,
    )
    if (picking) {
        AppPickerDialog(selected = excluded, onConfirm = { viewModel.exclude(it); picking = false }, onDismiss = { picking = false })
    }
}

/** Subpantalla de Filtro DNS. */
private enum class DnsPage(val title: String) { EXCLUDED("Apps fuera del filtro") }

@Composable
internal fun DnsContent(
    enabled: Boolean,
    status: DnsStatus,
    excluded: List<String>,
    actions: DnsActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
) {
    var page by rememberSaveable { mutableStateOf<DnsPage?>(null) }
    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen("Filtro DNS", modifier, onBack = onBack, snackbar = snackbar) {
                item {
                    GroupStack {
                        FgGroup(
                            footer = "Las consultas de nombres de todo el teléfono van cifradas a tu servidor, que rechaza los dominios para adultos y fuerza la búsqueda segura. " +
                                "Necesita tu cuenta. Apagarlo pide aprobación.",
                        ) {
                            FgSwitchRow("Filtro DNS propio", enabled, actions.setEnabled, subtitle = statusText(enabled, status))
                        }
                        if (status is DnsStatus.FailOpen) FgNote("El resolvedor no contesta: mientras tanto se usa el DNS del sistema, sin filtrar.")
                        FgNote("Para que no se pueda apagar sin avisar, activa «VPN siempre activa» para FocusGuard en los ajustes de VPN del sistema.")
                    }
                }
                item {
                    FgGroup(footer = "Las consultas son los dominios que pidió el teléfono en los últimos 30 días. Solo los ves tú, en este teléfono.") {
                        FgRow("Ver las consultas", onClick = actions.openLog)
                        FgRow("Apps fuera del filtro", value = excluded.size.toString(), onClick = { page = DnsPage.EXCLUDED })
                    }
                }
            }
        },
        sub = { current ->
            FgScreen(current.title, modifier, onBack = { page = null }, snackbar = snackbar) {
                item {
                    GroupStack {
                        if (excluded.isNotEmpty()) {
                            val labels = rememberAppLabels(excluded)
                            FgGroup {
                                excluded.forEach { app ->
                                    FgCell { RemovableAppRow(app, labels[app] ?: app, "Volver a filtrar ${labels[app] ?: app}", { actions.include(app) }) }
                                }
                            }
                        }
                        FgGroup(footer = "Sus consultas no pasan por el filtro (algunas apps no funcionan con una VPN). Dejar una fuera pide aprobación; volver a filtrarla, no.") {
                            FgActionRow("Elegir apps", actions.exclude)
                        }
                    }
                }
            }
        },
    )
}

private fun statusText(enabled: Boolean, status: DnsStatus): String = when {
    status is DnsStatus.Error -> status.message
    status is DnsStatus.FailOpen -> "Sin filtrar (el resolvedor no contesta)"
    status is DnsStatus.Running -> "Filtrando"
    enabled -> "Encendido, arrancando…"
    else -> "Apagado"
}
