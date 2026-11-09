package com.focusguard.ui.emergency

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.EmergencyRepository
import com.focusguard.data.PartnerEventOutbox
import com.focusguard.model.PartnerEventType
import com.focusguard.data.ProfileRepository
import com.focusguard.data.StreakStore
import com.focusguard.data.TrustedClock
import com.focusguard.decision.EmergencyAvailability
import com.focusguard.decision.EmergencyStep
import com.focusguard.decision.EmergencyUnlock
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Desbloqueo de emergencia con fricción (H12). Lo escrito en los pasos no se guarda: sirve para pararse a pensar. */
class EmergencyViewModel(
    private val emergency: EmergencyRepository,
    private val profiles: ProfileRepository,
    private val clock: TrustedClock,
    private val partnerEvents: PartnerEventOutbox,
    private val streaks: StreakStore,
) : ViewModel() {

    var step by mutableStateOf(EmergencyStep.INTRO)
        private set

    /** Lo escrito en el paso actual; se borra al avanzar. */
    var text by mutableStateOf("")
        private set
    var availability by mutableStateOf<EmergencyAvailability?>(null)
        private set
    var grantedUntil by mutableStateOf<Instant?>(null)
        private set
    private var waitEndsAt: Instant? = null
    private var granting = false

    val minutes: Int get() = emergency.config.minutes
    val perWeek: Int get() = emergency.config.perWeek

    init {
        viewModelScope.launch { availability = emergency.availability(clock.now(), profiles.profiles.first()) }
    }

    fun type(value: String) { text = value }

    val canAdvance: Boolean get() = EmergencyUnlock.canAdvance(step, text)

    fun advance() {
        if (!canAdvance) return
        step = EmergencyUnlock.next(step)
        text = ""
        if (step == EmergencyStep.WAIT) waitEndsAt = clock.now().plusSeconds(EmergencyUnlock.WAIT_SECONDS.toLong())
    }

    /** Segundos que faltan de la espera, medidos con el reloj de confianza. */
    fun waitLeft(nowMs: Long): Int {
        val end = waitEndsAt ?: return EmergencyUnlock.WAIT_SECONDS
        return ((end.toEpochMilli() - nowMs + 999) / 1000).toInt().coerceIn(0, EmergencyUnlock.WAIT_SECONDS)
    }

    /** Concede el desbloqueo al acabar la espera; el repositorio vuelve a comprobar cupo, modo estricto y pánico. */
    fun finish(nowMs: Long) {
        if (step != EmergencyStep.WAIT || waitLeft(nowMs) > 0 || granting) return
        granting = true
        viewModelScope.launch {
            try {
                val all = profiles.profiles.first()
                val now = clock.now()
                val until = emergency.grant(now, all)
                if (until != null) {
                    partnerEvents.record(PartnerEventType.EMERGENCY_UNLOCK)
                    // Un desbloqueo es una pausa de todo el bloqueo: cuenta para las rachas.
                    streaks.recordPause(now, emergency.config.minutes)
                    grantedUntil = until
                    step = EmergencyStep.DONE
                } else {
                    availability = emergency.availability(clock.now(), all)
                }
            } finally {
                granting = false
            }
        }
    }
}
