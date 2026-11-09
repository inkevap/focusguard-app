package com.focusguard.ui.profiles

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.model.SubAppRule
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.CheckRow
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle

/** Bloquear solo una parte de una app (Shorts, Reels, Stories…) sin bloquear la app entera. */
@Composable
internal fun SubAppsCard(
    rules: List<SubAppRule>,
    selected: Set<String>,
    onToggle: (id: String, blocked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(selected.isNotEmpty()) }
    AppCard(modifier) {
        SectionTitle("Dentro de apps", FgIcons.Apps)
        Hint("Bloquea solo esa parte: cuando aparece, la app vuelve atrás. El resto de la app sigue funcionando.")
        if (!expanded) {
            SecondaryButton(
                text = if (selected.isEmpty()) "Elegir secciones" else "Secciones elegidas: ${selected.size}",
                onClick = { expanded = true },
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            rules.forEach { rule ->
                CheckRow(rule.label, hint = null, checked = rule.id in selected, onCheckedChange = { onToggle(rule.id, it) })
            }
        }
    }
}
