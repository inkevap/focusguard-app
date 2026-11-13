package com.focusguard.ui.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.focusguard.model.SubAppRule
import com.focusguard.ui.components.FgCheckRow
import com.focusguard.ui.components.FgGroup

/** Bloquear solo una parte de una app (Shorts, Reels, Stories…) sin bloquear la app entera. */
@Composable
internal fun SubAppsCard(
    rules: List<SubAppRule>,
    selected: Set<String>,
    onToggle: (id: String, blocked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupStack(modifier) {
        FgGroup(footer = "Bloquea solo esa parte: cuando aparece, la app vuelve atrás. El resto de la app sigue funcionando.") {
            rules.forEach { rule -> FgCheckRow(rule.label, rule.id in selected, { onToggle(rule.id, it) }) }
        }
    }
}
