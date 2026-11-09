package com.focusguard.ui.settings

import com.focusguard.ui.icons.FgIcons
import androidx.compose.runtime.Composable
import com.focusguard.data.LauncherIcon
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.RadioGroup
import com.focusguard.ui.components.SectionTitle

/** El icono y el nombre de la app en el lanzador (H10). Solo cambia cómo se ve ahí: el bloqueo sigue igual. */
@Composable
internal fun AppIconContent(selected: LauncherIcon, onSelect: (LauncherIcon) -> Unit) {
    AppCard {
        SectionTitle("Icono de la app", FgIcons.Apps)
        Hint(
            "Cambia el icono y el nombre con que se ve FocusGuard en tu pantalla de inicio. El bloqueo sigue igual, pero en Ajustes del teléfono, " +
                "en las notificaciones y en el modo estricto seguirá apareciendo FocusGuard. Si tienes compañero, se lo contamos. " +
                "Algunos lanzadores tardan unos segundos en mostrarlo.",
        )
        RadioGroup(options = LauncherIcon.entries.map { Triple(it, it.label, it.hint) }, selected = selected, onSelect = onSelect)
    }
}
