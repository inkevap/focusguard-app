package com.focusguard.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle

/** Qué hace cada función, con un ejemplo y las excepciones, y un botón para ir a su pantalla. [topicId] abre con esa ficha buscada. */
@Composable
fun HelpScreen(onBack: () -> Unit, onOpen: (route: String) -> Unit, topicId: String? = null) {
    var query by rememberSaveable { mutableStateOf(topicId?.let(HelpTopics::byId)?.title.orEmpty()) }
    val topics = remember(query) { HelpTopics.search(query) }

    Scaffold(topBar = { FgTopBar("Ayuda", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar en la ayuda") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (topics.isEmpty()) item { Hint("No hay nada con esas palabras. Prueba con otras más cortas, por ejemplo «horario» o «compañero».") }
            items(topics, key = { it.id }) { topic -> TopicCard(topic, onOpen) }
        }
    }
}

@Composable
private fun TopicCard(topic: HelpTopic, onOpen: (String) -> Unit) {
    AppCard {
        SectionTitle(topic.title)
        Text(topic.summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        Text("Por ejemplo", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        Text(topic.example, style = MaterialTheme.typography.bodyMedium)
        if (topic.notes.isNotEmpty()) {
            Text("Ten en cuenta", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
            topic.notes.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp)) }
        }
        topic.route?.let { SecondaryButton("Abrir", { onOpen(it) }, Modifier.padding(top = 12.dp)) }
    }
}
