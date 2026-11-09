package com.focusguard.decision

import com.focusguard.model.DeviceContext
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import java.time.Instant
import java.time.LocalDateTime

/** Condiciones que dependen solo del tiempo. El límite de uso se evalúa aparte con el uso acumulado. */
object ProfileActivation {

    fun isActive(profile: Profile, localNow: LocalDateTime, now: Instant, context: DeviceContext = DeviceContext()): Boolean {
        if (!profile.enabled) return false
        if (profile.pausedUntil?.let { now.isBefore(it) } == true) return false
        profile.activeUntil?.let { if (!now.isBefore(it)) return false }
        profile.pomodoro?.let { if (Pomodoro.phaseAt(it, now) !is PomodoroPhase.Focus) return false }
        return Conditions.triggersMet(profile, localNow, context)
    }

    /** ¿Hay un bloqueo de pánico en curso? */
    fun isPanicActive(profiles: List<Profile>, now: Instant): Boolean = activePanic(profiles, now) != null

    /** El bloqueo total sin salida en curso (Ayúdame o Dormir), si hay uno. Un Bloquear ya «solo al acabar el tiempo» no lo es: solo bloquea lo que se eligió. */
    fun activePanic(profiles: List<Profile>, now: Instant): Profile? =
        profiles.firstOrNull { it.kind == ProfileKind.PANIC && !isExpired(it, now) }

    /** ¿Ya no tiene sentido guardar este perfil temporal? */
    fun isExpired(profile: Profile, now: Instant): Boolean = when {
        profile.activeUntil != null -> !now.isBefore(profile.activeUntil!!)
        profile.pomodoro != null -> Pomodoro.phaseAt(profile.pomodoro!!, now) is PomodoroPhase.Finished
        else -> false
    }
}
