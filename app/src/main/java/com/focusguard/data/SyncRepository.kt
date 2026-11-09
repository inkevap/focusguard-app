package com.focusguard.data

import com.focusguard.decision.BackupSettings
import com.focusguard.decision.BackupSnapshot
import com.focusguard.decision.SyncMerge
import com.focusguard.server.ApiResult
import com.focusguard.server.RemoteSync
import com.focusguard.server.ServerApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Dónde se guarda la frase de la sincronización y de qué cuenta es: la necesita cada sincronización automática, así que no se pide de nuevo. */
interface SyncSecrets {
    val owner: String?

    fun read(): CharArray?

    fun write(passphrase: CharArray, owner: String?)

    fun clear()
}

/** Lo de este teléfono que la sincronización lee y escribe; en la app lo pone la copia de seguridad. */
interface SyncLocal {
    fun syncSettings(): BackupSettings

    /** Aplica lo mezclado sin aflojar nada de lo que ya hay. */
    fun applySync(incoming: BackupSettings)

    val lastSyncMs: StateFlow<Long?>

    fun setLastSyncMs(ms: Long?)
}

sealed interface SyncResult {
    data object Done : SyncResult

    data object NotEnabled : SyncResult

    /** La frase no es la que cifró el documento de los otros dispositivos. */
    data object WrongPassphrase : SyncResult

    /** Aún no hay documento y la frase no se repitió: se crearía con una frase que quizá lleve una errata. */
    data object NeedsRepeat : SyncResult

    data class Failed(val message: String) : SyncResult
}

/**
 * Sincronización de ajustes entre dispositivos (N4). Cada vez: lee el documento cifrado de la cuenta, lo mezcla con los ajustes de aquí
 * ([SyncMerge]: solo suma, nunca afloja), aplica el resultado con la regla de restaurar una copia y, si cambió algo, lo vuelve a subir sobre
 * la versión leída. Si otro teléfono escribió entretanto, el servidor lo rechaza y se repite desde la lectura. Los perfiles no se sincronizan:
 * viajan con la copia de seguridad. [currentAccount] es el correo de la sesión: la frase guardada solo vale para la cuenta con la que se activó.
 */
class SyncRepository(
    private val api: ServerApi,
    private val local: SyncLocal,
    private val secrets: SyncSecrets,
    private val nowMs: () -> Long,
    private val currentAccount: () -> String?,
) {
    private val _enabled = MutableStateFlow(secrets.read() != null)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val lastSyncMs: StateFlow<Long?> get() = local.lastSyncMs

    /** Dos sincronizaciones a la vez (el trabajo periódico y un botón) harían doble el trabajo y se estorbarían. */
    private val lock = Mutex()

    /** Sube con cada [disable]: una sincronización que empezó antes y acaba después no reactiva ni vuelve a anotar lo que se desactivó. */
    @Volatile private var generation = 0

    /**
     * Activa la sincronización con [passphrase]. La frase solo se guarda si la primera sincronización sale bien (con una distinta a la de
     * los otros dispositivos, no). Si aún no hay documento, [repeat] tiene que coincidir: es la frase con la que se creará.
     */
    suspend fun enable(passphrase: CharArray, repeat: CharArray): SyncResult = lock.withLock {
        val asked = generation
        val result = run(passphrase, confirmed = repeat.contentEquals(passphrase))
        if (result == SyncResult.Done && asked == generation) {
            secrets.write(passphrase, currentAccount())
            _enabled.value = true
        }
        result
    }

    suspend fun syncNow(): SyncResult = lock.withLock {
        val passphrase = secrets.read()
        // La frase es de otra cuenta (la sesión caducó y entró otra persona) o se perdió: no se sincroniza con lo que no es suyo.
        if (passphrase == null || secrets.owner != currentAccount()) {
            if (_enabled.value) disable()
            return@withLock SyncResult.NotEnabled
        }
        run(passphrase, confirmed = true)
    }

    /** Deja de sincronizar en este teléfono; lo que ya está en el servidor y en los otros dispositivos no se toca. */
    fun disable() {
        generation++
        secrets.clear()
        local.setLastSyncMs(null)
        _enabled.value = false
    }

    /** Borra el documento de la cuenta y deja de sincronizar aquí: si no, la siguiente sincronización lo volvería a crear. */
    suspend fun deleteRemote(): ApiResult<Unit> = api.deleteSync().also { if (it is ApiResult.Ok) disable() }

    private suspend fun run(passphrase: CharArray, confirmed: Boolean): SyncResult {
        val asked = generation
        try {
            repeat(MAX_ATTEMPTS) {
                // null: otro teléfono escribió entretanto y hay que repetir desde la lectura.
                val result = attempt(passphrase, confirmed) ?: return@repeat
                if (result == SyncResult.Done && asked == generation) local.setLastSyncMs(nowMs())
                return result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un documento raro o un fallo del disco: se cuenta, no tumba el trabajo de segundo plano ni la app.
            return SyncResult.Failed("No se pudo sincronizar. Inténtalo de nuevo.")
        }
        return SyncResult.Failed("Otro dispositivo está sincronizando. Inténtalo de nuevo en un momento.")
    }

    /** Un intento: leer, mezclar, aplicar y subir. Devuelve null si hubo un conflicto de versión (se repite). */
    private suspend fun attempt(passphrase: CharArray, confirmed: Boolean): SyncResult? {
        val remote: RemoteSync? = when (val result = api.getSync()) {
            is ApiResult.Ok -> result.value
            is ApiResult.Failure -> if (result.error.isNotFound) null else return SyncResult.Failed(result.error.message)
        }
        if (remote == null && !confirmed) return SyncResult.NeedsRepeat
        // Sin documento aún, lo remoto es «nada».
        val remoteSettings = if (remote == null) BackupSettings() else {
            // Derivar la clave cuesta cientos de miles de vueltas: fuera del hilo principal.
            val plain = withContext(Dispatchers.Default) { BackupCrypto.decrypt(remote.blob, passphrase) } ?: return SyncResult.WrongPassphrase
            try {
                BackupJson.fromJson(String(plain)).settings
            } catch (e: IllegalStateException) {
                return SyncResult.Failed("El documento de sincronización no es válido.")
            }
        }
        val merged = SyncMerge.merge(local.syncSettings(), remoteSettings)
        local.applySync(merged)
        // Si el documento ya era esto mismo, no hay nada que subir.
        if (remote != null && merged == remoteSettings) return SyncResult.Done

        val blob = withContext(Dispatchers.Default) {
            BackupCrypto.encrypt(BackupJson.toJson(BackupSnapshot(settings = merged), includePlaces = false, exportedAtMs = 0).toString().toByteArray(), passphrase)
        }
        if (blob.size > MAX_BLOB) return SyncResult.Failed("Lo que hay que sincronizar es demasiado grande.")
        return when (val result = api.putSync(remote?.version ?: 0, BackupCrypto.FORMAT, blob)) {
            is ApiResult.Ok -> SyncResult.Done
            is ApiResult.Failure -> if (result.error.isSyncConflict) null else SyncResult.Failed(result.error.message)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3

        /** El tope del servidor (`server/src/routes/sync.ts`). */
        const val MAX_BLOB = 256_000
    }
}
