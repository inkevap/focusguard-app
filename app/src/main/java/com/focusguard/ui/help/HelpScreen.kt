package com.focusguard.ui.help

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.SubPageHost

/** Qué hace cada función, con un ejemplo y las excepciones, y un botón para ir a su pantalla. [topicId] abre directamente esa ficha. */
@Composable
fun HelpScreen(onBack: () -> Unit, onOpen: (route: String) -> Unit, topicId: String? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    var openId by rememberSaveable { mutableStateOf(topicId?.takeIf { HelpTopics.byId(it) != null }) }
    // Si se llegó desde otra pantalla a una ficha concreta, volver sale de la ayuda en vez de ir a la lista.
    var direct by rememberSaveable { mutableStateOf(openId != null) }
    val topics = remember(query) { HelpTopics.search(query) }
    val back = { if (direct) onBack() else openId = null }

    SubPageHost(
        page = openId,
        onBack = back,
        root = {
            FgScreen("Ayuda", onBack = onBack) {
                item {
                    FgGroup {
                        FgCell {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Buscar en la ayuda") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (topics.isEmpty()) {
                    item { FgNote("No hay nada con esas palabras. Prueba con otras más cortas, por ejemplo «horario» o «compañero».", Modifier.padding(horizontal = 16.dp)) }
                } else {
                    item {
                        FgGroup(footer = if (query.isBlank()) "${HelpTopics.all.size} temas en total." else null) {
                            topics.forEach { topic -> FgRow(topic.title, onClick = { direct = false; openId = topic.id }) }
                        }
                    }
                }
            }
        },
        sub = { id ->
            val topic = HelpTopics.byId(id)
            if (topic != null) TopicPage(topic, onBack = back, onOpen = onOpen)
        },
    )
}

@Composable
private fun TopicPage(topic: HelpTopic, onBack: () -> Unit, onOpen: (String) -> Unit) {
    FgScreen(topic.title, onBack = onBack) {
        item { FgGroup { FgCell { Text(topic.summary, style = MaterialTheme.typography.bodyLarge) } } }
        item { FgGroup(header = "Por ejemplo") { FgCell { Text(topic.example, style = MaterialTheme.typography.bodyMedium) } } }
        if (topic.notes.isNotEmpty()) {
            item {
                FgGroup(header = "Ten en cuenta") {
                    topic.notes.forEach { FgCell { Text(it, style = MaterialTheme.typography.bodyMedium) } }
                }
            }
        }
        topic.route?.let { route -> item { FgGroup { FgActionRow("Abrir", { onOpen(route) }) } } }
    }
}
