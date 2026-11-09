package com.focusguard.data

import com.focusguard.decision.BackupSettings
import com.focusguard.server.ApiResult
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.server.FakeTransport
import com.focusguard.server.HttpRequest
import com.focusguard.server.HttpResponse
import com.focusguard.server.MemoryAccountStore
import com.focusguard.server.ServerApi
import com.focusguard.server.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/** El servidor de sincronización de mentira: un documento con versión, como el de `server/src/routes/sync.ts`. */
private class FakeSyncServer {
    var version = 0
    var blob: ByteArray? = null
    val puts = mutableListOf<HttpRequest>()

    /** Ocurre antes de aceptar la siguiente escritura: otro dispositivo se adelanta. */
    var beforePut: (() -> Unit)? = null

    fun handle(request: HttpRequest): HttpResponse? = when (request.method) {
        "GET" -> blob?.let { HttpResponse(200, mapOf("x-sync-version" to version.toString(), "x-sync-format" to "1"), it) } ?: json(404, """{"error":"No hay"}""")
        "PUT" -> {
            puts += request
            beforePut?.also { beforePut = null }?.invoke()
            // Sin documento (nunca hubo, o se borró) la base es 0; con él, la versión leída.
            val expected = if (blob == null) 0 else version
            if (request.headers["x-sync-base"]?.toInt() == expected) {
                version++
                blob = request.body
                json(200, """{"version":$version}""")
            } else {
                json(409, """{"error":"Otro dispositivo sincronizó antes","code":"sync_conflict"}""")
            }
        }
        "DELETE" -> {
            blob = null
            version++
            json(204, "")
        }
        else -> null
    }
}

private class FakeLocal(var settings: BackupSettings = BackupSettings()) : SyncLocal {
    val applied = mutableListOf<BackupSettings>()
    private val last = MutableStateFlow<Long?>(null)

    override fun syncSettings() = settings

    override fun applySync(incoming: BackupSettings) {
        applied += incoming
        settings = incoming
    }

    override val lastSyncMs: StateFlow<Long?> get() = last

    override fun setLastSyncMs(ms: Long?) {
        last.value = ms
    }
}

private class FakeSecrets : SyncSecrets {
    var stored: CharArray? = null
    override var owner: String? = null

    override fun read() = stored

    override fun write(passphrase: CharArray, owner: String?) {
        stored = passphrase
        this.owner = owner
    }

    override fun clear() {
        stored = null
        owner = null
    }
}

class SyncRepositoryTest {
    private val server = FakeSyncServer()
    private val store = MemoryAccountStore().also { it.saveSession("a@b.co", "acc1", "ref1") }
    private val frase = "una frase larga y secreta".toCharArray()

    private var account: String? = "a@b.co"

    private fun device(local: FakeLocal = FakeLocal(), secrets: FakeSecrets = FakeSecrets()) =
        Triple(SyncRepository(ServerApi(FakeTransport(server::handle), { "https://fg.test" }, store), local, secrets, { 1_000L }, { account }), local, secrets)

    private suspend fun SyncRepository.enable(passphrase: CharArray) = enable(passphrase, passphrase)

    private fun remoteSettings(passphrase: CharArray = frase): BackupSettings =
        BackupJson.fromJson(String(BackupCrypto.decrypt(server.blob!!, passphrase)!!)).settings

    @Test fun elPrimerDispositivoCreaElDocumentoCifradoYGuardaLaFrase() = runBlocking {
        val (sync, local, secrets) = device(FakeLocal(BackupSettings(adultBlock = true, adultWords = setOf("a"))))
        assertEquals(SyncResult.Done, sync.enable(frase))
        assertTrue(sync.enabled.value)
        assertNotNull(secrets.stored)
        assertEquals(1_000L, local.lastSyncMs.value)
        assertEquals(1, server.version)
        assertEquals(BackupSettings(adultBlock = true, adultWords = setOf("a")), remoteSettings())
        assertEquals("0", server.puts.single().headers["x-sync-base"])
    }

    @Test fun elDocumentoNoSeLeeSinLaFrase() = runBlocking {
        device(FakeLocal(BackupSettings(adultWords = setOf("secreta")))).first.enable(frase)
        assertFalse(String(server.blob!!, Charsets.ISO_8859_1).contains("secreta"))
        assertNull(BackupCrypto.decrypt(server.blob!!, "otra frase distinta".toCharArray()))
    }

    @Test fun elSegundoDispositivoRecibeLoDelPrimeroYSubeLoSuyo() = runBlocking {
        device(FakeLocal(BackupSettings(adultBlock = true, adultWords = setOf("a")))).first.enable(frase)
        val (sync, local, _) = device(FakeLocal(BackupSettings(adultWords = setOf("b"), newApps = NewAppsPolicy(true, 48))))
        assertEquals(SyncResult.Done, sync.enable(frase))
        val expected = BackupSettings(adultBlock = true, adultWords = setOf("a", "b"), newApps = NewAppsPolicy(true, 48))
        assertEquals(expected, local.settings)
        assertEquals(expected, remoteSettings())
        assertEquals(2, server.version)
        assertEquals("1", server.puts.last().headers["x-sync-base"])
    }

