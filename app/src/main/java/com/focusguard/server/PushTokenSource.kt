package com.focusguard.server

/** De dónde sale el token con el que el servidor avisa a este teléfono (push). `null` si no hay push en esta compilación o no se pudo obtener. */
fun interface PushTokenSource {
    suspend fun current(): String?
}
