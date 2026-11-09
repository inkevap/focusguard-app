package com.focusguard.server

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.focusguard.model.PartnerEvent
import com.focusguard.model.RemoteStatus
import org.json.JSONArray
import org.json.JSONObject

sealed interface ApiError {
    val message: String

    /** Merece la pena volver a intentarlo más tarde (sin conexión, servidor caído, demasiados intentos). Lo demás no se arregla solo. */
    val retryable: Boolean get() = this is Offline || (this is Http && (status == 429 || status >= 500))

    /** El servidor dice que el contenido está mal formado o es demasiado grande: repetirlo igual no cambiará nada. */
    val rejectedAsInvalid: Boolean get() = this is Http && (status == 400 || status == 413 || status == 422)

    val isNotFound: Boolean get() = this is Http && status == 404

    /** Otro dispositivo escribió antes en la sincronización: hay que leer, mezclar y volver a escribir. */
    val isSyncConflict: Boolean get() = this is Http && code == "sync_conflict"

    /** El servidor no conoce el dispositivo de esta cuenta (se reinició de cero, o la cuenta es otra). */
    val isDeviceUnknown: Boolean get() = this is Http && code == "device_unknown"

    /** El servidor ya no tiene esa solicitud (caducó y se purgó). Un 404 sin código puede ser un proxy o una versión sin la ruta. */
    val isRequestUnknown: Boolean get() = this is Http && code == "request_unknown"

    /** `code` es el identificador estable que manda el servidor en algunos errores (por ejemplo `device_unknown`). */
    class Http(val status: Int, override val message: String, val code: String? = null) : ApiError
    data object Offline : ApiError {
        override val message = "No hay conexión con el servidor. Inténtalo de nuevo."
    }
    data object NotConfigured : ApiError {
        override val message = "Falta la dirección del servidor."
    }
    /** El servidor respondió con éxito pero con algo que no es lo esperado (un portal de acceso, un proxy…). */
    data object Unexpected : ApiError {
        override val message = "El servidor respondió algo inesperado. Comprueba la dirección."
    }
    data object SessionExpired : ApiError {
        override val message = "La sesión caducó. Inicia sesión otra vez."
    }
}

sealed interface ApiResult<out T> {
    class Ok<T>(val value: T) : ApiResult<T>
    class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** Lee los campos de una respuesta correcta; si falta alguno es una respuesta inesperada, no un fallo del programa. */
inline fun <R> ApiResult<JSONObject>.read(transform: (JSONObject) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> try {
        ApiResult.Ok(transform(value))
    } catch (e: org.json.JSONException) {
        ApiResult.Failure(ApiError.Unexpected)
    }
    is ApiResult.Failure -> this
}

fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Ok -> ApiResult.Ok(transform(value))
    is ApiResult.Failure -> this
}

/** Una copia de seguridad tal como la guarda el servidor: el sobre cifrado y su formato. */
class RemoteBackup(val format: Int, val blob: ByteArray)

/** El documento de sincronización: lo cifrado y la versión sobre la que escribir la siguiente. */
class RemoteSync(val version: Int, val format: Int, val blob: ByteArray)

/** Resolvedor DNS sobre HTTPS de este dispositivo y qué hacer si no responde. */
class DnsConfig(val dohUrl: String, val failOpen: Boolean)

/** Cambios de una lista: con `full` los valores de `added` son la lista entera y reemplazan a la local. */
class ListChanges(val version: Long, val full: Boolean, val added: List<String>, val removed: List<String>)

/** El texto para el usuario de un resultado sin valor: [ok] si salió bien, el mensaje del fallo si no. */
fun ApiResult<Unit>.messageOr(ok: String): String = when (this) {
    is ApiResult.Ok -> ok
    is ApiResult.Failure -> error.message
}

data class Me(val email: String, val emailVerified: Boolean)

/** `accepted` es falso mientras la invitación espera a que el compañero la acepte desde su correo. */
data class PartnerInfo(val id: String, val email: String, val accepted: Boolean)

/** Alguien a quien ayudo (I3): [mutual] si él también es mi compañero, [pending] las solicitudes suyas que esperan mi respuesta. */
data class HelpedInfo(val id: String, val email: String, val mutual: Boolean, val pending: Int)

