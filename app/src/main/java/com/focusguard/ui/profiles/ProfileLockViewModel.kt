package com.focusguard.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.ApprovalRepository
import com.focusguard.data.ChangeGuard
import com.focusguard.data.PartnerApprovals
import com.focusguard.data.ProfileLockRepository
import com.focusguard.data.StrictRepository
import com.focusguard.data.TrustedClock
import com.focusguard.decision.Approvals
import com.focusguard.decision.ProfileLocks
import com.focusguard.model.ExitContext
import com.focusguard.model.ExitMethod
import com.focusguard.model.ExitRequest
import com.focusguard.model.ExitStatus
import com.focusguard.model.ProfileLock
import com.focusguard.model.StrictAction
import com.focusguard.server.ApiResult
import com.focusguard.service.DeviceContextProvider
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Pone y quita el candado de un perfil. No depende del modo estricto; solo comparte con él el PIN. */
class ProfileLockViewModel(
    private val repository: ProfileLockRepository,
    private val strict: StrictRepository,
    private val deviceContext: DeviceContextProvider,
    private val clock: TrustedClock,
    private val guard: ChangeGuard,
    private val approvals: ApprovalRepository,
    private val partnerApprovals: PartnerApprovals,
) : ViewModel() {

    val locks: StateFlow<Map<Long, ProfileLock>> = repository.locks
    val requests: StateFlow<Map<Long, ExitRequest>> = repository.requests
    val hasPin: Boolean get() = strict.hasPin

    /** Pone el candado. Devuelve un mensaje de error, o null si quedó puesto. */
    fun lock(profileId: Long, methods: List<ExitMethod>, newPin: String): String? {
        ProfileLocks.invalidReason(methods)?.let { return it }
        if (ExitMethod.Pin in methods && !strict.hasPin) {
            if (!StrictRepository.isValidPin(newPin)) return "Define un PIN de 4 a 8 dígitos."
            strict.setPin(newPin)
        }
        if (ExitMethod.Partner in methods && !guard.partnerLinked) return "Para que tu compañero lo apruebe, primero vincúlalo en Protección."
        repository.lock(profileId, ProfileLock(methods))
        return null
    }

    /** Pide quitar el candado. Con el compañero entre los métodos, también le llega el correo para aprobarlo. */
    fun requestUnlock(profileId: Long) {
        repository.saveRequest(profileId, ExitRequest(StrictAction.Disable, clock.now()))
        if (!repository.needsPartner(profileId) || !guard.partnerLinked) return
        guard.requestStrictExit(ApprovalExecutor.profileUnlock(profileId), Approvals.ACTION_PROFILE_UNLOCK)
        viewModelScope.launch { partnerApprovals.sync() }
    }

    /** Cancela lo pedido y retira del servidor la solicitud al compañero, para que el enlace de su correo deje de valer. */
    fun cancelRequest(profileId: Long) {
        repository.saveRequest(profileId, null)
        val pending = guard.pending(ApprovalExecutor.profileUnlock(profileId)) ?: return
        viewModelScope.launch {
            val result = partnerApprovals.cancel(pending)
            if (result is ApiResult.Ok || (result is ApiResult.Failure && !result.error.retryable)) approvals.remove(pending.id)
        }
    }

    fun status(profileId: Long, enteredPin: String, now: Instant = clock.now()): ExitStatus? {
        val lock = repository.locks.value[profileId] ?: return null
        val request = repository.requests.value[profileId] ?: return null
        val pinCorrect = if (enteredPin.isBlank()) null else strict.verifyPin(enteredPin)
        return ProfileLocks.evaluateExit(lock, request, ExitContext(deviceContext.isCharging(), pinCorrect, guard.partnerLinked), clock.localAt(now), now)
    }

    /** Quita el candado si todos los métodos están cumplidos. */
    fun confirmUnlock(profileId: Long, enteredPin: String): Boolean {
        if (status(profileId, enteredPin)?.satisfied != true) return false
        repository.unlock(profileId)
        guard.pending(ApprovalExecutor.profileUnlock(profileId))?.let { approvals.remove(it.id) }
        return true
    }
}
