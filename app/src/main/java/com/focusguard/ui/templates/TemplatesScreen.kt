package com.focusguard.ui.templates

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.decision.TimeRanges
import com.focusguard.model.BlockMode
import com.focusguard.model.Profile
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.plural

/** Perfiles listos para crear con un toque: los personales («Para ti», si el APK los trae) y los genéricos. Después se editan como cualquiera. */
@Composable
fun TemplatesScreen(viewModel: TemplatesViewModel, onBack: () -> Unit, onCreated: (profileId: Long) -> Unit) {
    val lists = viewModel.lists
    TemplatesContent(lists, onBack) { viewModel.create(it, onCreated) }
}

@Composable
internal fun TemplatesContent(lists: TemplateLists, onBack: () -> Unit, onCreate: (TemplateItem) -> Unit) {
    FgScreen("Plantillas de perfil", onBack = onBack) {
        item {
            FgNote(
                "Elige una y se crea un perfil con sus apps, webs, horario y límite. Después lo cambias como quieras: nada se bloquea hasta que lo creas.",
                Modifier.padding(horizontal = 16.dp),
            )
        }
        if (!lists.loaded) item { FgNote("Mirando qué apps tienes instaladas…", Modifier.padding(horizontal = 16.dp)) }
        if (lists.personal.isNotEmpty()) {
            item { FgGroup(header = "Para ti") { lists.personal.forEach { TemplateRow(it, onCreate) } } }
        }
        if (lists.generic.isNotEmpty()) {
            item { FgGroup(header = "Plantillas") { lists.generic.forEach { TemplateRow(it, onCreate) } } }
        }
    }
}

/** Tocar una plantilla crea el perfil y lo abre para editarlo. */
@Composable
private fun TemplateRow(item: TemplateItem, onCreate: (TemplateItem) -> Unit) {
    val template = item.template
    FgRow(
        "${template.emoji} ${template.title}".trim(),
        subtitle = "${template.description}\n${summary(item.preview)}",
        onClick = { onCreate(item) },
    )
}

/** «4 apps instaladas · 14 webs · 45 min al día · De 22:00 a 07:00»: lo que el perfil tendrá en este teléfono. */
internal fun summary(profile: Profile): String {
    val parts = buildList {
        val allow = profile.mode == BlockMode.ALLOW_ONLY
        add(if (allow) "Solo permite ${plural(profile.apps.size, "app", "apps")}" else plural(profile.apps.size, "app instalada", "apps instaladas"))
        if (profile.webs.isNotEmpty()) add(plural(profile.webs.size, "web", "webs"))
        profile.limit?.let { add("${formatAmountOfTime(it.amount.toLong())} al día") }
        profile.intervals.firstOrNull()?.let { add(TimeRanges.describe(it.startMinute, it.endMinute)) }
    }
    return parts.joinToString(" · ")
}
