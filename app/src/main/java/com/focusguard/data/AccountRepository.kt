package com.focusguard.data

import com.focusguard.server.AccountStore
import com.focusguard.server.ApiError
import com.focusguard.server.ApiResult
import com.focusguard.server.PushTokenSource
import com.focusguard.server.ServerApi
import com.focusguard.server.map
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** Qué sabe la app de la cuenta. `emailVerified` es `null` mientras no se haya podido preguntar al servidor. */
data class AccountState(val email: String? = null, val emailVerified: Boolean? = null, val deviceRegistered: Boolean = false) {
    val signedIn: Boolean get() = email != null
}

/** Cuenta propia en el servidor (N1) y registro de este dispositivo (N5). */
class AccountRepository(
    private val api: ServerApi,
    private val store: AccountStore,
    private val deviceName: () -> String,
    private val saveServerUrl: (String) -> Boolean,
    /** Qué más hay que olvidar al cerrar la sesión (el compañero de la cuenta). */
    private val onSignOut: () -> Unit = {},
    /** Quien entra puede ser otra cuenta: se vuelve a preguntar por su compañero (con un fallo de red se conserva lo anterior, que es lo más estricto). */
    private val onSignIn: suspend () -> Unit = {},
    /** Token de push de este teléfono; sin él el servidor no puede avisarle y el teléfono se entera al consultar. */
    private val pushToken: PushTokenSource = PushTokenSource { null },
) {
    /** Último token que el servidor confirmó. Solo en memoria: tras reiniciar la app se vuelve a enviar una vez, y es una petición idempotente. */
    private var sentPushToken: String? = null

    private val _state = MutableStateFlow(snapshot(verified = null))
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private fun snapshot(verified: Boolean?) = AccountState(
        email = store.email.takeIf { store.hasSession },
        emailVerified = verified,
        deviceRegistered = store.deviceId != null,
    )

    suspend fun register(email: String, password: String): ApiResult<Unit> = signedIn(api.register(email, password))

    suspend fun login(email: String, password: String): ApiResult<Unit> = signedIn(api.login(email, password))

    private suspend fun signedIn(result: ApiResult<Unit>): ApiResult<Unit> {
        if (result is ApiResult.Ok) {
            onSignIn()
            refresh()
            ensureDevice()
        }
        return result
    }

    /** Cualquier llamada que acabe con la sesión caducada deja el estado al día, no solo `refresh`. */
    private fun <T> tracked(result: ApiResult<T>): ApiResult<T> {
        if (result is ApiResult.Failure && result.error == ApiError.SessionExpired) _state.value = snapshot(null)
        return result
    }

    /** Pregunta al servidor por el estado de la cuenta (por ejemplo, si ya se confirmó el correo). */
    suspend fun refresh(): ApiResult<Unit> {
        if (!store.hasSession) {
            _state.value = snapshot(null)
            return ApiResult.Ok(Unit)
        }
        val me = tracked(api.me())
        if (me is ApiResult.Ok) {
            _state.value = snapshot(me.value.emailVerified)
            // Si al entrar no hubo conexión para registrar el dispositivo, se reintenta aquí.
            // También si el token de push cambió o aún no se mandó (por ejemplo, tras actualizar desde una versión sin push).
            val token = currentPushToken()
            if (store.deviceId == null || (token != null && token != sentPushToken)) registerDevice(token)
        }
        return me.map { }
    }

    /** Registra este dispositivo si aún no lo está. Si el servidor ya no lo conoce (se reinició de cero), lo registra de nuevo. */
    suspend fun ensureDevice(): ApiResult<Unit> = registerDevice(currentPushToken())

    private suspend fun registerDevice(token: String?): ApiResult<Unit> {
        if (!store.hasSession) return ApiResult.Failure(ApiError.SessionExpired)
        var result = tracked(api.registerDevice(deviceName(), store.deviceId, token))
        if (result is ApiResult.Failure && result.error.isDeviceUnknown && store.deviceId != null) {
            store.saveDeviceId(null)
            result = tracked(api.registerDevice(deviceName(), null, token))
        }
        return when (result) {
            is ApiResult.Ok -> {
                store.saveDeviceId(result.value)
                sentPushToken = token ?: sentPushToken
                _state.value = _state.value.copy(deviceRegistered = true)
                ApiResult.Ok(Unit)
            }
            is ApiResult.Failure -> result
        }
    }

    /** Un fallo al pedir el token (sin Google Play, sin red) es «sin push», no un error de la cuenta. */
    private suspend fun currentPushToken(): String? = try {
        pushToken.current()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    suspend fun resendVerification(): ApiResult<Unit> = tracked(api.resendVerification())

    /** Solo se puede cambiar de servidor con la sesión cerrada: los tokens valen para el servidor que los dio. Devuelve si se guardó. */
    fun changeServer(raw: String): Boolean = !store.hasSession && saveServerUrl(raw)

    suspend fun requestPasswordReset(email: String): ApiResult<Unit> = api.requestPasswordReset(email)

    /**
     * Cierra la sesión. Antes le pide al servidor que deje de mandar avisos a este dispositivo (otra cuenta puede entrar en este teléfono);
     * si no hay red o tarda, se cierra igual: la app ignora los avisos de una cuenta que ya no tiene sesión.
     */
    suspend fun signOut() {
        try {
            val deviceId = store.deviceId
            if (store.hasSession && deviceId != null) withTimeoutOrNull(FORGET_PUSH_MS) { api.forgetPushToken(deviceId) }
        } finally {
            api.signOut()
            sentPushToken = null
            onSignOut()
            _state.value = snapshot(null)
        }
    }

    private companion object {
        const val FORGET_PUSH_MS = 5_000L
    }
}
