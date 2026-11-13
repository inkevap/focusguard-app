package com.focusguard.ui.profiles

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.focusguard.model.AppCategory
import com.focusguard.ui.components.FgCheckRow
import com.focusguard.ui.components.FgGroup

/** Bloquear por categoría (A12): todas las apps y webs conocidas de cada tipo, aunque se instalen o aparezcan después. */
@Composable
internal fun CategoriesCard(selected: Set<String>, onToggle: (id: String, blocked: Boolean) -> Unit, modifier: Modifier = Modifier) {
    GroupStack(modifier) {
        FgGroup(
            footer = "Al marcar una categoría se añaden a este perfil las apps que tienes instaladas y las webs de ese tipo: las verás en sus listas. La categoría sigue activa para las apps que instales más adelante; para dejar pasar alguna, desmarca la categoría y quítala de la lista. Las webs se reconocen en los navegadores compatibles, no en incógnito.",
        ) {
            AppCategory.entries.forEach { category ->
                FgCheckRow(category.label, category.id in selected, { onToggle(category.id, it) })
            }
        }
    }
}