/** Resultado de quitar al compañero: sin aprobación si solo era una invitación; con una solicitud si ya había aceptado. */
sealed interface PartnerRemoval {
    data object Done : PartnerRemoval
    class NeedsApproval(val requestId: String) : PartnerRemoval
}

/**
 * Cliente de la API del servidor (`docs/BACKEND.md`). Guarda la sesión en [AccountStore] y, si el servidor responde 401 a una
 * petición con sesión, renueva el token de acceso con el de refresco y repite la petición una vez. Si el refresco ya no vale,
 * cierra la sesión local.
 */
class ServerApi(
    private val transport: HttpTransport,
    private val baseUrl: () -> String?,
    private val store: AccountStore,
) {
    private val refreshLock = Mutex()

    suspend fun register(email: String, password: String): ApiResult<Unit> = authenticate("/auth/register", email, password)

    suspend fun login(email: String, password: String): ApiResult<Unit> = authenticate("/auth/login", email, password)

    /** El servidor responde igual exista o no la cuenta. */
    suspend fun requestPasswordReset(email: String): ApiResult<Unit> = callUnit("POST", "/auth/password/reset", JSONObject().put("email", email.trim()), signedIn = false)

    suspend fun me(): ApiResult<Me> =
        call("GET", "/me", null, signedIn = true).read { Me(it.getString("email"), it.getBoolean("emailVerified")) }

    suspend fun resendVerification(): ApiResult<Unit> = callUnit("POST", "/auth/email/verify/resend", JSONObject(), signedIn = true)

    /** Crea el dispositivo, o actualiza el nombre y el token de push del que ya hay. Devuelve su identificador. Sin token no se toca el que el servidor ya guarda. */
    suspend fun registerDevice(name: String, existingId: String?, pushToken: String? = null): ApiResult<String> {
        val body = JSONObject().put("name", name)
        existingId?.let { body.put("deviceId", it) }
        pushToken?.let { body.put("fcmToken", it) }
        return call("PUT", "/devices/register", body, signedIn = true).read { it.getString("deviceId") }
    }

    /** El servidor deja de mandar avisos a este dispositivo. Se llama al cerrar sesión, antes de olvidar la sesión. */
    suspend fun forgetPushToken(deviceId: String): ApiResult<Unit> = callUnit("DELETE", "/devices/$deviceId/push-token", null, signedIn = true)

    // ---- Compañero ----

    suspend fun partners(): ApiResult<List<PartnerInfo>> = call("GET", "/partners", null, signedIn = true).read { json ->
        val array = json.getJSONArray("partners")
        (0 until array.length()).map { array.getJSONObject(it).let { p -> PartnerInfo(p.getString("id"), p.getString("email"), p.getString("status") == "aceptado") } }
    }

    /** Las personas que me eligieron como compañero. Con el correo sin confirmar el servidor responde una lista vacía. */
    suspend fun helping(): ApiResult<List<HelpedInfo>> = call("GET", "/partners/helping", null, signedIn = true).read { json ->
        val array = json.getJSONArray("helping")
        (0 until array.length()).map { array.getJSONObject(it).let { h -> HelpedInfo(h.getString("id"), h.getString("email"), h.optBoolean("mutual"), h.optInt("pending")) } }
    }

    suspend fun invitePartner(email: String): ApiResult<PartnerInfo> =
        call("POST", "/partners", JSONObject().put("email", email.trim()), signedIn = true).read { PartnerInfo(it.getString("id"), it.getString("email"), accepted = false) }

    suspend fun removePartner(id: String): ApiResult<PartnerRemoval> =
        call("DELETE", "/partners/$id", null, signedIn = true).read { if (it.has("requestId")) PartnerRemoval.NeedsApproval(it.getString("requestId")) else PartnerRemoval.Done }

    // ---- Solicitudes de aprobación ----

    /** Pide al compañero que apruebe una acción. `summary` es el texto que verá en su correo. Devuelve el id de la solicitud. */
    suspend fun createRequest(deviceId: String, actionType: Int, summary: String): ApiResult<String> {
        val body = JSONObject().put("deviceId", deviceId).put("actionType", actionType).put("summary", summary)
        return call("POST", "/requests", body, signedIn = true).read { it.getString("requestId") }
    }

    /** Estado de la solicitud. Una aprobación que ya se gastó cuenta como caducada: no se puede aplicar otra vez. */
    suspend fun request(id: String): ApiResult<RemoteStatus> = call("GET", "/requests/$id", null, signedIn = true).read {
        when (it.getString("status")) {
            "pendiente" -> RemoteStatus.WAITING
            "aprobada" -> if (it.optBoolean("consumed", false)) RemoteStatus.EXPIRED else RemoteStatus.APPROVED
            "rechazada" -> RemoteStatus.REJECTED
            else -> RemoteStatus.EXPIRED
        }
    }

    suspend fun cancelRequest(id: String): ApiResult<Unit> = callUnit("POST", "/requests/$id/cancel", JSONObject(), signedIn = true)

    /** Marca la aprobación como usada: solo funciona una vez. */
    suspend fun consumeRequest(id: String): ApiResult<Unit> = callUnit("POST", "/requests/$id/consume", JSONObject(), signedIn = true)

    // ---- Listas de bloqueo (públicas, sin cuenta) ----

    /** Versión actual de cada lista del servidor. */
    suspend fun blocklistVersions(): ApiResult<Map<String, Long>> = call("GET", "/blocklists", null, signedIn = false).read { json ->
        val versions = json.getJSONObject("versions")
        versions.keys().asSequence().associateWith { versions.getLong(it) }
    }

    /** Lo que cambió en una lista desde la versión `since` (o la lista entera si el servidor lo decide). */
    suspend fun blocklistChanges(list: String, since: Long): ApiResult<ListChanges> =
        call("GET", "/blocklists?list=$list&since=$since", null, signedIn = false).read { json ->
            fun strings(key: String) = json.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
            ListChanges(json.getLong("version"), json.getBoolean("full"), strings("added"), strings("removed"))
        }

    // ---- Reportar un bloqueo erróneo (B10) ----

    /** Avisa al servidor de un bloqueo que el usuario cree erróneo; la lista se revisa a mano. Pide cuenta. */
    suspend fun reportWrongBlock(kind: String, value: String): ApiResult<Unit> =
        callUnit("POST", "/reports/wrong-block", JSONObject().put("kind", kind).put("value", value), signedIn = true)

    // ---- Filtro DNS ----

    /** A dónde mandar las consultas DNS de este dispositivo. Pide cuenta: la ruta lleva un identificador propio del dispositivo. */
    suspend fun dnsConfig(deviceId: String): ApiResult<DnsConfig> =
        call("GET", "/dns/config?deviceId=$deviceId", null, signedIn = true).read { DnsConfig(it.getString("dohUrl"), it.optBoolean("failOpen", true)) }

    // ---- Copia de seguridad (cifrada en el teléfono; el servidor guarda bytes opacos) ----

    suspend fun uploadBackup(format: Int, blob: ByteArray): ApiResult<Unit> =
        when (val result = exchange("POST", "/backups", Body(blob, "application/octet-stream", mapOf("x-backup-format" to format.toString())), signedIn = true)) {
            is ApiResult.Ok -> if (result.value.status == 201) ApiResult.Ok(Unit) else failure(result.value)
            is ApiResult.Failure -> result
        }

    /** La copia más reciente que esta versión sabe leer (`maxFormat`). Sin ninguna, falla con un 404. */
    suspend fun latestBackup(maxFormat: Int): ApiResult<RemoteBackup> =
        when (val result = exchange("GET", "/backups/latest?maxFormat=$maxFormat", null, signedIn = true, accept = "application/octet-stream")) {
            is ApiResult.Ok -> {
                val response = result.value
                val format = response.headers["x-backup-format"]?.toIntOrNull()
                if (response.status == 200 && format != null) ApiResult.Ok(RemoteBackup(format, response.body)) else failure(response)
            }
            is ApiResult.Failure -> result
        }

    suspend fun deleteBackups(): ApiResult<Unit> = callUnit("DELETE", "/backups", null, signedIn = true)

    // ---- Sincronización entre dispositivos ----

    /** El documento de la cuenta. Sin ninguno aún, falla con un 404. */
    suspend fun getSync(): ApiResult<RemoteSync> =
        when (val result = exchange("GET", "/sync", null, signedIn = true, accept = "application/octet-stream")) {
            is ApiResult.Ok -> {
                val response = result.value
                val version = response.headers["x-sync-version"]?.toIntOrNull()
                val format = response.headers["x-sync-format"]?.toIntOrNull()
                if (response.status == 200 && version != null && format != null) ApiResult.Ok(RemoteSync(version, format, response.body)) else failure(response)
            }
            is ApiResult.Failure -> result
        }

    /** Escribe sobre la versión [base] (0 si aún no hay documento). Devuelve la versión nueva; con un conflicto, un fallo con `isSyncConflict`. */
    suspend fun putSync(base: Int, format: Int, blob: ByteArray): ApiResult<Int> {
        val body = Body(blob, "application/octet-stream", mapOf("x-sync-base" to base.toString(), "x-sync-format" to format.toString()))
        return when (val result = exchange("PUT", "/sync", body, signedIn = true)) {
            is ApiResult.Ok -> result.value.let { response ->
                if (response.status == 200) parse(response).read { it.getInt("version") } else failure(response)
            }
            is ApiResult.Failure -> result
        }
    }

    suspend fun deleteSync(): ApiResult<Unit> = callUnit("DELETE", "/sync", null, signedIn = true)

    // ---- Test de adicción ----

    /** Guarda la puntuación total (nunca las respuestas) en la cuenta. */
    suspend fun saveAssessment(score: Int, takenAt: java.time.Instant): ApiResult<Unit> =
        callUnit("POST", "/assessments", JSONObject().put("score", score).put("takenAt", takenAt.toString()), signedIn = true)

    suspend fun deleteAssessments(): ApiResult<Unit> = callUnit("DELETE", "/assessments", null, signedIn = true)

    // ---- Eventos y latido ----

    /** Manda un lote de sucesos (como mucho 50). Solo llevan qué pasó, cuándo y el paquete o dominio. */
    suspend fun sendEvents(deviceId: String, events: List<PartnerEvent>): ApiResult<Unit> {
        val array = JSONArray()
        events.forEach { array.put(JSONObject().put("type", it.type.wire).put("at", it.at.toString()).apply { it.label?.let { l -> put("label", l) } }) }
        return callUnit("POST", "/events", JSONObject().put("deviceId", deviceId).put("events", array), signedIn = true)
    }

    /** Señal de vida: si dejan de llegar, el servidor avisa al compañero de que la app ya no funciona. */
    suspend fun heartbeat(deviceId: String): ApiResult<Unit> = callUnit("POST", "/heartbeat", JSONObject().put("deviceId", deviceId), signedIn = true)

    fun signOut() = store.clearSession()

    private suspend fun authenticate(path: String, email: String, password: String): ApiResult<Unit> {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        return call("POST", path, body, signedIn = false).read {
            store.saveSession(email.trim().lowercase(), it.getString("accessToken"), it.getString("refreshToken"))
        }
    }

    private suspend fun callUnit(method: String, path: String, body: JSONObject?, signedIn: Boolean): ApiResult<Unit> = call(method, path, body, signedIn).map { }

    private suspend fun call(method: String, path: String, body: JSONObject?, signedIn: Boolean): ApiResult<JSONObject> =
        when (val result = exchange(method, path, body?.let(Body::of), signedIn)) {
            is ApiResult.Ok -> parse(result.value)
            is ApiResult.Failure -> result
        }

    /** El cuerpo de una petición: sus bytes, su tipo y las cabeceras propias que lleve. */
    private class Body(val bytes: ByteArray, val contentType: String, val headers: Map<String, String> = emptyMap()) {
        companion object {
            const val JSON = "application/json"

            fun of(json: JSONObject) = Body(json.toString().toByteArray(), JSON)
        }
    }

    /**
     * Manda la petición y, si el servidor dice 401 a una petición con sesión, renueva el token y la repite una vez. Devuelve la respuesta
     * tal cual (sea cual sea su estado) o el fallo de red o de sesión.
     */
    private suspend fun exchange(method: String, path: String, body: Body?, signedIn: Boolean, accept: String = Body.JSON): ApiResult<HttpResponse> {
        val base = baseUrl() ?: return ApiResult.Failure(ApiError.NotConfigured)
        // El token con el que sale esta petición: si otra ya lo renovó mientras tanto, no se renueva otra vez.
        val sentWith = if (signedIn) store.accessToken else null
        var response = send(method, base + path, body, accept = accept, token = sentWith) ?: return ApiResult.Failure(ApiError.Offline)
        if (signedIn && response.status == 401) {
            when (val outcome = refresh(base, staleAccess = sentWith)) {
                Refresh.Done -> response = send(method, base + path, body, accept = accept, token = store.accessToken) ?: return ApiResult.Failure(ApiError.Offline)
                Refresh.Expired -> return ApiResult.Failure(ApiError.SessionExpired)
                is Refresh.Failed -> return ApiResult.Failure(outcome.error)
            }
            if (response.status == 401) {
                store.expireSession()
                return ApiResult.Failure(ApiError.SessionExpired)
            }
        }
        return ApiResult.Ok(response)
    }

    private fun parse(response: HttpResponse): ApiResult<JSONObject> {
        val json = response.json()
        // 204: respuesta correcta sin cuerpo (por ejemplo, cancelar una invitación).
        if (response.status == 204) return ApiResult.Ok(JSONObject())
        if (response.status in 200..299) return if (json != null) ApiResult.Ok(json) else ApiResult.Failure(ApiError.Unexpected)
        val message = json?.optString("error").orEmpty().ifBlank { genericMessage(response.status) }
        return ApiResult.Failure(ApiError.Http(response.status, message, json?.optString("code")?.ifBlank { null }))
    }

    /** El fallo que dice una respuesta que no era la esperada (su mensaje, si lo trae). */
    private fun failure(response: HttpResponse): ApiResult.Failure = parse(response) as? ApiResult.Failure ?: ApiResult.Failure(ApiError.Unexpected)

    private fun genericMessage(status: Int) = when {
        status == 429 -> "Demasiados intentos. Espera un rato."
        status >= 500 -> "El servidor tuvo un problema. Inténtalo más tarde."
        else -> "No se pudo completar la petición."
    }

    private suspend fun send(method: String, url: String, body: Body?, accept: String, token: String?): HttpResponse? {
        val headers = buildMap {
            put("accept", accept)
            if (body != null) {
                put("content-type", body.contentType)
                putAll(body.headers)
            }
            if (token != null) put("authorization", "Bearer $token")
        }
        return try {
            transport.send(HttpRequest(method, url, headers, body?.bytes))
        } catch (e: NetworkException) {
            null
        }
    }

    private sealed interface Refresh {
        data object Done : Refresh
        data object Expired : Refresh
        class Failed(val error: ApiError) : Refresh
    }

    /** Una sola renovación a la vez: si otra petición ya cambió el token de acceso, se aprovecha. */
    private suspend fun refresh(base: String, staleAccess: String?): Refresh = refreshLock.withLock {
        if (store.accessToken != staleAccess) return Refresh.Done
        val refreshToken = store.refreshToken ?: return Refresh.Expired
        val body = Body.of(JSONObject().put("refreshToken", refreshToken))
        val response = send("POST", "$base/auth/refresh", body, accept = Body.JSON, token = null) ?: return Refresh.Failed(ApiError.Offline)
        if (response.status == 401) {
            store.expireSession()
            return Refresh.Expired
        }
        val parsed = parse(response)
        if (parsed is ApiResult.Failure) return Refresh.Failed(parsed.error)
        val saved = (parsed as ApiResult.Ok).value.let { json -> runCatching { json.getString("accessToken") to json.getString("refreshToken") }.getOrNull() }
            ?: return Refresh.Failed(ApiError.Unexpected)
        store.saveTokens(saved.first, saved.second)
        Refresh.Done
    }
}
