package com.focusguard.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ProfileRepository
import com.focusguard.data.TrustedClock
import com.focusguard.decision.ProfileActivation
import com.focusguard.decision.SleepPlan
import com.focusguard.model.Profile
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Dormir: bloquea todo el teléfono hasta la hora de despertar, y no se puede quitar antes. Solo queda permitido el reloj y la alarma (para que suene)
 * y las apps que se elijan. Usa el mismo mecanismo que Ayúdame (`Profile.panic`), pero termina a una hora del día y no tras un número de minutos.
 */
class SleepViewModel(
    private val repository: ProfileRepository,
    private val clock: TrustedClock,
    /** Los paquetes del reloj y la alarma instalados; consulta al sistema. */
    private val clockApps: () -> Set<String>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** Hasta cuándo dura el bloqueo en curso (o un bloqueo de pánico, que se comporta igual); null si no hay. */
    val activeUntil: StateFlow<Instant?> = repository.profiles
        .map { profiles -> ProfileActivation.activePanic(profiles, clock.now())?.activeUntil }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Los paquetes del reloj y la alarma, que siempre quedan permitidos. */
    val alarmApps: Set<String> get() = clockApps()

    fun minutesUntil(wakeMinute: Int, now: Instant = clock.now()) = SleepPlan.minutesUntil(now, wakeMinute, zone)

    fun start(wakeMinute: Int, extraApps: Set<String>) {
        val until = SleepPlan.wakeAt(clock.now(), wakeMinute, zone)
        viewModelScope.launch { repository.save(Profile.panic(until, TITLE, clockApps() + extraApps)) }
    }

    companion object {
        /** El título del perfil que crea; Inicio lo usa para abrir esta pantalla desde su tarjeta. */
        const val TITLE = "Dormir"
    }
}
