package com.focusguard.data

import com.focusguard.data.StrictRepository.Companion.toJson
import com.focusguard.decision.BackupSettings
import com.focusguard.decision.BackupSnapshot
import com.focusguard.decision.EmbeddedBrowserPolicy
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import org.json.JSONArray
import org.json.JSONObject

/** La copia de seguridad ⇄ JSON, en un solo sitio. Leer valida todo antes de que nadie aplique nada. */
internal object BackupJson {
    /** Versión del esquema. La 1 (solo perfiles y dos ajustes) se sigue leyendo. */
    const val VERSION = 2

    /** Solo perfiles normales y duraderos: un bloqueo temporal (sesión, Pomodoro) ya no vale en otro momento ni en otro teléfono. */
    fun isPortable(profile: Profile) = profile.kind == ProfileKind.NORMAL && profile.activeUntil == null && profile.pomodoro == null

    fun toJson(snapshot: BackupSnapshot, includePlaces: Boolean, exportedAtMs: Long): JSONObject = JSONObject().apply {
        val settings = snapshot.settings
        put("version", VERSION)
        put("exportedAtMs", exportedAtMs)
        put(
            "settings",
            JSONObject().apply {
                settings.dayStartMinute?.let { put("dayStartMinute", it) }
                put("adultBlock", settings.adultBlock)
                put("adultWords", JSONArray(settings.adultWords.sorted()))
                settings.newApps?.let { put("newApps", it.encode()) }
                settings.embeddedBrowsers?.let { put("embeddedBrowsers", it.encode()) }
                put("foldFloating", settings.foldFloating)
            },
        )
        snapshot.lockStyle?.let { put("lockStyle", LockStyleJson.toJson(it)) }
        snapshot.strict?.let { put("strict", it.toJson()) }
        put("profiles", JSONArray().also { array -> snapshot.profiles.forEach { array.put(ProfileJson.toJson(it, includePlaces)) } })
    }

    /** Lanza `IllegalStateException` con un mensaje claro si el texto no es una copia válida; si no, la copia entera ya leída. */
    fun fromJson(text: String): BackupSnapshot {
        val json = try { JSONObject(text) } catch (e: Exception) { error("El archivo no es un JSON válido") }
        if (json.optInt("version") !in 1..VERSION) error("Versión de archivo no compatible")
        val array = json.optJSONArray("profiles") ?: error("El archivo no contiene perfiles")
        val profiles = (0 until array.length()).map { index ->
            runCatching { ProfileJson.fromJson(array.getJSONObject(index)) }.getOrElse { error("Perfil ${index + 1} no válido") }
        }.filter(::isPortable)
        return BackupSnapshot(
            settings = json.optJSONObject("settings")?.let(::settings) ?: BackupSettings(),
            lockStyle = json.optJSONObject("lockStyle")?.let { runCatching { LockStyleJson.fromJson(it) }.getOrNull() },
            strict = json.optJSONObject("strict")?.let { runCatching { StrictRepository.configFromJson(it) }.getOrNull() },
            profiles = profiles,
        )
    }

    private fun settings(json: JSONObject) = BackupSettings(
        dayStartMinute = if (json.has("dayStartMinute")) json.optInt("dayStartMinute") else null,
        adultBlock = json.optBoolean("adultBlock"),
        adultWords = json.stringSet("adultWords"),
        newApps = if (json.has("newApps")) NewAppsPolicy.decode(json.optString("newApps")) else null,
        embeddedBrowsers = if (json.has("embeddedBrowsers")) EmbeddedBrowserPolicy.decode(json.optString("embeddedBrowsers")) else null,
        foldFloating = json.optBoolean("foldFloating"),
    )
}
