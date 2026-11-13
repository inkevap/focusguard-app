package com.focusguard.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.RemovableAppRow
import com.focusguard.ui.components.rememberAppLabels
import com.focusguard.ui.icons.FgIcons

/* Piezas pequeñas que comparten las pantallas de perfiles y sesiones. */

/** Los grupos de una subpantalla escrita como bloque, con el mismo hueco entre grupos que una pantalla. */
@Composable
internal fun GroupStack(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
}

/** Una fila con su botón de quitar. */
@Composable
internal fun RemovableFgRow(title: String, removeDescription: String, modifier: Modifier = Modifier, onRemove: () -> Unit) {
    FgRow(
        title, modifier,
        trailing = { IconButton(onClick = onRemove) { Icon(FgIcons.Delete, contentDescription = removeDescription) } },
    )
}

/** Un aviso de error o de falta de permiso, suelto bajo un grupo. */
@Composable
internal fun ErrorNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = modifier.padding(horizontal = 16.dp))
}

/** Las apps elegidas, cada una con su botón de quitar, y la acción de elegir más. */
@Composable
internal fun SelectedAppsGroups(apps: Set<String>, onChange: (Set<String>) -> Unit, onPick: () -> Unit, modifier: Modifier = Modifier) {
    GroupStack(modifier) {
        if (apps.isNotEmpty()) {
            val labels = rememberAppLabels(apps)
            FgGroup {
                apps.sortedBy { labels[it]?.lowercase() }.forEach { pkg ->
                    val label = labels[pkg] ?: pkg
                    FgCell { RemovableAppRow(pkg, label, "Quitar $label", { onChange(apps - pkg) }) }
                }
            }
        }
        FgGroup { FgActionRow(if (apps.isEmpty()) "Elegir apps" else "Cambiar las apps elegidas", onPick) }
    }
}

/** La barra de abajo con la acción principal de una pantalla, siempre a la vista. */
@Composable
internal fun BottomAction(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
