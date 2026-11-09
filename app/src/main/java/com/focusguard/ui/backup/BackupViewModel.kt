package com.focusguard.ui.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.RestoreResult
import com.focusguard.data.ServerBackupRepository
import com.focusguard.data.SyncRepository
import com.focusguard.data.SyncResult
import com.focusguard.server.messageOr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BackupViewModel(private val backups: ServerBackupRepository, private val sync: SyncRepository) : ViewModel() {
    val lastBackupMs: StateFlow<Long?> = backups.lastBackupMs
    val syncEnabled: StateFlow<Boolean> = sync.enabled
    val lastSyncMs: StateFlow<Long?> = sync.lastSyncMs

    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        message = null
    }

    /** Guarda la copia. La frase se pide dos veces: una equivocada deja la copia sin poder abrirse. */
    fun save(passphrase: String, repeat: String) {
        val problem = passphraseProblem(passphrase, repeat, repeatRequired = true)
        if (problem != null) message = problem else run { backups.save(passphrase.toCharArray()).messageOr("Copia guardada.") }
    }

    fun restore(passphrase: String) {
        if (passphrase.isEmpty()) {
            message = "Escribe la frase con la que guardaste la copia."
            return
        }
        run {
            when (val result = backups.restore(passphrase.toCharArray())) {
                is RestoreResult.Done -> "Copia restaurada: ${result.profiles} perfiles añadidos. Lo que ya tenías no se ha tocado." +
                    (if (result.strictNeedsPin) " El modo estricto de la copia pide un PIN que aquí no hay: configúralo de nuevo." else "") +
                    (if (result.strictNeedsPartner) " El modo estricto de la copia pide la aprobación de un compañero que aquí no hay: vincula uno y configúralo de nuevo." else "")
                RestoreResult.NoBackup -> "No hay ninguna copia que esta versión de la app pueda leer. Si la guardaste con una versión más nueva, actualiza FocusGuard."
                RestoreResult.WrongPassphrase -> "La frase no es la de la copia, o la copia está dañada."
                is RestoreResult.Failed -> result.message
            }
        }
    }

    /** Activa la sincronización. La frase tiene que ser la misma en todos los dispositivos; en el primero, repetirla evita una errata. */
    fun enableSync(passphrase: String, repeat: String) {
        val problem = passphraseProblem(passphrase, repeat, repeatRequired = false)
        if (problem != null) message = problem else run { syncMessage(sync.enable(passphrase.toCharArray(), repeat.toCharArray()), "Sincronización activada.") }
    }

    fun syncNow() = run { syncMessage(sync.syncNow(), "Sincronizado.") }

    fun disableSync() {
        sync.disable()
        message = "Este teléfono ya no se sincroniza. Lo que hay en el servidor y en tus otros dispositivos no se ha tocado."
    }

    fun deleteSyncRemote() = run { sync.deleteRemote().messageOr("Se borró lo sincronizado del servidor y este teléfono dejó de sincronizar.") }

    private fun syncMessage(result: SyncResult, done: String) = when (result) {
        SyncResult.Done -> done
        SyncResult.NotEnabled -> "La sincronización no está activada."
        SyncResult.WrongPassphrase -> "La frase no es la de tus otros dispositivos. Tiene que ser la misma en todos."
        SyncResult.NeedsRepeat -> "Es la primera vez que se sincroniza: repite la frase para evitar una errata."
        is SyncResult.Failed -> result.message
    }

    fun deleteAll() = run { backups.deleteAll().messageOr("Copias borradas del servidor.") }

    private fun run(action: suspend () -> String) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                message = action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "No se pudo completar. Inténtalo de nuevo."
            } finally {
                busy = false
            }
        }
    }

    /** Con [repeatRequired] la repetición tiene que coincidir siempre; sin él, solo si se escribió (en la sincronización la pide el servidor al crear el documento). */
    private fun passphraseProblem(passphrase: String, repeat: String, repeatRequired: Boolean) = when {
        passphrase.length < MIN_PASSPHRASE -> "La frase necesita al menos $MIN_PASSPHRASE caracteres."
        (repeatRequired || repeat.isNotEmpty()) && passphrase != repeat -> "Las dos frases no coinciden."
        else -> null
    }

    private companion object {
        const val MIN_PASSPHRASE = 10
    }
}
