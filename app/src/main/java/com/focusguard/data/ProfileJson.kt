package com.focusguard.data

import com.focusguard.model.BlockMode
import com.focusguard.model.ConditionMode
import com.focusguard.model.GeoFence
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.LimitScope
import com.focusguard.model.PomodoroPlan
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule
import java.time.DayOfWeek
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Perfil ⇄ JSON. Lo usan las aprobaciones pendientes y la exportación e importación. */
object ProfileJson {

    /** Con [includePlaces] en false no salen las redes Wi-Fi ni el lugar: identifican dónde vive el usuario y no deben acabar en un archivo que se comparte (tampoco los dispositivos Bluetooth). */
    fun toJson(profile: Profile, includePlaces: Boolean = true): JSONObject = JSONObject().apply {
        put("id", profile.id)
        put("title", profile.title)
        put("emoji", profile.emoji)
        put("enabled", profile.enabled)
        put("mode", profile.mode.name)
        put("kind", profile.kind.name)
        put("apps", JSONArray(profile.apps.sorted()))
        put("webs", JSONArray().also { array ->
            profile.webs.forEach { array.put(JSONObject().put("value", it.value).put("type", it.matchType.name)) }
        })
        put("intervals", JSONArray().also { array ->
            profile.intervals.forEach {
                array.put(JSONObject().put("day", it.day.value).put("start", it.startMinute).put("end", it.endMinute))
            }
        })
        profile.pausedUntil?.let { put("pausedUntilMs", it.toEpochMilli()) }
        profile.activeUntil?.let { put("activeUntilMs", it.toEpochMilli()) }
        profile.limit?.let {
            put("limit", JSONObject().put("kind", it.kind.name).put("amount", it.amount).put("period", it.period.name).put("scope", it.scope.name))
        }
        if (includePlaces && profile.wifiNames.isNotEmpty()) put("wifiNames", JSONArray(profile.wifiNames.sorted()))
        if (includePlaces && profile.bluetoothNames.isNotEmpty()) put("bluetoothNames", JSONArray(profile.bluetoothNames.sorted()))
        if (profile.requireCharging) put("requireCharging", true)
        if (profile.subApps.isNotEmpty()) put("subApps", JSONArray(profile.subApps.sorted()))
        if (profile.categories.isNotEmpty()) put("categories", JSONArray(profile.categories.sorted()))
        put("conditionMode", profile.conditionMode.name)
        profile.geofence?.takeIf { includePlaces }?.let {
            put("geofence", JSONObject().put("lat", it.latitude).put("lon", it.longitude).put("radius", it.radiusMeters).put("inverted", it.inverted))
        }
        profile.pomodoro?.let {
            put("pomodoro", JSONObject()
                .put("startMs", it.startedAt.toEpochMilli())
                .put("focus", it.focusMinutes).put("break", it.breakMinutes).put("sessions", it.sessions))
        }
    }

    fun fromJson(json: JSONObject): Profile = Profile(
        id = json.optLong("id", 0),
        title = json.getString("title"),
        emoji = json.optString("emoji", ""),
        enabled = json.optBoolean("enabled", true),
        mode = BlockMode.valueOf(json.optString("mode", BlockMode.BLOCK.name)),
        kind = ProfileKind.valueOf(json.optString("kind", ProfileKind.NORMAL.name)),
        apps = json.stringSet("apps"),
        webs = json.optJSONArray("webs")?.let { a ->
            (0 until a.length()).map {
                val item = a.getJSONObject(it)
                WebRule(item.getString("value"), WebMatchType.valueOf(item.getString("type")))
            }
        } ?: emptyList(),
        intervals = json.optJSONArray("intervals")?.let { a ->
            (0 until a.length()).map {
                val item = a.getJSONObject(it)
                ScheduleInterval(DayOfWeek.of(item.getInt("day")), item.getInt("start"), item.getInt("end"))
            }
        } ?: emptyList(),
        pausedUntil = if (json.has("pausedUntilMs")) Instant.ofEpochMilli(json.getLong("pausedUntilMs")) else null,
        activeUntil = if (json.has("activeUntilMs")) Instant.ofEpochMilli(json.getLong("activeUntilMs")) else null,
        limit = json.optJSONObject("limit")?.let {
            UsageLimit(
                LimitKind.valueOf(it.getString("kind")), it.getInt("amount"), LimitPeriod.valueOf(it.getString("period")),
                LimitScope.valueOf(it.optString("scope", LimitScope.SHARED.name)),
            )
        },
        wifiNames = json.stringSet("wifiNames"),
        bluetoothNames = json.stringSet("bluetoothNames"),
        requireCharging = json.optBoolean("requireCharging", false),
        subApps = json.stringSet("subApps"),
        categories = json.stringSet("categories"),
        conditionMode = ConditionMode.valueOf(json.optString("conditionMode", ConditionMode.ALL.name)),
        geofence = json.optJSONObject("geofence")?.let {
            GeoFence(it.getDouble("lat"), it.getDouble("lon"), it.getInt("radius"), it.optBoolean("inverted", false))
        },
        pomodoro = json.optJSONObject("pomodoro")?.let {
            PomodoroPlan(Instant.ofEpochMilli(it.getLong("startMs")), it.getInt("focus"), it.getInt("break"), it.getInt("sessions"))
        },
    )
}
