package com.focusguard.ui.urge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.Urge
import com.focusguard.data.UrgeLog
import com.focusguard.decision.UrgeOutcome
import com.focusguard.decision.UrgeTrigger
import kotlinx.coroutines.launch

/** En qué punto está la pantalla del modo impulsos. */
sealed interface UrgeStage {
    data object Loading : UrgeStage

    data object Intro : UrgeStage

    data class Running(val urge: Urge) : UrgeStage

    /** Ya contestó: se puede apuntar qué lo desencadenó. */
    data class Finished(val urgeId: Long, val outcome: UrgeOutcome, val trigger: UrgeTrigger? = null) : UrgeStage
}

/** Modo impulsos (L3). El impulso en curso se guarda: si se cierra la app, la cuenta atrás sigue donde iba. */
class UrgeViewModel(private val log: UrgeLog) : ViewModel() {

    var stage by mutableStateOf<UrgeStage>(UrgeStage.Loading)
        private set

    init {
        viewModelScope.launch { stage = log.current()?.let { UrgeStage.Running(it) } ?: UrgeStage.Intro }
    }

    fun start() {
        viewModelScope.launch { stage = UrgeStage.Running(log.start()) }
    }

    fun finish(outcome: UrgeOutcome) {
        val running = stage as? UrgeStage.Running ?: return
        viewModelScope.launch {
            log.finish(running.urge.id, outcome)
            stage = UrgeStage.Finished(running.urge.id, outcome)
        }
    }

    fun setTrigger(trigger: UrgeTrigger) {
        val finished = stage as? UrgeStage.Finished ?: return
        // Tocar el mismo motivo otra vez lo quita.
        val chosen = trigger.takeIf { it != finished.trigger }
        viewModelScope.launch {
            log.setTrigger(finished.urgeId, chosen)
            stage = finished.copy(trigger = chosen)
        }
    }
}
