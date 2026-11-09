package com.focusguard.data

import com.focusguard.decision.StrictMode
import com.focusguard.model.StrictProtection
import java.time.Instant

/** Pregunta rápida para la interfaz: ¿puedo editar ahora o lo impide el modo estricto? */
class StrictGuard(private val repository: StrictRepository, private val clock: TrustedClock) {

    fun isEditLocked(now: Instant? = null): Boolean {
        val at = now ?: clock.now()
        return StrictMode.isProtected(repository.config.value, StrictProtection.EDIT_LOCK, clock.localAt(at), at)
    }
}
