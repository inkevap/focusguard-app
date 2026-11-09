package com.focusguard.decision

import com.focusguard.model.DeviceContext
import com.focusguard.model.Profile
import java.time.Instant
import java.time.LocalDateTime

/**
 * Bloqueo de arranque (H5). Tras reiniciar, el servicio de accesibilidad tarda un rato en estar listo y mientras tanto no hay quien
 * bloquee: una superposición tapa la pantalla hasta que el servicio da señal, con un tope para no dejar el teléfono inservible.
 */
object BootShield {
    /** Pasado este tiempo desde el arranque ya no se muestra, esté o no listo el servicio. */
    const val MAX_AGE_MS = 5 * 60_000L

    /** La regla completa: hay algo que proteger, el servicio aún no está listo y no pasó el tiempo máximo. */
    fun shouldShow(bootAgeMs: Long, serviceReady: Boolean, somethingToProtect: Boolean): Boolean =
        somethingToProtect && !serviceReady && !isExpired(bootAgeMs)

    /**
     * ¿Hay algo que proteger ahora? Un perfil que bloquea, el modo estricto o un pánico en curso. Al arrancar no se conocen la Wi-Fi ni la
     * ubicación y se usa el contexto de «sin permiso de ubicación», que da esas condiciones por cumplidas: un perfil con Wi-Fi o lugar cuenta,
     * porque es mejor tapar un momento de más que dejar un hueco. El cargador sí se sabe que no está conectado hasta leerlo.
     */
    fun hasSomethingToProtect(profiles: List<Profile>, strictActive: Boolean, localNow: LocalDateTime, now: Instant): Boolean =
        strictActive || ProfileActivation.isPanicActive(profiles, now) ||
            profiles.any { ProfileActivation.isActive(it, localNow, now, DeviceContext(locationAvailable = false)) }

    /** ¿Ya pasó el tiempo máximo? [bootAgeMs] es el tiempo desde el arranque (reloj monótono: cambiar la hora no lo alarga ni lo acorta). */
    fun isExpired(bootAgeMs: Long): Boolean = bootAgeMs !in 0 until MAX_AGE_MS
}
