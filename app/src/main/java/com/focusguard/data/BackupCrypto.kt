package com.focusguard.data

import java.nio.ByteBuffer
import java.text.Normalizer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado de la copia de seguridad que se sube al servidor (N2): el servidor guarda bytes opacos y nunca ve la frase. AES-256 en modo
 * GCM (cifra y autentica: una frase equivocada o un archivo tocado no descifran) con la clave derivada de la frase por PBKDF2.
 *
 * Sobre: `FGB1` | iteraciones (4) | sal (16) | nonce (12) | texto cifrado y etiqueta. Lo anterior al texto cifrado va como datos
 * autenticados. Las iteraciones viajan en el sobre para poder subirlas sin romper copias viejas.
 */
object BackupCrypto {
    /** Versión del sobre; va en la cabecera `X-Backup-Format` del servidor. */
    const val FORMAT = 1

    private const val DEFAULT_ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 1_000
    private const val MAX_ITERATIONS = 600_000
    private val MAGIC = "FGB1".toByteArray()
    private const val SALT = 16
    private const val NONCE = 12
    private const val TAG_BITS = 128
    private const val HEADER = 4 + 4 + SALT + NONCE

    fun encrypt(plain: ByteArray, passphrase: CharArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS)
        val random = SecureRandom()
        val salt = ByteArray(SALT).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER).put(MAGIC).putInt(iterations).put(salt).put(nonce).array()
        return header + cipher(Cipher.ENCRYPT_MODE, header, passphrase, iterations, salt, nonce).doFinal(plain)
    }

    /** El contenido, o `null` si la frase no es la que cifró la copia o el archivo está dañado. */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray? {
        if (blob.size < HEADER + TAG_BITS / 8 || !blob.copyOfRange(0, 4).contentEquals(MAGIC)) return null
        val iterations = ByteBuffer.wrap(blob, 4, 4).int
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) return null
        return try {
            val salt = blob.copyOfRange(8, 8 + SALT)
            val nonce = blob.copyOfRange(8 + SALT, HEADER)
            cipher(Cipher.DECRYPT_MODE, blob.copyOf(HEADER), passphrase, iterations, salt, nonce).doFinal(blob, HEADER, blob.size - HEADER)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    /**
     * La frase como solo ASCII (los bytes de su forma normalizada en UTF-8, en hexadecimal): cada proveedor de PBKDF2 codifica los
     * caracteres a su manera (Android recorta a 8 bits, el JDK usa UTF-8), y una frase con ñ o acentos no abriría la copia en otro teléfono.
     */
    private fun portable(passphrase: CharArray): CharArray {
        val bytes = Normalizer.normalize(String(passphrase), Normalizer.Form.NFC).toByteArray(Charsets.UTF_8)
        return CharArray(bytes.size * 2) { "0123456789abcdef"[(bytes[it / 2].toInt() shr if (it % 2 == 0) 4 else 0) and 0xF] }
    }

    private fun cipher(mode: Int, header: ByteArray, passphrase: CharArray, iterations: Int, salt: ByteArray, nonce: ByteArray): Cipher {
        val spec = PBEKeySpec(portable(passphrase), salt, iterations, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
        }
    }
}
