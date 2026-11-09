package com.focusguard.push

import com.focusguard.server.PushTokenSource
import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Token de Firebase Cloud Messaging. Sin `google-services.json` en la compilación Firebase no se inicia: `getInstance()` falla y aquí
 * eso es «sin push», no un error (el teléfono se entera consultando el estado). Con Google Play lento o sin acceso a FCM el token puede
 * tardar mucho: no se espera más de [TOKEN_MS], porque quien pide el token es el inicio de sesión.
 */
object FirebasePushTokenSource : PushTokenSource {
    private const val TOKEN_MS = 5_000L

    override suspend fun current(): String? = withTimeoutOrNull(TOKEN_MS) {
        suspendCancellableCoroutine { cont ->
            val task = runCatching { FirebaseMessaging.getInstance().token }.getOrNull()
            if (task == null) {
                cont.resume(null)
            } else {
                task.addOnCompleteListener { cont.resume(if (it.isSuccessful) it.result?.takeIf { token -> token.isNotBlank() } else null) }
            }
        }
    }
}
