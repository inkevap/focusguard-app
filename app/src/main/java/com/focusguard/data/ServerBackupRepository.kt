package com.focusguard.data

import com.focusguard.server.ApiError
import com.focusguard.server.ApiResult
import com.focusguard.server.ServerApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

sealed interface RestoreResult {
    /** Cuántos perfiles se añadieron y si el modo estricto de la copia se quedó fuera por necesitar un PIN o un compañero que aquí no hay. */
    data class Done(val profiles: Int, val strictNeedsPin: Boolean, val strictNeedsPartner: Boolean = false) : RestoreResult

    data object NoBackup : RestoreResult

    /** La frase no es la que cifró la copia (o la copia está dañada). */
    data object WrongPassphrase : RestoreResult

    data class Failed(val message: String) : RestoreResult
}

/**
 * Copia de seguridad de la configuración en el servidor (N2, H8). Se cifra en el teléfono con una frase que solo sabe el usuario: el
 * servidor guarda bytes que no puede leer. Sin la frase la copia no se recupera, y eso se dice antes de guardar.
 */
class ServerBackupRepository(
    private val api: ServerApi,
    private val backup: BackupRepository,
    private val settings: SettingsRepository,
    private val clock: TrustedClock,
) {
    /** Cuándo se subió la última copia (hora de confianza); null si nunca. */
    val lastBackupMs: StateFlow<Long?> get() = settings.lastServerBackupMs

    suspend fun save(passphrase: CharArray): ApiResult<Unit> {
        // Armar la copia y derivar la clave (cientos de miles de vueltas) cuesta: fuera del hilo principal.
        val blob = withContext(Dispatchers.Default) {
            BackupCrypto.encrypt(backup.snapshotJson(forServer = true).toString().toByteArray(), passphrase)
        }
        if (blob.size > MAX_BLOB) return ApiResult.Failure(ApiError.Http(413, "La copia es demasiado grande para guardarla en el servidor."))
        val result = api.uploadBackup(BackupCrypto.FORMAT, blob)
        if (result is ApiResult.Ok) settings.setLastServerBackupMs(clock.nowMs())
        return result
    }

    suspend fun restore(passphrase: CharArray): RestoreResult {
        val remote = when (val result = api.latestBackup(BackupCrypto.FORMAT)) {
            is ApiResult.Ok -> result.value
            is ApiResult.Failure -> return if (result.error.isNotFound) RestoreResult.NoBackup else RestoreResult.Failed(result.error.message)
        }
        val plain = withContext(Dispatchers.Default) { BackupCrypto.decrypt(remote.blob, passphrase) } ?: return RestoreResult.WrongPassphrase
        return try {
            backup.restore(String(plain), fromServer = true).let { RestoreResult.Done(it.profiles, it.strictNeedsPin, it.strictNeedsPartner) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // `restore` explica con un mensaje claro lo que falla (nada se aplicó: se valida todo antes).
            RestoreResult.Failed(e.message ?: ApiError.Unexpected.message)
        } catch (e: Exception) {
            // Un fallo del disco o de la base a medias: se cuenta, no se cierra la app.
            RestoreResult.Failed("No se pudo restaurar la copia. Inténtalo de nuevo.")
        }
    }

    suspend fun deleteAll(): ApiResult<Unit> = api.deleteBackups().also {
        if (it is ApiResult.Ok) settings.setLastServerBackupMs(null)
    }

    private companion object {
        /** El tope del servidor (`server/src/routes/backups.ts`): se evita subir lo que va a rechazar. */
        const val MAX_BLOB = 2_000_000
    }
}
