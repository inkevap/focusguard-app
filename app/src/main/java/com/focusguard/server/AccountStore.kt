package com.focusguard.server

/** Sesión del servidor y datos de la cuenta en este teléfono. */
interface AccountStore {
    val accessToken: String?
    val refreshToken: String?
    val email: String?
    val deviceId: String?

    /** Hay tokens guardados. Barato: no descifra nada. */
    val hasSession: Boolean

    fun saveSession(email: String?, accessToken: String, refreshToken: String)

    fun saveTokens(accessToken: String, refreshToken: String)

    fun saveDeviceId(id: String?)

    /** La sesión dejó de valer (el servidor ya no renueva el token): se olvidan los tokens pero se conserva el dispositivo, para no duplicarlo al volver a entrar. */
    fun expireSession()

    /** Cierre de sesión hecho por el usuario: olvida todo, incluido el dispositivo (otra cuenta lo registrará de nuevo). */
    fun clearSession()
}
