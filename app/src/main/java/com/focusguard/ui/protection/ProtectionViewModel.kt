package com.focusguard.ui.protection

import androidx.lifecycle.ViewModel
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.ApprovalRepository
import com.focusguard.data.ChangeGuard
import com.focusguard.data.GuardResult
import com.focusguard.data.SettingsRepository
import com.focusguard.data.StrictRepository
import com.focusguard.data.TrustedClock
import com.focusguard.decision.Approvals
import com.focusguard.decision.EmbeddedBrowserPolicy
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.decision.StrictMode
import com.focusguard.decision.UninstallGuard
import com.focusguard.decision.Weakening
import com.focusguard.model.ApprovalMode
import com.focusguard.model.StrictState
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProtectionViewModel(
    private val settings: SettingsRepository,
    approvals: ApprovalRepository,
    private val strict: StrictRepository,
    private val guard: ChangeGuard,
    private val clock: TrustedClock,
) : ViewModel() {

    val adultBlockEnabled: StateFlow<Boolean> = settings.adultBlockEnabled
    val newApps: StateFlow<NewAppsPolicy> = settings.newApps
    val embeddedBrowsers: StateFlow<EmbeddedBrowserPolicy> = settings.embeddedBrowsers
    val detectedWebViewApps: StateFlow<Set<String>> = settings.detectedWebViewApps
    val foldFloating: StateFlow<Boolean> = settings.foldFloating
    val approvalMode: StateFlow<ApprovalMode> = approvals.mode
    val pendingApprovals = approvals.pending

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun strictState(nowMs: Long): StrictState {
        val now = Instant.ofEpochMilli(nowMs)
        return StrictMode.state(strict.config.value, clock.localAt(now), now)
    }

    /** Encender es libre; apagar pasa por el modo estricto y las aprobaciones. */
    fun setAdultBlock(enabled: Boolean) {
        if (!enabled && blocked(ApprovalExecutor.KEY_ADULT_OFF, Approvals.ACTION_ADULT_BLOCK)) return
        settings.setAdultBlockEnabled(enabled)
    }

    /** Encender o alargar el plazo es libre; apagarlo o acortarlo pasa por el modo estricto y las aprobaciones. */
    fun setNewApps(policy: NewAppsPolicy) {
        if (Weakening.newAppsWeakened(settings.newApps.value, policy) &&
            blocked(ApprovalExecutor.KEY_NEW_APPS, Weakening.ACTION_NEW_APPS, policy.encode())
        ) return
        settings.setNewApps(policy)
    }

    /** Encender o ampliar es libre; apagar, quitar apps o pasar de todas a algunas pasa por el modo estricto y las aprobaciones. */
    fun setEmbeddedBrowsers(policy: EmbeddedBrowserPolicy) {
        if (Weakening.embeddedBrowsersWeakened(settings.embeddedBrowsers.value, policy) &&
            blocked(ApprovalExecutor.KEY_EMBEDDED, Weakening.ACTION_EMBEDDED_BROWSERS, policy.encode())
        ) return
        settings.setEmbeddedBrowsers(policy)
    }

    val adminOffGrantUntilMs: StateFlow<Long> = settings.adminOffGrantUntilMs

    /**
     * Pide poder desactivar el administrador del dispositivo. Con el modo estricto activo no se puede; con aprobaciones se crea la solicitud
     * y, al aprobarse, se abre una ventana de 10 minutos; sin ellas se abre al momento (un toque consciente, no una puerta cerrada).
     */
    fun requestAdminOff() {
        val result = guard.requestUninstall(ApprovalExecutor.KEY_ADMIN_OFF, Approvals.ACTION_ADMIN_OFF)
        if (result is GuardResult.Blocked) {
            _message.value = result.message
            return
        }
        settings.grantAdminOff(UninstallGuard.grantUntil(clock.nowMs()))
        _message.value = "Ya puedes desactivarlo en los ajustes de Android durante 10 minutos."
    }

    fun setFoldFloating(enabled: Boolean) {
        if (!enabled && blocked(ApprovalExecutor.KEY_FOLD_FLOATING, Weakening.ACTION_FOLD_FLOATING)) return
        settings.setFoldFloating(enabled)
    }

    /** true si el cambio no se hace ahora; deja el motivo en [message]. */
    private fun blocked(actionKey: String, actionId: Int, payload: String? = null): Boolean {
        val result = guard.check(actionKey, actionId, payload)
        if (result is GuardResult.Blocked) _message.value = result.message
        return result is GuardResult.Blocked
    }
}
