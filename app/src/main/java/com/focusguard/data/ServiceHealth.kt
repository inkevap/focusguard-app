package com.focusguard.data

import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Última señal de vida del servicio de accesibilidad, para que el solucionador sepa si sigue funcionando.
 * Vive en memoria: el servicio y la app comparten proceso, y un proceso muerto cuenta como «sin señal».
 */
class ServiceHealth {

    private val _beats = MutableStateFlow<Instant?>(null)

    /** La última señal; cambia con cada una. Quien solo espera «ya está listo» lo escucha en vez de preguntar cada poco. */
    val beats: StateFlow<Instant?> = _beats.asStateFlow()

    val lastBeat: Instant? get() = _beats.value

    fun beat(now: Instant) {
        _beats.value = now
    }

    fun clear() {
        _beats.value = null
    }
}
