package com.focusguard.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.decision.TimeRanges
import com.focusguard.model.BlockMode
import com.focusguard.model.Profile
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SectionLabel
import com.focusguard.ui.components.SectionTitle
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
    Scaffold(topBar = { FgTopBar("Plantillas de perfil", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Hint("Elige una y se crea un perfil con sus apps, webs, horario y límite. Después lo cambias como quieras: nada se bloquea hasta que lo creas.") }
            if (!lists.loaded) item { Hint("Mirando qué apps tienes instaladas…") }
            if (lists.personal.isNotEmpty()) {
                item { SectionLabel("Para ti") }
                items(lists.personal, key = { "p" + it.template.id }) { TemplateCard(it, onCreate) }
            }
            if (lists.generic.isNotEmpty()) {
                item { SectionLabel("Plantillas") }
                items(lists.generic, key = { "g" + it.template.id }) { TemplateCard(it, onCreate) }
            }
        }
    }
}

@Composable
private fun TemplateCard(item: TemplateItem, onCreate: (TemplateItem) -> Unit) {
    val template = item.template
    AppCard {
        SectionTitle("${template.emoji} ${template.title}".trim())
        Hint(template.description)
        Hint(summary(item.preview))
        PrimaryButton("Crear perfil", { onCreate(item) }, Modifier.padding(top = 12.dp))
    }
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
