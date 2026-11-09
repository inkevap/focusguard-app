package com.focusguard.ui.approvals

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ApprovalApplier
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.AppliedReport
import com.focusguard.data.ApprovalRepository
import com.focusguard.data.ChangeGuard
import com.focusguard.data.PartnerApprovals
import com.focusguard.data.PartnerRepository
import com.focusguard.data.PartnerState
import com.focusguard.server.ApiResult
import com.focusguard.data.TrustedClock
import com.focusguard.decision.Approvals
import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalProgress
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.ApprovalStatus
import com.focusguard.service.StepCounter
import java.time.Instant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ApprovalsViewModel(
    private val repository: ApprovalRepository,
    private val executor: ApprovalExecutor,
    private val stepCounter: StepCounter,
    private val clock: TrustedClock,
    private val partnerApprovals: PartnerApprovals,
    private val partner: PartnerRepository,
    private val guard: ChangeGuard,
    private val applier: ApprovalApplier,
) : ViewModel() {

    init {
        // Para saber si el compañero ya aceptó (la elección «compañero» solo se ofrece con uno activo).
        viewModelScope.launch { partner.refresh() }
    }

    val mode: StateFlow<ApprovalMode> = repository.mode
    val partnerState: StateFlow<PartnerState> = partner.state
    val pending: StateFlow<List<ApprovalRequest>> = repository.pending

    /** Pasos recorridos por solicitud, actualizados por [refreshSteps]. */
    var steps by mutableStateOf(emptyMap<Long, Int>())
        private set

    var message by mutableStateOf<String?>(null)
        private set

    val stepsAvailable: Boolean get() = stepCounter.isAvailable

    fun status(request: ApprovalRequest, typed: String, now: Instant): ApprovalStatus =
        Approvals.evaluate(request, ApprovalProgress(typed, steps[request.id] ?: 0), now)

    /** Lee el contador y actualiza el avance de las solicitudes por pasos. */
    fun refreshSteps() {
        viewModelScope.launch {
            val total = stepCounter.currentTotal() ?: return@launch
            val updated = steps.toMutableMap()
            repository.pending.value.filter { it.mode == ApprovalMode.STEPS }.forEach { request ->
                val baseline = request.stepsBaseline
                if (baseline == null) {
                    repository.setBaseline(request.id, total)
                    updated[request.id] = 0
                } else {
                    updated[request.id] = Approvals.stepsSince(baseline, total)
                }
            }
            steps = updated
        }
    }

    private var applying = false

    fun apply(request: ApprovalRequest, typed: String) {
        if (applying || status(request, typed, clock.now()) != ApprovalStatus.Ready) return
        applying = true
        viewModelScope.launch {
            try {
                applyNow(request)
            } finally {
                applying = false
            }
        }
    }

    private suspend fun applyNow(request: ApprovalRequest) {
        // Lo del compañero pasa por la misma pasada que usan los avisos, para que no se aplique dos veces: gasta la aprobación en el servidor
        // y solo entonces aplica; sin conexión no se aplica.
        if (request.mode == ApprovalMode.PARTNER) {
            executor.blockedReason(request)?.let {
                message = it
                return
            }
            reportApplied(applier.applyApproved())
            return
        }
        message = executor.apply(request) ?: "Aplicado."
    }

    private fun reportApplied(report: AppliedReport) {
        message = when {
            report.failed.isNotEmpty() -> report.failed.first()
            report.applied.isNotEmpty() -> "Aplicado: ${report.applied.joinToString("; ")}."
            else -> message
        }
    }

    /**
     * Una solicitud al compañero solo se da por cancelada cuando el servidor lo confirma: antes se quitaba de la lista aunque no hubiera
     * conexión, y el enlace del correo seguía valiendo. Sin conexión se queda y se avisa.
     */
    fun cancel(request: ApprovalRequest) {
        if (request.mode != ApprovalMode.PARTNER) {
            repository.remove(request.id)
            return
        }
        viewModelScope.launch {
            val result = partnerApprovals.cancel(request)
            if (result is ApiResult.Failure && result.error.retryable) {
                message = "Sin conexión: no se pudo cancelar. Tu compañero aún podría aprobarla; inténtalo de nuevo con conexión."
            } else {
                // Cancelada, o el servidor ya no la tiene (resuelta o caducada): se quita de la lista.
                repository.remove(request.id)
                message = "Solicitud cancelada. Tu compañero ya no puede aprobarla."
            }
        }
    }

    /** Envía las solicitudes pendientes al compañero y consulta cómo van. Se llama al abrir la pantalla y cada pocos segundos. */
    fun syncPartner() {
        viewModelScope.launch {
            val result = partnerApprovals.sync()
            if (result is ApiResult.Failure) message = result.error.message
            // Con la pantalla abierta lo aprobado se aplica en cuanto llega, sin botón.
            reportApplied(applier.applyApproved())
        }
    }

    /** Una solicitud rechazada o caducada se pide de nuevo como solicitud nueva (sustituye a la anterior). */
    fun askAgain(request: ApprovalRequest) {
        // Una solicitud que tenía espera previa (desinstalar) la conserva al pedirla otra vez.
        repository.create(request.actionKey, request.actionId, request.payload, ApprovalMode.PARTNER, holdByMode = request.sendAfter != null)
        syncPartner()
    }

    fun clearMessage() { message = null }

    /** Endurecer el modo es libre; cambiarlo cuando ya hay uno elegido pasa por una aprobación. */
    fun changeMode(newMode: ApprovalMode) {
        val current = repository.mode.value
        if (newMode == current) return
        if (newMode == ApprovalMode.PARTNER && partner.state.value !is PartnerState.Active) {
            message = "Primero invita a un compañero y espera a que acepte (Cuenta)."
            return
        }
        if (current == ApprovalMode.NONE) {
            repository.setMode(newMode)
        } else {
            // Con el modo parental, cambiar cómo se aprueba también lo aprueba el compañero.
            val viaPartner = guard.parentalActive
            repository.create(ApprovalExecutor.KEY_APPROVAL_MODE, Approvals.ACTION_APPROVAL_MODE, newMode.name, if (viaPartner) ApprovalMode.PARTNER else null)
            if (viaPartner) syncPartner()
            message = "Solicitud creada: cambiar el modo de aprobación también necesita aprobación."
        }
    }
}
