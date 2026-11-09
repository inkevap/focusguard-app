package com.focusguard.data

import android.content.Context
import com.focusguard.model.ExitMethod
import com.focusguard.model.ExitRequest
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.StrictAction
import com.focusguard.model.StrictConfig
import com.focusguard.model.StrictProtection
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Configuración del modo estricto, solicitud de salida en curso y PIN (con sal y hash). */
class StrictRepository(context: Context) {

    private val prefs = context.getSharedPreferences("strict", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<StrictConfig> = _config.asStateFlow()

    private val _request = MutableStateFlow(readRequest())
    val request: StateFlow<ExitRequest?> = _request.asStateFlow()

    fun saveConfig(config: StrictConfig) {
        prefs.edit().putString(KEY_CONFIG, config.toJson().toString()).apply()
        _config.value = config
    }

    fun saveRequest(request: ExitRequest?) {
        prefs.edit().apply {
            if (request == null) remove(KEY_REQUEST) else putString(KEY_REQUEST, request.toJson().toString())
        }.apply()
        _request.value = request
    }

    /** El compañero aprobó la salida pedida: si hay una solicitud en curso de esa acción, la marca. Devuelve false si ya no existe. */
    fun markPartnerApproved(action: StrictAction): Boolean {
        val current = _request.value?.takeIf { it.action == action } ?: return false
        saveRequest(current.copy(partnerApproved = true))
        return true
    }

    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH)

    /** Guarda el PIN (4 a 8 dígitos). Devuelve false si no es válido. */
    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.getEncoder().encodeToString(salt))
            .putString(KEY_PIN_HASH, hash(pin, salt))
            .apply()
        return true
    }

    fun verifyPin(pin: String): Boolean {
        val saltText = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val actual = hash(pin, Base64.getDecoder().decode(saltText))
        return MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return Base64.getEncoder().encodeToString(digest.digest(pin.toByteArray()))
    }

    private fun readConfig(): StrictConfig =
        prefs.getString(KEY_CONFIG, null)?.let { runCatching { configFromJson(JSONObject(it)) }.getOrNull() } ?: StrictConfig()

    private fun readRequest(): ExitRequest? =
        prefs.getString(KEY_REQUEST, null)?.let { runCatching { requestFromJson(JSONObject(it)) }.getOrNull() }

    companion object {
        private const val KEY_CONFIG = "config"
        private const val KEY_REQUEST = "request"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"

        fun isValidPin(pin: String) = pin.length in 4..8 && pin.all(Char::isDigit)

        internal fun StrictConfig.toJson() = JSONObject().apply {
            put("enabled", enabled)
            put("pausedUntilMs", pausedUntil?.toEpochMilli() ?: JSONObject.NULL)
            put(
                "intervals",
                JSONArray().also { array ->
                    intervals.forEach {
                        array.put(JSONObject().put("day", it.day.value).put("start", it.startMinute).put("end", it.endMinute))
                    }
                },
            )
            put("protections", JSONArray(protections.map { it.name }))
            put(
                "exitMethods",
                JSONArray().also { array ->
                    exitMethods.forEach { array.put(it.toJson()) }
                },
            )
        }

        internal fun configFromJson(json: JSONObject) = StrictConfig(
            enabled = json.optBoolean("enabled"),
            pausedUntil = if (json.isNull("pausedUntilMs")) null else Instant.ofEpochMilli(json.getLong("pausedUntilMs")),
            intervals = json.getJSONArray("intervals").let { array ->
                (0 until array.length()).map {
                    val item = array.getJSONObject(it)
                    ScheduleInterval(DayOfWeek.of(item.getInt("day")), item.getInt("start"), item.getInt("end"))
                }
            },
            protections = json.getJSONArray("protections").let { array ->
                (0 until array.length()).mapTo(mutableSetOf()) { StrictProtection.valueOf(array.getString(it)) }
            },
            exitMethods = json.getJSONArray("exitMethods").let { array ->
                (0 until array.length()).map { exitMethodFromJson(array.getJSONObject(it)) }
            },
        )

        internal fun ExitMethod.toJson() = when (this) {
            is ExitMethod.Timer -> JSONObject().put("type", "TIMER").put("minutes", minutes)
            is ExitMethod.Cooldown -> JSONObject().put("type", "COOLDOWN").put("minutes", minutes)
            ExitMethod.FollowSchedule -> JSONObject().put("type", "FOLLOW_SCHEDULE")
            ExitMethod.Charger -> JSONObject().put("type", "CHARGER")
            ExitMethod.Pin -> JSONObject().put("type", "PIN")
            ExitMethod.Partner -> JSONObject().put("type", "PARTNER")
        }

        internal fun exitMethodFromJson(json: JSONObject): ExitMethod = when (json.getString("type")) {
            "TIMER" -> ExitMethod.Timer(json.getInt("minutes"))
            "COOLDOWN" -> ExitMethod.Cooldown(json.getInt("minutes"))
            "FOLLOW_SCHEDULE" -> ExitMethod.FollowSchedule
            "CHARGER" -> ExitMethod.Charger
            "PIN" -> ExitMethod.Pin
            "PARTNER" -> ExitMethod.Partner
            else -> error("método de salida desconocido")
        }

        internal fun ExitRequest.toJson() = JSONObject().apply {
            put("requestedAtMs", requestedAt.toEpochMilli())
            put("partnerApproved", partnerApproved)
            when (val a = action) {
                is StrictAction.Pause -> put("action", "PAUSE").put("minutes", a.minutes)
                StrictAction.Disable -> put("action", "DISABLE")
            }
        }

        internal fun requestFromJson(json: JSONObject) = ExitRequest(
            action = if (json.getString("action") == "PAUSE") StrictAction.Pause(json.getInt("minutes")) else StrictAction.Disable,
            requestedAt = Instant.ofEpochMilli(json.getLong("requestedAtMs")),
            partnerApproved = json.optBoolean("partnerApproved"),
        )
    }
}
