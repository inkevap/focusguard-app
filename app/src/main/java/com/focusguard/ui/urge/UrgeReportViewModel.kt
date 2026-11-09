package com.focusguard.ui.urge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.ChangeGuard
import com.focusguard.data.PartnerApprovals
import com.focusguard.data.PartnerRepository
import com.focusguard.data.SettingsRepository
import com.focusguard.data.UrgeLog
import com.focusguard.decision.Approvals
import com.focusguard.decision.UrgeReport
import com.focusguard.decision.UrgeReports
import com.focusguard.ui.askPartner
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UrgeReportViewModel(
    private val log: UrgeLog,
    private val settings: SettingsRepository,
    private val partner: PartnerRepository,
    private val guard: ChangeGuard,
    private val partnerApprovals: PartnerApprovals,
) : ViewModel() {
    private val zone = ZoneId.systemDefault()

    /** El informe de las últimas semanas; `null` mientras se lee del disco. El día se vuelve a mirar cada hora: pasada la medianoche la ventana avanza. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val report: StateFlow<UrgeReport?> = flow {
        while (true) {
            emit(log.calendarToday(zone))
            delay(HOUR_MS)
        }
    }.distinctUntilChanged().flatMapLatest { today ->
        log.finishedSince(UrgeReports.sinceDay(today).atStartOfDay(zone).toInstant().toEpochMilli()).map { rows -> UrgeReports.build(rows, today, zone) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sharing: StateFlow<Boolean> = settings.shareUrgeReport

    /** Hay un compañero activo y los avisos a él están encendidos: solo entonces se le puede enseñar el resumen. */
    val canShare: Boolean get() = partner.isActive && settings.partnerNotices.value

    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    /** Encenderlo es libre (se ve más) pero necesita un compañero activo; apagarlo con compañero lo tiene que aprobar él. */
    fun setSharing(enabled: Boolean) {
        when {
            enabled && !canShare -> message = "Necesitas un compañero activo, con los avisos encendidos, para enseñarle el resumen."
            enabled -> {
                settings.setShareUrgeReport(true)
                message = "Tu compañero recibirá cada semana un resumen: cuántos impulsos, cuántos pasaron, la hora más frecuente y lo que más los desencadena."
            }
            !partner.isActive -> settings.setShareUrgeReport(false)
            // Si el compañero dejó de estar activo justo ahora ya no hay a quién pedírselo.
            else -> askPartner(guard, partnerApprovals, ApprovalExecutor.KEY_URGE_SHARING_OFF, Approvals.ACTION_URGE_SHARING_OFF)?.let { message = it } ?: settings.setShareUrgeReport(false)
        }
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
    }
}
