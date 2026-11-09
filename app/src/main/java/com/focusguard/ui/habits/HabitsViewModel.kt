package com.focusguard.ui.habits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.HabitsStore
import com.focusguard.data.SettingsRepository
import com.focusguard.decision.MoodPromptSettings
import com.focusguard.decision.NudgeSettings
import com.focusguard.decision.Reminder
import com.focusguard.decision.Reminders
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Aviso de tiempo (K7), recordatorios propios (J4) y pregunta de ánimo (K5). Todos son avisos: encenderlos o apagarlos es libre. */
class HabitsViewModel(private val settings: SettingsRepository, private val store: HabitsStore) : ViewModel() {

    val nudge: StateFlow<NudgeSettings> = settings.nudge
    val moodPrompt: StateFlow<MoodPromptSettings> = settings.moodPrompt
    val reminders: StateFlow<List<Reminder>> = store.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    fun setNudge(value: NudgeSettings) = settings.setNudge(value)

    fun setMoodPrompt(value: MoodPromptSettings) = settings.setMoodPrompt(value)

    /** [onAdded] se llama solo si se añadió: la pantalla no vacía lo escrito si no. */
    fun addReminder(minuteOfDay: Int, text: String, onAdded: () -> Unit) {
        viewModelScope.launch {
            if (store.addReminder(minuteOfDay, text)) {
                message = "Recordatorio añadido."
                onAdded()
            } else {
                message = "No se pudo: como mucho ${Reminders.MAX} recordatorios, con hora y texto."
            }
        }
    }

    fun setReminderEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { store.setReminderEnabled(id, enabled) }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch { store.deleteReminder(id) }
    }
}
