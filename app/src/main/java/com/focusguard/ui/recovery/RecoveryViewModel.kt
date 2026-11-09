package com.focusguard.ui.recovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.RecoveryStore
import com.focusguard.decision.StepRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecoveryViewModel(private val store: RecoveryStore) : ViewModel() {
    /** Lo hecho hasta ahora; `null` mientras se lee del disco. */
    val progress: StateFlow<Map<Int, StepRecord>?> = store.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    /** El día de hoy (lógico, desde 1970). La pantalla lo vuelve a pedir cada minuto: pasada la medianoche se abre el paso siguiente. */
    val today: Long get() = store.today().toEpochDay()

    fun complete(step: Int, answer: String) {
        viewModelScope.launch {
            if (!store.complete(step, answer)) message = "Ese paso no se pudo guardar. Mira cuál toca ahora e inténtalo de nuevo."
        }
    }

    fun restart() {
        viewModelScope.launch { store.restart() }
    }
}
