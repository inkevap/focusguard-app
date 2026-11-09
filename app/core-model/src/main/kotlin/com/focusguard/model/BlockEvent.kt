package com.focusguard.model

import java.time.Instant

/** Un bloqueo registrado en el teléfono. Nunca contiene texto de pantalla, solo qué se bloqueó y cuándo. */
data class BlockEvent(
    val at: Instant,
    val profileId: Long,
    val reason: BlockReason,
    /** Paquete o dominio bloqueado. */
    val target: String,
)