    @Test fun conLaFraseEquivocadaNoSeActivaNiSeAplicaNada() = runBlocking {
        device(FakeLocal(BackupSettings(adultWords = setOf("a")))).first.enable(frase)
        val (sync, local, secrets) = device(FakeLocal(BackupSettings(adultWords = setOf("b"))))
        assertEquals(SyncResult.WrongPassphrase, sync.enable("otra frase distinta".toCharArray()))
        assertFalse(sync.enabled.value)
        assertNull(secrets.stored)
        assertTrue(local.applied.isEmpty())
        assertEquals(1, server.version)
    }

    @Test fun siElDocumentoYaEsLoMismoNoSubeNada() = runBlocking {
        val (sync, _, _) = device(FakeLocal(BackupSettings(adultWords = setOf("a"))))
        sync.enable(frase)
        val puts = server.puts.size
        assertEquals(SyncResult.Done, sync.syncNow())
        assertEquals(puts, server.puts.size)
        assertEquals(1, server.version)
    }

    @Test fun conUnConflictoLeeDeNuevoMezclaYReintenta() = runBlocking {
        val (sync, local, _) = device(FakeLocal(BackupSettings(adultWords = setOf("a"))))
        // Otro dispositivo escribe justo antes de que llegue la nuestra.
        val other = device(FakeLocal(BackupSettings(adultWords = setOf("z"))), FakeSecrets()).first
        server.beforePut = { runBlocking { other.enable(frase) } }
        assertEquals(SyncResult.Done, sync.enable(frase))
        assertEquals(setOf("a", "z"), local.settings.adultWords)
        assertEquals(setOf("a", "z"), remoteSettings().adultWords)
    }

    @Test fun sinSincronizacionActivadaNoHaceNada() = runBlocking {
        val (sync, _, _) = device()
        assertEquals(SyncResult.NotEnabled, sync.syncNow())
        assertEquals(0, server.puts.size)
    }

    @Test fun alDejarDeSincronizarSeBorraLaFraseYLaFecha() = runBlocking {
        val (sync, local, secrets) = device()
        sync.enable(frase)
        sync.disable()
        assertFalse(sync.enabled.value)
        assertNull(secrets.stored)
        assertNull(local.lastSyncMs.value)
        assertEquals(SyncResult.NotEnabled, sync.syncNow())
    }

    @Test fun unDocumentoQueNoEsUnaCopiaValidaDaUnFallo() = runBlocking {
        val (sync, _, _) = device()
        server.version = 1
        server.blob = BackupCrypto.encrypt("no es json".toByteArray(), frase)
        val result = sync.enable(frase)
        assertTrue(result is SyncResult.Failed)
        assertFalse(sync.enabled.value)
        assertEquals(0, server.puts.size)
    }

    @Test fun laPrimeraVezHaceFaltaRepetirLaFraseParaCrearElDocumento() = runBlocking {
        val (sync, _, secrets) = device()
        assertEquals(SyncResult.NeedsRepeat, sync.enable(frase, "otra distinta".toCharArray()))
        assertEquals(SyncResult.NeedsRepeat, sync.enable(frase, CharArray(0)))
        assertFalse(sync.enabled.value)
        assertNull(secrets.stored)
        assertNull(server.blob)
        assertEquals(SyncResult.Done, sync.enable(frase, frase))
        // En los demás dispositivos el documento ya existe: no hace falta repetir.
        val (second, _, _) = device()
        assertEquals(SyncResult.Done, second.enable(frase, CharArray(0)))
    }

    @Test fun laFraseSoloValeParaLaCuentaConLaQueSeActivo() = runBlocking {
        val (sync, local, secrets) = device()
        sync.enable(frase)
        assertEquals("a@b.co", secrets.owner)
        // La sesión caducó y entró otra cuenta: no se sincroniza con el documento de la nueva ni se sube lo de la anterior.
        account = "otra@b.co"
        val puts = server.puts.size
        assertEquals(SyncResult.NotEnabled, sync.syncNow())
        assertFalse(sync.enabled.value)
        assertNull(secrets.stored)
        assertNull(local.lastSyncMs.value)
        assertEquals(puts, server.puts.size)
    }

    @Test fun siSePierdeLaFraseLaPantallaDejaDeDecirQueEstaActivada() = runBlocking {
        val (sync, _, secrets) = device()
        sync.enable(frase)
        secrets.clear()
        assertEquals(SyncResult.NotEnabled, sync.syncNow())
        assertFalse(sync.enabled.value)
    }

    @Test fun borrarLoDelServidorTambienDejaDeSincronizarAqui() = runBlocking {
        val (sync, _, secrets) = device()
        sync.enable(frase)
        assertIs<ApiResult.Ok<Unit>>(sync.deleteRemote())
        assertFalse(sync.enabled.value)
        assertNull(secrets.stored)
        assertNull(server.blob)
    }

    @Test fun desactivarMientrasSeActivaNoDejaLaFraseGuardada() = runBlocking {
        val (sync, _, secrets) = device()
        // El cierre de sesión llega mientras la primera sincronización está a medias (antes de que la nuestra se acepte).
        server.beforePut = { sync.disable() }
        assertEquals(SyncResult.Done, sync.enable(frase))
        assertFalse(sync.enabled.value)
        assertNull(secrets.stored)
    }
}
