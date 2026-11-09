package com.focusguard.ui.dns

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.ChangeGuard
import com.focusguard.data.SettingsRepository
import com.focusguard.decision.Approvals
import com.focusguard.dns.DnsFilter
import com.focusguard.dns.DnsStatus
import kotlinx.coroutines.flow.StateFlow

class DnsViewModel(
    private val settings: SettingsRepository,
    private val filter: DnsFilter,
    private val guard: ChangeGuard,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.dnsFilterEnabled
    val status: StateFlow<DnsStatus> = filter.status
    val excluded: StateFlow<Set<String>> = settings.dnsExcludedApps

    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    /** El permiso de VPN que hay que pedir antes de encender, o null si ya está. */
    fun permissionIntent(): Intent? = filter.permissionIntent()

    /** Encender es libre (con el permiso de VPN ya concedido). */
    fun turnOn() = filter.setEnabled(true)

    /** Apagarlo deja todo el teléfono sin filtro: pasa por el modo estricto y las aprobaciones. */
    fun turnOff() {
        val blocked = guard.blockedMessage(ApprovalExecutor.KEY_DNS_OFF, Approvals.ACTION_DNS_OFF)
        if (blocked == null) filter.setEnabled(false) else message = blocked
    }

    /** Dejar una app fuera del filtro lo debilita: pasa por las aprobaciones (el modo estricto o la aprobación son los mismos para todas). */
    fun exclude(packages: Set<String>) {
        val added = packages - settings.dnsExcludedApps.value
        if (added.isEmpty()) return
        val first = added.min()
        val blocked = guard.blockedMessage(ApprovalExecutor.KEY_DNS_EXCLUDE, Approvals.ACTION_DNS_EXCLUDE, first)
        if (blocked != null) {
            // Una solicitud por acción: con varias apps a la vez solo se pide la primera.
            message = if (added.size > 1) "$blocked Solo se pide una app cada vez: elige las demás cuando esté aprobada." else blocked
            return
        }
        added.forEach(settings::addDnsExcludedApp)
        filter.restart()
    }

    /** Volver a filtrar una app es libre. */
    fun include(app: String) {
        settings.removeDnsExcludedApp(app)
        filter.restart()
    }
}
