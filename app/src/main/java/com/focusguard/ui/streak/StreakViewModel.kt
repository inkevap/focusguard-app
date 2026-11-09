package com.focusguard.ui.streak

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.ChangeGuard
import com.focusguard.data.GuardResult
import com.focusguard.data.StreakStore
import com.focusguard.decision.Approvals
import com.focusguard.decision.DayRecord
import com.focusguard.decision.StreakConfig
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Rachas (L1, L2). Restaurar un día perdido pasa por el guardián de cambios; la meta y el reinicio no debilitan nada y son libres. */
class StreakViewModel(private val store: StreakStore, private val guard: ChangeGuard) : ViewModel() {

    val records: StateFlow<Map<LocalDate, DayRecord>> = store.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val config: StateFlow<StreakConfig> = store.config

    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    /** El día lógico de ahora (con el inicio del día de los ajustes), con la hora de confianza. */
    fun today(): LocalDate = store.today()

    fun setGoal(days: Int) = store.setGoal(days)

    fun reset() {
        store.reset()
        message = "Racha reiniciada. Tu mejor racha se conserva."
    }

    fun restore(day: LocalDate) {
        val result = guard.check(ApprovalExecutor.streakRestore(day), Approvals.ACTION_STREAK_RESTORE)
        if (result is GuardResult.Blocked) {
            message = result.message
            return
        }
        viewModelScope.launch {
            message = if (store.restore(day)) "Día restaurado." else StreakStore.CANNOT_RESTORE
        }
    }
}
