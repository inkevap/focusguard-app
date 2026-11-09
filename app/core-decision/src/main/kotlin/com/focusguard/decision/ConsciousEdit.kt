package com.focusguard.decision

import com.focusguard.model.Profile

/** Edición consciente (E16): editar un horario existente con el modo estricto apagado pide esperar unos segundos. */
object ConsciousEdit {

    const val WAIT_SECONDS = 10

    /** Con el modo estricto bloqueando la edición no hay espera: directamente no se puede. */
    fun required(existing: Profile?, strictLocked: Boolean): Boolean =
        existing != null && existing.intervals.isNotEmpty() && !strictLocked
}
