package com.focusguard.ui.profiles

import com.focusguard.ui.icons.FgIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.focusguard.model.AppCategory
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.CheckRow
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SectionTitle

/** Bloquear por categoría (A12): todas las apps y webs conocidas de cada tipo, aunque se instalen o aparezcan después. */
@Composable
internal fun CategoriesCard(selected: Set<String>, onToggle: (id: String, blocked: Boolean) -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        SectionTitle("Por categoría", FgIcons.Apps)
        Hint("Al marcar una categoría se añaden a este perfil las apps que tienes instaladas y las webs de ese tipo: las verás en sus listas. La categoría sigue activa para las apps que instales más adelante; para dejar pasar alguna, desmarca la categoría y quítala de la lista. Las webs se reconocen en los navegadores compatibles, no en incógnito.")
        AppCategory.entries.forEach { category ->
            CheckRow(category.label, hint = null, checked = category.id in selected, onCheckedChange = { onToggle(category.id, it) })
        }
    }
}
