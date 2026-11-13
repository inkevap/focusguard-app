package com.focusguard.ui.settings

import androidx.compose.runtime.Composable
import com.focusguard.data.LauncherIcon
import com.focusguard.ui.components.FgRadioGroup
import com.focusguard.ui.profiles.GroupStack

/** El icono y el nombre de la app en el lanzador (H10). Solo cambia cómo se ve ahí: el bloqueo sigue igual. */
@Composable
internal fun AppIconContent(selected: LauncherIcon, onSelect: (LauncherIcon) -> Unit) {
    GroupStack {
        FgRadioGroup(
            options = LauncherIcon.entries.map { Triple(it, it.label, it.hint) },
            selected = selected,
            onSelect = onSelect,
            footer = "Cambia el icono y el nombre con que se ve FocusGuard en tu pantalla de inicio. El bloqueo sigue igual, pero en Ajustes del teléfono, " +
                "en las notificaciones y en el modo estricto seguirá apareciendo FocusGuard. Si tienes compañero, se lo contamos. " +
                "Algunos lanzadores tardan unos segundos en mostrarlo.",
        )
    }
}
