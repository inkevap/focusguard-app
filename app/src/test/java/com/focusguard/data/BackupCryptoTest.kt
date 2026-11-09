package com.focusguard.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class BackupCryptoTest {
    private val plain = """{"version":2,"profiles":[]}""".toByteArray()
    private val phrase = "una frase larga y mía".toCharArray()
    private val fast = 1_000

    @Test fun cifraYDescifraConLaMismaFrase() {
        val blob = BackupCrypto.encrypt(plain, phrase, fast)
        assertContentEquals(plain, BackupCrypto.decrypt(blob, phrase))
    }

    @Test fun conOtraFraseNoDescifra() {
        val blob = BackupCrypto.encrypt(plain, phrase, fast)
        assertNull(BackupCrypto.decrypt(blob, "otra frase distinta".toCharArray()))
    }

    @Test fun unArchivoTocadoNoDescifra() {
        val blob = BackupCrypto.encrypt(plain, phrase, fast)
        for (index in listOf(0, 5, 10, 30, blob.size - 1)) {
            val tampered = blob.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertNull(BackupCrypto.decrypt(tampered, phrase), "byte $index")
        }
    }

    @Test fun cadaCopiaLlevaSuSalYSuNonce() {
        val a = BackupCrypto.encrypt(plain, phrase, fast)
        val b = BackupCrypto.encrypt(plain, phrase, fast)
        assertNotEquals(a.toList(), b.toList())
    }

    @Test fun elContenidoNoSeVeEnElSobre() {
        val blob = BackupCrypto.encrypt(plain, phrase, fast)
        assertFalse(String(blob, Charsets.ISO_8859_1).contains("profiles"))
    }

    @Test fun losSobresRotosOAjenosSeRechazanSinFallar() {
        assertNull(BackupCrypto.decrypt(ByteArray(0), phrase))
        assertNull(BackupCrypto.decrypt(ByteArray(100), phrase))
        // Iteraciones fuera de rango: no se acepta que un archivo hostil pida trabajo sin límite.
        val hostile = BackupCrypto.encrypt(plain, phrase, fast).also { it[4] = 0x7F }
        assertNull(BackupCrypto.decrypt(hostile, phrase))
    }

    @Test fun unaFraseConAcentosYEnyesSeDescifraIgualSeaCualSeaLaFormaDeEscribirla() {
        val composed = "contraseña-ñandú-2026".toCharArray()
        val decomposed = java.text.Normalizer.normalize("contraseña-ñandú-2026", java.text.Normalizer.Form.NFD).toCharArray()
        val blob = BackupCrypto.encrypt(plain, composed, fast)
        assertContentEquals(plain, BackupCrypto.decrypt(blob, decomposed))
    }
}
