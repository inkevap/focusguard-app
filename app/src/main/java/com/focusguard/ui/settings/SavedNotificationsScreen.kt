package com.focusguard.ui.settings

import com.focusguard.ui.icons.FgIcons
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.focusguard.appLabel
import com.focusguard.data.NotificationLog
import com.focusguard.ui.components.AppIcon
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.formatLocal
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Una notificación guardada, lista para pintar. */
internal data class SavedNotification(val id: Long, val app: String, val at: Instant, val title: String, val text: String, val packageName: String? = null)

class SavedNotificationsViewModel(private val log: NotificationLog, private val context: Context) : ViewModel() {

    /** Las etiquetas de las apps se resuelven una vez por app en cada cambio de la lista, y fuera del hilo principal. */
    internal val items: StateFlow<List<SavedNotification>> = log.all
        .map { rows ->
            val labels = rows.map { it.packageName }.distinct().associateWith(context::appLabel)
            rows.map { SavedNotification(it.id, labels.getValue(it.packageName), Instant.ofEpochMilli(it.atMs), it.title, it.text, it.packageName) }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clear() { viewModelScope.launch { log.clear() } }
}

@Composable
fun SavedNotificationsScreen(viewModel: SavedNotificationsViewModel, onBack: () -> Unit) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    SavedNotificationsContent(items, viewModel::clear, onBack = onBack)
}

@Composable
internal fun SavedNotificationsContent(items: List<SavedNotification>, onClear: () -> Unit, modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    FgScreen("Notificaciones guardadas", modifier = modifier, onBack = onBack) {
        item { FgNote("Son las notificaciones que llegaron mientras la app estaba bloqueada. Se guardan 7 días en este teléfono.", Modifier.padding(horizontal = 16.dp)) }
        if (items.isEmpty()) {
            item { FgNote("No hay ninguna guardada.", Modifier.padding(horizontal = 16.dp)) }
        } else {
            item { FgGroup { FgActionRow("Borrar todas", onClear, danger = true) } }
        }
        items(items, key = { it.id }) { n ->
            FgGroup {
                FgCell {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIcon(n.packageName, size = 24.dp)
                        Text("${n.app} · ${formatLocal(n.at, withDay = true)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (n.title.isNotBlank()) Text(n.title, style = MaterialTheme.typography.titleMedium)
                    if (n.text.isNotBlank()) Text(n.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
