package com.focusguard.decision

import java.time.Duration
import java.time.Instant

/** Pausar un perfil (E9): lo deja de bloquear un rato y vuelve solo. Pausar debilita, así que pasa por el guardián de cambios. */
object Pauses {

    val PRESET_MINUTES = listOf(5, 15, 30, 60)

    /** Cuántas pausas se guardan por perfil para enseñarlas en su vista previa. */
    const val HISTORY_KEPT = 10

    fun until(now: Instant, minutes: Int): Instant = now.plus(Duration.ofMinutes(minutes.toLong()))

    /** Solo las duraciones ofrecidas: una solicitud con otra cifra no se aplica. */
    fun isValid(minutes: Int) = minutes in PRESET_MINUTES
}
