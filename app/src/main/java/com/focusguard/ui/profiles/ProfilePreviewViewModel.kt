package com.focusguard.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.ChangeGuard
import com.focusguard.data.GuardResult
import com.focusguard.data.ProfilePauses
import com.focusguard.decision.Weakening
import com.focusguard.ui.formatAmountOfTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.focusguard.data.ProfileRepository
import com.focusguard.data.SettingsRepository
import com.focusguard.data.TrustedClock
import com.focusguard.data.UsageRepository
import com.focusguard.decision.Conditions
import com.focusguard.decision.ProfilePreview
import com.focusguard.model.DeviceContext
import com.focusguard.service.DeviceContextProvider
import com.focusguard.model.Profile
import com.focusguard.model.ProfileUsage
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfilePreviewViewModel(
    private val repository: ProfileRepository,
    private val usageRepository: UsageRepository,
    private val settings: SettingsRepository,
    private val clock: TrustedClock,
    private val guard: ChangeGuard,
    private val deviceContext: DeviceContextProvider,
    private val pauses: ProfilePauses,
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    internal fun pauseHistory(id: Long): Flow<List<PauseRecord>> =
        pauses.history(id).map { rows -> rows.map { PauseRecord(Instant.ofEpochMilli(it.startedMs), it.minutes) } }

    /** Pausar debilita: con el modo estricto no se puede y con aprobaciones se crea una solicitud. */
    fun pause(profileId: Long, minutes: Int) {
        when (val result = guard.check(ApprovalExecutor.profilePause(profileId), Weakening.ACTION_PROFILE_PAUSE, minutes.toString())) {
            GuardResult.Allowed -> viewModelScope.launch {
                _message.value = if (pauses.pause(profileId, minutes)) "Pausa de ${formatAmountOfTime(minutes.toLong())} iniciada." else "No se pudo pausar."
            }
            is GuardResult.Blocked -> _message.value = result.message
        }
    }

    fun resume(profileId: Long) { viewModelScope.launch { pauses.resume(profileId) } }

    fun profile(id: Long): Flow<Profile?> = repository.profiles.map { list -> list.firstOrNull { it.id == id } }

    suspend fun usage(): Map<Long, ProfileUsage> = usageRepository.loadAll()

    internal fun state(profile: Profile, usage: ProfileUsage?, nowMs: Long): ProfilePreviewState {
        val now = Instant.ofEpochMilli(nowMs)
        val local = clock.localAt(now)
        return ProfilePreviewState(
            profile = profile,
            status = ProfilePreview.status(profile, now, local, if (Conditions.needsDevice(listOf(profile))) deviceContext.current() else DeviceContext()),
            limit = profile.limit?.let { ProfilePreview.limitProgress(it, usage, nowMs, local, settings.dayStartMinute.value) },
            editLocked = guard.isLocked(now),
        )
    }
}
