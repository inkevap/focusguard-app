package com.focusguard.server

import android.content.Context

/**
 * Guarda la sesión con los tokens cifrados (AES-GCM) con una clave del almacén de claves de Android que no sale del
 * dispositivo; cada valor va ligado a su nombre (datos autenticados), así que no se puede cambiar uno por otro. Si la clave ya
 * no sirve (datos restaurados en otro teléfono, clave invalidada), la sesión se da por caducada.
 * Los tokens se descifran una vez y se guardan en memoria: descifrar en cada petición costaba una consulta al almacén de claves.
 */
class KeystoreAccountStore(context: Context) : AccountStore {
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    private class Tokens(val access: String, val refresh: String)

    @Volatile private var cached: Tokens? = null

    private val cipher = KeystoreCipher(ALIAS)

    private fun tokens(): Tokens? {
        cached?.let { return it }
        val access = readSecret(KEY_ACCESS) ?: return null
        val refresh = readSecret(KEY_REFRESH) ?: return null
        return Tokens(access, refresh).also { cached = it }
    }

    override val accessToken: String? get() = tokens()?.access
    override val refreshToken: String? get() = tokens()?.refresh
    override val email: String? get() = prefs.getString(KEY_EMAIL, null)
    override val deviceId: String? get() = prefs.getString(KEY_DEVICE, null)
    override val hasSession: Boolean get() = prefs.contains(KEY_ACCESS) && prefs.contains(KEY_REFRESH)

    override fun saveSession(email: String?, accessToken: String, refreshToken: String) {
        writeTokens(accessToken, refreshToken, email)
    }

    override fun saveTokens(accessToken: String, refreshToken: String) {
        writeTokens(accessToken, refreshToken, email = null)
    }

    private fun writeTokens(accessToken: String, refreshToken: String, email: String?) {
        val edit = prefs.edit().putString(KEY_ACCESS, cipher.encrypt(accessToken, KEY_ACCESS)).putString(KEY_REFRESH, cipher.encrypt(refreshToken, KEY_REFRESH))
        if (email != null) edit.putString(KEY_EMAIL, email)
        edit.apply()
        cached = Tokens(accessToken, refreshToken)
    }

    override fun saveDeviceId(id: String?) {
        prefs.edit().putString(KEY_DEVICE, id).apply()
    }

    override fun expireSession() {
        cached = null
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).apply()
    }

    override fun clearSession() {
        cached = null
        prefs.edit().clear().apply()
    }

    private fun readSecret(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return cipher.decryptOrNull(stored, name, onUnreadable = ::expireSession)
    }

    private companion object {
        const val ALIAS = "focusguard_account"
        const val KEY_ACCESS = "access"
        const val KEY_REFRESH = "refresh"
        const val KEY_EMAIL = "email"
        const val KEY_DEVICE = "device"
    }
}
