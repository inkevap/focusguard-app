package com.focusguard.data

import android.content.Context
import com.focusguard.data.StrictRepository.Companion.exitMethodFromJson
import com.focusguard.data.StrictRepository.Companion.toJson
import com.focusguard.model.ExitMethod
import com.focusguard.model.ExitRequest
import com.focusguard.model.ProfileLock
import com.focusguard.model.StrictAction
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Candados de perfiles sueltos y la solicitud de quitarlos en curso, en preferencias privadas. No dependen del modo estricto.
 * shortcut: no viajan en la copia de seguridad ni en la sincronización; al restaurar en otro teléfono los perfiles quedan sin candado. Subir si hace falta que sobrevivan a un reinstalado.
 */
class ProfileLockRepository(context: Context) {

    private val prefs = context.getSharedPreferences("profile_locks", Context.MODE_PRIVATE)

    private val _locks = MutableStateFlow(readLocks())
    val locks: StateFlow<Map<Long, ProfileLock>> = _locks.asStateFlow()

    private val _requests = MutableStateFlow(readRequests())
    val requests: StateFlow<Map<Long, ExitRequest>> = _requests.asStateFlow()

    fun isLocked(profileId: Long) = profileId in _locks.value

    fun lock(profileId: Long, lock: ProfileLock) {
        writeLocks(_locks.value + (profileId to lock))
        writeRequests(_requests.value - profileId)
    }

    /** Quita el candado y la solicitud de quitarlo. */
    fun unlock(profileId: Long) {
        writeLocks(_locks.value - profileId)
        writeRequests(_requests.value - profileId)
    }

    fun saveRequest(profileId: Long, request: ExitRequest?) =
        writeRequests(if (request == null) _requests.value - profileId else _requests.value + (profileId to request))

    /** El compañero aprobó quitar el candado de [profileId]. Devuelve false si ya no hay solicitud en curso. */
    fun markPartnerApproved(profileId: Long): Boolean {
        val current = _requests.value[profileId] ?: return false
        saveRequest(profileId, current.copy(partnerApproved = true))
        return true
    }

    private fun writeLocks(value: Map<Long, ProfileLock>) {
        val array = JSONArray()
        value.forEach { (id, lock) ->
            array.put(JSONObject().put("id", id).put("methods", JSONArray().also { m -> lock.exitMethods.forEach { m.put(it.toJson()) } }))
        }
        prefs.edit().putString(KEY_LOCKS, array.toString()).apply()
        _locks.value = value
    }

    private fun writeRequests(value: Map<Long, ExitRequest>) {
        val array = JSONArray()
        value.forEach { (id, request) -> array.put(request.toJson().put("id", id)) }
        prefs.edit().putString(KEY_REQUESTS, array.toString()).apply()
        _requests.value = value
    }

    private fun readLocks(): Map<Long, ProfileLock> = runCatching {
        val array = JSONArray(prefs.getString(KEY_LOCKS, "[]"))
        (0 until array.length()).associate {
            val item = array.getJSONObject(it)
            val methods = item.getJSONArray("methods")
            item.getLong("id") to ProfileLock((0 until methods.length()).map { i -> exitMethodFromJson(methods.getJSONObject(i)) })
        }
    }.getOrDefault(emptyMap())

    private fun readRequests(): Map<Long, ExitRequest> = runCatching {
        val array = JSONArray(prefs.getString(KEY_REQUESTS, "[]"))
        (0 until array.length()).associate {
            val item = array.getJSONObject(it)
            item.getLong("id") to ExitRequest(
                action = StrictAction.Disable,
                requestedAt = Instant.ofEpochMilli(item.getLong("requestedAtMs")),
                partnerApproved = item.optBoolean("partnerApproved"),
            )
        }
    }.getOrDefault(emptyMap())

    /** Si los métodos incluyen al compañero, hace falta uno vinculado para poder quitar el candado. */
    fun needsPartner(profileId: Long) = _locks.value[profileId]?.exitMethods?.any { it is ExitMethod.Partner } == true

    private companion object {
        const val KEY_LOCKS = "locks"
        const val KEY_REQUESTS = "requests"
    }
}
