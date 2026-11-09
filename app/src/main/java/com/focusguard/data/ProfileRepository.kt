package com.focusguard.data

import com.focusguard.model.BlockMode
import com.focusguard.model.PomodoroPlan
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.model.ConditionMode
import com.focusguard.model.EndConditions
import com.focusguard.model.EndPolicy
import com.focusguard.model.GeoFence
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.LimitScope
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule
import com.focusguard.decision.ProfileActivation
import java.time.DayOfWeek
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepository(private val dao: ProfileDao) {

    val profiles: Flow<List<Profile>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun get(id: Long): Profile? = dao.get(id)?.toModel()

    /** Guarda y devuelve el id (nuevo si [Profile.id] es 0). */
    suspend fun save(profile: Profile): Long = dao.save(
        profile = ProfileEntity(
            id = profile.id,
            title = profile.title,
            emoji = profile.emoji,
            enabled = profile.enabled,
            mode = profile.mode.name,
            pausedUntilMs = profile.pausedUntil?.toEpochMilli(),
            limitKind = profile.limit?.kind?.name,
            limitAmount = profile.limit?.amount,
            limitPeriod = profile.limit?.period?.name,
            limitScope = profile.limit?.scope?.name,
            kind = profile.kind.name,
            activeUntilMs = profile.activeUntil?.toEpochMilli(),
            pomodoroStartMs = profile.pomodoro?.startedAt?.toEpochMilli(),
            pomodoroFocusMin = profile.pomodoro?.focusMinutes,
            pomodoroBreakMin = profile.pomodoro?.breakMinutes,
            pomodoroSessions = profile.pomodoro?.sessions,
            conditionMode = profile.conditionMode.name,
            requireCharging = profile.requireCharging,
            geoLat = profile.geofence?.latitude,
            geoLon = profile.geofence?.longitude,
            geoRadiusM = profile.geofence?.radiusMeters,
            geoInverted = profile.geofence?.inverted ?: false,
            subApps = profile.subApps.sorted().joinToString(","),
            categories = profile.categories.sorted().joinToString(","),
            bluetooth = profile.bluetoothNames.sorted().joinToString("\n"),
            endPolicy = profile.endPolicy.name,
            endWifi = profile.endConditions.wifiNames.sorted().joinToString("\n"),
            endBluetooth = profile.endConditions.bluetoothNames.sorted().joinToString("\n"),
            endCharging = profile.endConditions.charging,
            endLat = profile.endConditions.place?.latitude,
            endLon = profile.endConditions.place?.longitude,
            endRadiusM = profile.endConditions.place?.radiusMeters,
            endWaitUntilMs = profile.endConditions.waitUntil?.toEpochMilli(),
        ),
        apps = profile.apps.toList(),
        webs = profile.webs.map { ProfileWebEntity(profileId = 0, value = it.value, matchType = it.matchType.name) },
        intervals = profile.intervals.map {
            ProfileIntervalEntity(profileId = 0, day = it.day.value, startMinute = it.startMinute, endMinute = it.endMinute)
        },
        wifis = profile.wifiNames.toList(),
    )

    /** Borra el perfil; false si es un bloqueo de pánico en curso, que solo desaparece al caducar. */
    suspend fun delete(id: Long): Boolean {
        if (get(id)?.canEndEarly == false) return false
        dao.delete(id)
        return true
    }

    /** Borra los perfiles temporales (Quick Block, Pomodoro) que ya caducaron. Devuelve los borrados. */
    suspend fun purgeExpired(now: Instant): List<Profile> {
        val expired = dao.getAll().map { it.toModel() }
            .filter { it.kind != ProfileKind.NORMAL && ProfileActivation.isExpired(it, now) }
        expired.forEach { dao.delete(it.id) }
        return expired
    }

    /** Levanta un Bloquear ya porque se cumplió una de sus salidas (lugar, cargador, Wi-Fi, Bluetooth). Es el único camino que ignora [Profile.canEndEarly]. */
    suspend fun endByCondition(id: Long) = dao.delete(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val profile = get(id) ?: return
        if (!enabled && !profile.canEndEarly) return
        save(profile.copy(enabled = enabled))
    }
}

private fun ProfileWithDetails.toModel() = Profile(
    id = profile.id,
    title = profile.title,
    emoji = profile.emoji,
    enabled = profile.enabled,
    mode = BlockMode.valueOf(profile.mode),
    apps = apps.map { it.packageName }.toSet(),
    webs = webs.map { WebRule(it.value, WebMatchType.valueOf(it.matchType)) },
    intervals = intervals.map { ScheduleInterval(DayOfWeek.of(it.day), it.startMinute, it.endMinute) },
    pausedUntil = profile.pausedUntilMs?.let(Instant::ofEpochMilli),
    limit = if (profile.limitKind != null && profile.limitAmount != null) {
        UsageLimit(
            LimitKind.valueOf(profile.limitKind),
            profile.limitAmount,
            LimitPeriod.valueOf(profile.limitPeriod ?: LimitPeriod.DAILY.name),
            profile.limitScope?.let(LimitScope::valueOf) ?: LimitScope.SHARED,
        )
    } else {
        null
    },
    kind = ProfileKind.valueOf(profile.kind),
    activeUntil = profile.activeUntilMs?.let(Instant::ofEpochMilli),
    pomodoro = profile.pomodoroStartMs?.let {
        PomodoroPlan(
            startedAt = Instant.ofEpochMilli(it),
            focusMinutes = profile.pomodoroFocusMin ?: 25,
            breakMinutes = profile.pomodoroBreakMin ?: 5,
            sessions = profile.pomodoroSessions ?: 4,
        )
    },
    wifiNames = wifis.map { it.ssid }.toSet(),
    requireCharging = profile.requireCharging,
    geofence = if (profile.geoLat != null && profile.geoLon != null) {
        GeoFence(profile.geoLat, profile.geoLon, profile.geoRadiusM ?: GeoFence.DEFAULT_RADIUS_METERS, profile.geoInverted)
    } else {
        null
    },
    conditionMode = ConditionMode.valueOf(profile.conditionMode),
    subApps = profile.subApps.split(',').filter { it.isNotBlank() }.toSet(),
    categories = profile.categories.split(',').filter { it.isNotBlank() }.toSet(),
    bluetoothNames = profile.bluetooth.split('\n').filter { it.isNotBlank() }.toSet(),
    endPolicy = EndPolicy.valueOf(profile.endPolicy),
    endConditions = EndConditions(
        place = if (profile.endLat != null && profile.endLon != null) {
            GeoFence(profile.endLat, profile.endLon, profile.endRadiusM ?: GeoFence.DEFAULT_RADIUS_METERS)
        } else {
            null
        },
        charging = profile.endCharging,
        wifiNames = profile.endWifi.split('\n').filter { it.isNotBlank() }.toSet(),
        bluetoothNames = profile.endBluetooth.split('\n').filter { it.isNotBlank() }.toSet(),
        waitUntil = profile.endWaitUntilMs?.let(Instant::ofEpochMilli),
    ),
)
