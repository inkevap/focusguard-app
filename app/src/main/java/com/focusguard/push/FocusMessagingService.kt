package com.focusguard.push

import android.util.Log
import com.focusguard.data.AccountRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Recibe los avisos de Firebase. Los dos métodos corren fuera del hilo principal, así que se puede esperar a la red. */
class FocusMessagingService : FirebaseMessagingService(), KoinComponent {
    private val handler: PushHandler by inject()
    private val account: AccountRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) = guarded("aviso") { handler.handle(message.data) }

    /** El token cambia de vez en cuando (reinstalación, restauración): el servidor tiene que saber el nuevo. Sin sesión `ensureDevice` no hace nada. */
    override fun onNewToken(token: String) = guarded("token") { account.ensureDevice() }

    /**
     * Una excepción aquí tiraría el proceso de la app, que es la que protege: se anota y se sigue; el teléfono se entera en el siguiente envío.
     * El trabajo corre aparte y aquí solo se espera hasta [HANDLE_MS]: la red es bloqueante y no se corta al cancelar, y Android da unos 20 s
     * a estos métodos, así que el servicio devuelve el control a tiempo aunque el servidor no conteste.
     */
    private fun guarded(what: String, block: suspend () -> Unit) {
        val job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo procesar el $what de push", e)
            }
        }
        runCatching { runBlocking { withTimeoutOrNull(HANDLE_MS) { job.join() } ?: job.cancel() } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FocusPush"
        const val HANDLE_MS = 15_000L
    }
}
