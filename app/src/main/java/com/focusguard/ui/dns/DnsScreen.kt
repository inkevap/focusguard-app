package com.focusguard.ui.dns

import com.focusguard.ui.icons.FgIcons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.focusguard.dns.DnsStatus
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.RemovableAppRow
import com.focusguard.ui.components.rememberAppLabels
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.StatusChip
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.profiles.AppPickerDialog

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
    Scaffold(
        topBar = {
            FgTopBar("Filtro DNS", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
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
            modifier = Modifier.padding(padding),
        )
    }
    if (picking) {
        AppPickerDialog(selected = excluded, onConfirm = { viewModel.exclude(it); picking = false }, onDismiss = { picking = false })
    }
}

@Composable
internal fun DnsContent(enabled: Boolean, status: DnsStatus, excluded: List<String>, actions: DnsActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppCard {
                SwitchRow(
                    title = "Filtro DNS propio",
                    subtitle = "Las consultas de nombres de todo el teléfono van cifradas a tu servidor, que rechaza los dominios para adultos y fuerza la búsqueda segura. Necesita tu cuenta. Apagarlo pide aprobación.",
                    checked = enabled,
                    onCheckedChange = actions.setEnabled,
                )
                StatusChip(statusText(enabled, status), Modifier.padding(top = 8.dp))
                if (status is DnsStatus.FailOpen) Hint("El resolvedor no contesta: mientras tanto se usa el DNS del sistema, sin filtrar.")
                Hint("Para que no se pueda apagar sin avisar, activa «VPN siempre activa» para FocusGuard en los ajustes de VPN del sistema.")
            }
        }
        item {
            AppCard {
                SectionTitle("Consultas", FgIcons.Chart)
                Hint("Qué dominios pidió el teléfono en los últimos 30 días, bloqueados y permitidos. Solo los ves tú, en este teléfono.")
                SecondaryButton("Ver las consultas", actions.openLog, Modifier.fillMaxWidth())
            }
        }
        item {
            AppCard {
                SectionTitle("Apps fuera del filtro", FgIcons.Globe)
                Hint("Sus consultas no pasan por el filtro (algunas apps no funcionan con una VPN). Dejar una fuera pide aprobación; volver a filtrarla, no.")
                val labels = rememberAppLabels(excluded)
                excluded.forEach { app -> RemovableAppRow(app, labels[app] ?: app, "Volver a filtrar ${labels[app] ?: app}", { actions.include(app) }) }
                SecondaryButton("Elegir apps", actions.exclude, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
    }
}

private fun statusText(enabled: Boolean, status: DnsStatus): String = when {
    status is DnsStatus.Error -> status.message
    status is DnsStatus.FailOpen -> "Sin filtrar (el resolvedor no contesta)"
    status is DnsStatus.Running -> "Filtrando"
    enabled -> "Encendido, arrancando…"
    else -> "Apagado"
}
