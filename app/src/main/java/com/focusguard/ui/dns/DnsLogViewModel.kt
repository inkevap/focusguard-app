package com.focusguard.ui.dns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.DnsDay
import com.focusguard.data.DnsQueryEntity
import com.focusguard.data.DnsQueryLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DnsLogViewModel(private val log: DnsQueryLog) : ViewModel() {
    val today: Long get() = log.today()

    /** Los días con consultas, el más reciente primero. */
    val days: StateFlow<List<DnsDay>> = log.days.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val chosenDay = MutableStateFlow<Long?>(null)
    private val _showBlocked = MutableStateFlow(true)
    val showBlocked: StateFlow<Boolean> = _showBlocked

    /** El día elegido, o el más reciente con consultas. */
    val selectedDay: StateFlow<Long?> = combine(chosenDay, days) { chosen, days -> chosen?.takeIf { c -> days.any { it.day == c } } ?: days.firstOrNull()?.day }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hosts: StateFlow<List<DnsQueryEntity>> = combine(selectedDay, showBlocked, ::Pair)
        .flatMapLatest { (day, blocked) -> if (day == null) kotlinx.coroutines.flow.flowOf(emptyList()) else log.hosts(day, blocked) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDay(day: Long) {
        chosenDay.value = day
    }

    fun showBlocked(blocked: Boolean) {
        _showBlocked.value = blocked
    }

    /** Borra el historial de este teléfono; el compañero solo recibía los totales de cada día. */
    fun clear() {
        viewModelScope.launch { log.clear() }
    }
}
