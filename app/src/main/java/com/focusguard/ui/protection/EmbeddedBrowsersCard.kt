package com.focusguard.ui.protection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.focusguard.ui.components.AppIcon
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRadioRow
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.RemovableAppRow
import com.focusguard.ui.components.rememberAppLabels
import com.focusguard.ui.profiles.AppPickerDialog
import com.focusguard.ui.profiles.GroupStack
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

    GroupStack(modifier) {
        FgGroup(footer = "Facebook, Instagram o Line abren las webs en su propia vista y se saltan tu bloqueo de webs. Con esto se bloquea la app mientras enseña una.") {
            FgSwitchRow("Navegadores dentro de apps", policy.enabled, { onChange(policy.copy(enabled = it)) })
        }
        if (policy.enabled) {
            FgGroup(
                footer = if (policy.allApps) "Cualquier app con una vista web se bloquea, también las de inicio de sesión. Si molesta, elige solo algunas." else null,
            ) {
                FgRadioRow("Todas las apps", policy.allApps, { onChange(policy.copy(allApps = true)) })
                FgRadioRow("Solo las que elija", !policy.allApps, { onChange(policy.copy(allApps = false)) })
            }
            if (!policy.allApps) {
                if (policy.apps.isNotEmpty()) {
                    val chosenLabels = rememberAppLabels(policy.apps)
                    FgGroup(header = "Apps elegidas") {
                        policy.apps.sortedBy { chosenLabels[it]?.lowercase() }.forEach { pkg ->
                            FgCell { RemovableAppRow(pkg, chosenLabels[pkg] ?: pkg, "Quitar ${chosenLabels[pkg] ?: pkg}", { onChange(policy.copy(apps = policy.apps - pkg)) }) }
                        }
                    }
                }
                FgGroup { FgActionRow(if (policy.apps.isEmpty()) "Elegir apps" else "Cambiar las apps elegidas", { picking = true }) }
                if (suggestions.isNotEmpty()) {
                    FgGroup(header = "Sugeridas") {
                        FgCell {
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
    }

    if (picking) {
        AppPickerDialog(
            selected = policy.apps,
            onConfirm = { onChange(policy.copy(apps = it)); picking = false },
            onDismiss = { picking = false },
        )
    }
}
