package com.focusguard.data

import com.focusguard.server.ApiError
import com.focusguard.server.ApiResult
import com.focusguard.server.HelpedInfo
import com.focusguard.server.ServerApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Las personas a las que ayudo como compañero (I3). Es solo para mirar: no cambia ninguna protección de este teléfono, así que no se guarda.
 * Sin conexión se enseña lo último que se supo; con cualquier otro fallo (la sesión caducó, la cuenta es otra) se vacía, para no enseñar
 * a nadie los datos de una cuenta que ya no está.
 */
class HelpingRepository(private val api: ServerApi) {
    private val _helping = MutableStateFlow<List<HelpedInfo>>(emptyList())
    val helping: StateFlow<List<HelpedInfo>> = _helping.asStateFlow()

    /** Sube con cada cierre de sesión: una respuesta que salió antes y llega después no repone lo que se vació. */
    @Volatile private var generation = 0

    suspend fun refresh(): ApiResult<Unit> {
        val asked = generation
        val result = api.helping()
        if (asked != generation) return ApiResult.Ok(Unit)
        return when (result) {
            is ApiResult.Ok -> {
                _helping.value = result.value
                ApiResult.Ok(Unit)
            }
            is ApiResult.Failure -> {
                if (result.error !is ApiError.Offline) _helping.value = emptyList()
                result
            }
        }
    }

    /** Al cerrar la sesión no queda nada de la cuenta anterior. */
    fun clear() {
        generation++
        _helping.value = emptyList()
    }
}
