package com.focusguard.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.HabitsStore
import com.focusguard.data.JournalEntryEntity
import com.focusguard.data.MoodEntity
import com.focusguard.data.JournalStore
import com.focusguard.data.PlannedActivityEntity
import com.focusguard.decision.Journal
import com.focusguard.decision.Mood
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Diario y planificador (L4). Lo escrito se queda en el teléfono. */
class JournalViewModel(private val store: JournalStore, private val habits: HabitsStore) : ViewModel() {

    val entries: StateFlow<List<JournalEntryEntity>> = store.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** De hoy en adelante; la pantalla se queda con hoy y mañana. */
    val activities: StateFlow<List<PlannedActivityEntity>> = store.activities(store.today()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cómo te has sentido la última semana, del más reciente al más antiguo (K5). */
    val moods: StateFlow<List<MoodEntity>> = habits.moodsSince(store.today().minusDays(6)).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    fun today(): LocalDate = store.today()

    fun logMood(mood: Mood) {
        viewModelScope.launch {
            habits.logMood(mood)
            message = "Apuntado."
        }
    }

    fun saveToday(text: String) {
        viewModelScope.launch { message = if (store.saveToday(text)) "Guardado." else "Escribe algo antes de guardar." }
    }

    fun plan(day: LocalDate, text: String) {
        viewModelScope.launch {
            message = if (store.plan(day, text)) "Planificado. Ya no se puede cambiar." else "No se pudo: como mucho ${Journal.MAX_ACTIVITIES_PER_DAY} actividades por día."
        }
    }

    fun complete(activity: PlannedActivityEntity) {
        viewModelScope.launch {
            if (!store.complete(activity.id, LocalDate.ofEpochDay(activity.epochDay))) message = "Solo se marca como hecha el mismo día."
        }
    }
}
