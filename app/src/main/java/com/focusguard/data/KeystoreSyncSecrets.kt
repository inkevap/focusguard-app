package com.focusguard.data

import android.content.Context
import com.focusguard.server.KeystoreCipher

/**
 * La frase de la sincronización, cifrada con una clave del almacén de claves del teléfono, y la cuenta a la que pertenece (para no
 * sincronizar con otra distinta). Si la clave ya no sirve, la frase se da por perdida.
 */
class KeystoreSyncSecrets(context: Context) : SyncSecrets {
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val cipher = KeystoreCipher("focusguard_sync")

    override val owner: String? get() = prefs.getString(OWNER, null)

    override fun read(): CharArray? {
        val stored = prefs.getString(KEY, null) ?: return null
        return cipher.decryptOrNull(stored, KEY, onUnreadable = ::clear)?.toCharArray()
    }

    override fun write(passphrase: CharArray, owner: String?) {
        prefs.edit().putString(KEY, cipher.encrypt(String(passphrase), KEY)).putString(OWNER, owner).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY).remove(OWNER).apply()
    }

    private companion object {
        const val KEY = "passphrase"
        const val OWNER = "owner"
    }
}
