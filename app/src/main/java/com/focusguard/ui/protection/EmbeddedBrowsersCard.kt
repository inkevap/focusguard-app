package com.focusguard.ui.protection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.focusguard.appLabel
import com.focusguard.decision.EmbeddedBrowserPolicy
import com.focusguard.decision.EmbeddedBrowsers
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.AppIcon
import com.focusguard.ui.components.RemovableAppRow
import com.focusguard.ui.components.rememberAppLabels
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.profiles.AppPickerDialog
import com.focusguard.ui.profiles.loadInstalledApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Navegadores dentro de otras apps (B8): bloquea las apps mientras enseñan una vista web propia. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmbeddedBrowsersCard(
    policy: EmbeddedBrowserPolicy,
    detected: Set<String>,
    onChange: (EmbeddedBrowserPolicy) -> Unit,
    modifier: Modifier = Modifier,
    installed: Set<String>? = null,
) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    // Los nombres de apps instaladas tardan en resolverse: se hace fuera del hilo principal.
    val installedApps by produceState(installed ?: emptySet()) {
        if (installed == null) value = withContext(Dispatchers.Default) { loadInstalledApps(context).mapTo(mutableSetOf()) { it.packageName } }
    }
    val suggestions = remember(installedApps, detected) { (EmbeddedBrowsers.KNOWN.filter { it in installedApps } + detected).distinct() }

    AppCard(modifier) {
        Column {
            SwitchRow(
                title = "Navegadores dentro de apps",
                subtitle = "Facebook, Instagram o Line abren las webs en su propia vista y se saltan tu bloqueo de webs. Con esto se bloquea la app mientras enseña una.",
                checked = policy.enabled,
                onCheckedChange = { onChange(policy.copy(enabled = it)) },
            )
            if (policy.enabled) {
                ChipChoices(
                    options = listOf(true to "Todas las apps", false to "Solo las que elija"),
                    selected = policy.allApps,
                    onSelect = { onChange(policy.copy(allApps = it)) },
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (policy.allApps) {
                    Hint("Cualquier app con una vista web se bloquea, también las de inicio de sesión. Si molesta, elige solo algunas.")
                } else {
                    val chosenLabels = rememberAppLabels(policy.apps)
                    policy.apps.sortedBy { chosenLabels[it]?.lowercase() }.forEach { pkg ->
                        RemovableAppRow(pkg, chosenLabels[pkg] ?: pkg, "Quitar ${chosenLabels[pkg] ?: pkg}", { onChange(policy.copy(apps = policy.apps - pkg)) })
                    }
                    SecondaryButton(
                        text = if (policy.apps.isEmpty()) "Elegir apps" else "Cambiar las apps elegidas",
                        onClick = { picking = true },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (suggestions.isNotEmpty()) {
                        Hint("Sugeridas")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            suggestions.forEach { pkg ->
                                FilterChip(
                                    selected = pkg in policy.apps,
                                    onClick = { onChange(policy.copy(apps = if (pkg in policy.apps) policy.apps - pkg else policy.apps + pkg)) },
                                    label = { Text(context.appLabel(pkg)) },
                                    leadingIcon = { AppIcon(pkg, size = 24.dp) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        AppPickerDialog(
            selected = policy.apps,
            onConfirm = { onChange(policy.copy(apps = it)); picking = false },
            onDismiss = { picking = false },
        )
    }
}
