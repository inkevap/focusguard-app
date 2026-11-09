package com.focusguard.ui.strict

import com.focusguard.service.DeviceContextProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.ApprovalRepository
import com.focusguard.data.ChangeGuard
import com.focusguard.data.PartnerApprovals
import com.focusguard.data.StrictRepository
import com.focusguard.data.TrustedClock
import com.focusguard.decision.Approvals
import com.focusguard.decision.StrictMode
import com.focusguard.model.ExitContext
import com.focusguard.model.ExitMethod
import com.focusguard.model.ExitRequest
import com.focusguard.model.ExitStatus
import com.focusguard.model.StrictAction
import com.focusguard.model.StrictConfig
import com.focusguard.model.StrictState
import com.focusguard.server.ApiResult
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StrictViewModel(
    private val repository: StrictRepository,
    private val deviceContext: DeviceContextProvider,
    private val clock: TrustedClock,
    private val guard: ChangeGuard,
    private val approvals: ApprovalRepository,
    private val partnerApprovals: PartnerApprovals,
) : ViewModel() {

    val config: StateFlow<StrictConfig> = repository.config
    val request: StateFlow<ExitRequest?> = repository.request
    val hasPin: Boolean get() = repository.hasPin

    fun state(now: Instant = clock.now()): StrictState = StrictMode.state(config.value, clock.localAt(now), now)

    fun canEditConfig(now: Instant = clock.now()) = StrictMode.canEditConfig(config.value, clock.localAt(now), now)

    /** Guarda la configuración (solo si no está activo). Devuelve un mensaje de error o null si se guardó. */
    fun save(newConfig: StrictConfig, newPin: String, currentPin: String): String? {
        if (!canEditConfig()) return "El modo estricto está activo: pide una pausa o desactivarlo."
        // Relajar lo ya activado (fuera del horario o en pausa) también pasa por la solicitud de salida.
        if (StrictMode.isWeakening(config.value, newConfig)) {
            return "Eso relaja el modo estricto. Pide «Desactivar» y cumple tus métodos de salida; luego podrás cambiarlo."
        }
        if (newPin.isNotBlank() && repository.hasPin && !repository.verifyPin(currentPin)) {
            return "Para cambiar el PIN hace falta el PIN actual."
        }
        if (newConfig.enabled) {
            if (newConfig.exitMethods.isEmpty()) return "Elige al menos un método de salida."
            val needsPin = newConfig.exitMethods.any { it is ExitMethod.Pin }
            if (needsPin && newPin.isBlank() && !repository.hasPin) return "Define un PIN de 4 a 8 dígitos."
            if (ExitMethod.FollowSchedule in newConfig.exitMethods && newConfig.intervals.isEmpty()) {
                return "\"Seguir horarios\" necesita al menos un intervalo de horario."
            }
            // Sin compañero nadie podría aprobar la salida: el modo estricto se quedaría sin forma de pausarse o desactivarse.
            if (ExitMethod.Partner in newConfig.exitMethods && ExitMethod.Partner !in config.value.exitMethods && !guard.partnerLinked) {
                return "Para que tu compañero apruebe la salida, primero vincúlalo en Protección."
            }
        }
        if (newPin.isNotBlank() && !repository.setPin(newPin)) return "El PIN debe tener de 4 a 8 dígitos."
        repository.saveConfig(newConfig)
        return null
    }

    /** Pide la salida. Con el compañero entre los métodos, también le pide que la apruebe (le llega un correo). */
    fun requestExit(action: StrictAction) {
        repository.saveRequest(ExitRequest(action, clock.now()))
        if (ExitMethod.Partner !in config.value.exitMethods || !guard.partnerLinked) return
        guard.requestStrictExit(ApprovalExecutor.strictExit(action), Approvals.ACTION_STRICT_EXIT)
        viewModelScope.launch { partnerApprovals.sync() }
    }

    /**
     * Cancela la salida pedida y, si esperaba al compañero, retira también esa solicitud para que el enlace de su correo deje de valer
     * (si no hay conexión se queda: aprobarla después no cambia nada, la salida ya no existe).
     */
    fun cancelRequest() {
        val action = request.value?.action
        repository.saveRequest(null)
        val pending = action?.let { guard.pending(ApprovalExecutor.strictExit(it)) } ?: return
        viewModelScope.launch {
            val result = partnerApprovals.cancel(pending)
            if (result is ApiResult.Ok || (result is ApiResult.Failure && !result.error.retryable)) approvals.remove(pending.id)
        }
    }

    /** Reanudar es endurecer, así que no pide nada. */
    fun resume() = repository.saveConfig(config.value.copy(pausedUntil = null))

    fun status(enteredPin: String, now: Instant = clock.now()): ExitStatus? {
        val req = request.value ?: return null
        val pinCorrect = if (enteredPin.isBlank()) null else repository.verifyPin(enteredPin)
        return StrictMode.evaluateExit(config.value, req, ExitContext(deviceContext.isCharging(), pinCorrect, guard.partnerLinked), clock.localAt(now), now)
    }

    /** Aplica la solicitud si todo está cumplido. */
    fun confirm(enteredPin: String): Boolean {
        val req = request.value ?: return false
        if (status(enteredPin)?.satisfied != true) return false
        repository.saveConfig(StrictMode.apply(config.value, req.action, clock.now()))
        repository.saveRequest(null)
        // Si la aprobación del compañero ya se gastó la solicitud ya no existe; si no, no queda colgada.
        guard.pending(ApprovalExecutor.strictExit(req.action))?.let { approvals.remove(it.id) }
        return true
    }
}
