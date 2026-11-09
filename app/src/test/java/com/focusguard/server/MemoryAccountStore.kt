package com.focusguard.server

/** Almacén en memoria para las pruebas. */
class MemoryAccountStore : AccountStore {
    override var accessToken: String? = null
        private set
    override var refreshToken: String? = null
        private set
    override var email: String? = null
        private set
    override var deviceId: String? = null
        private set

    override fun saveSession(email: String?, accessToken: String, refreshToken: String) {
        this.email = email
        saveTokens(accessToken, refreshToken)
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    override fun saveDeviceId(id: String?) {
        deviceId = id
    }

    override val hasSession: Boolean get() = accessToken != null

    override fun expireSession() {
        accessToken = null
        refreshToken = null
    }

    override fun clearSession() {
        accessToken = null
        refreshToken = null
        email = null
        deviceId = null
    }
}
