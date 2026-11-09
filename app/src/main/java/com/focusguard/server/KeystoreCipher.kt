package com.focusguard.server

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM con una clave del almacén de claves de Android que no sale del dispositivo. Cada valor va ligado a su nombre (datos
 * autenticados): no se puede cambiar uno por otro. Lo usan la sesión de la cuenta y la frase de la sincronización.
 */
class KeystoreCipher(private val alias: String) {
    private val secretKey: SecretKey by lazy { loadOrCreateKey() }

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String, name: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey)
            updateAAD(name.toByteArray())
        }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    /** Lanza `GeneralSecurityException` si la etiqueta no cuadra o la clave ya no sirve, y `ProviderException` en un fallo pasajero del almacén. */
    fun decrypt(stored: String, name: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, bytes, 0, IV_BYTES))
            updateAAD(name.toByteArray())
        }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }

    /**
     * Descifra, o devuelve null si no se puede. Con lo que no se va a poder leer nunca más (etiqueta que no cuadra, clave invalidada o
     * perdida, texto dañado) se llama a [onUnreadable] para que quien guarda el valor lo borre; un fallo pasajero del almacén de claves
     * (ocupado, arranque) solo da null y se reintenta en la siguiente lectura.
     */
    fun decryptOrNull(stored: String, name: String, onUnreadable: () -> Unit): String? = try {
        decrypt(stored, name)
    } catch (e: java.security.GeneralSecurityException) {
        onUnreadable()
        null
    } catch (e: IllegalArgumentException) {
        onUnreadable()
        null
    } catch (e: java.security.ProviderException) {
        null
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
