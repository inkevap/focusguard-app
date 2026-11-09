package com.focusguard.data

import com.focusguard.data.StrictRepository.Companion.configFromJson
import com.focusguard.data.StrictRepository.Companion.requestFromJson
import com.focusguard.data.StrictRepository.Companion.toJson
import com.focusguard.model.BlockMode
import com.focusguard.model.ConditionMode
import com.focusguard.model.GeoFence
import com.focusguard.model.ExitMethod
import com.focusguard.model.StrictAction
import com.focusguard.model.ExitRequest
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.PomodoroPlan
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.StrictConfig
import com.focusguard.model.StrictProtection
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule
import java.time.DayOfWeek
import java.time.Instant
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonRoundTripTest {

    private fun roundTrip(profile: Profile) = ProfileJson.fromJson(JSONObject(ProfileJson.toJson(profile).toString()))

    @Test
    fun perfilCompletoSobreviveAlViajeDeIdaYVuelta() {
        val profile = Profile(
            id = 7,
            title = "Estudio",
            emoji = "📚",
            enabled = false,
            mode = BlockMode.ALLOW_ONLY,
            apps = linkedSetOf("com.notes", "com.docs"),
            webs = listOf(WebRule("wikipedia.org", WebMatchType.EXACT), WebRule("curso", WebMatchType.CONTAINS)),
            intervals = listOf(ScheduleInterval(DayOfWeek.MONDAY, 22 * 60, 6 * 60), ScheduleInterval(DayOfWeek.SUNDAY, 0, 24 * 60)),
            pausedUntil = Instant.parse("2026-10-26T10:00:00Z"),
            limit = UsageLimit(LimitKind.OPENS, 5, LimitPeriod.WEEKLY, com.focusguard.model.LimitScope.PER_TARGET),
            kind = ProfileKind.POMODORO,
            activeUntil = Instant.parse("2026-10-26T11:00:00Z"),
            pomodoro = PomodoroPlan(Instant.parse("2026-10-26T09:00:00Z"), 25, 5, 4),
        )
        assertEquals(profile, roundTrip(profile))
    }

    @Test
    fun laCopiaDeSeguridadNoLlevaRedesNiLugar() {
        val profile = Profile(id = 3, title = "Casa", wifiNames = setOf("Casa"), bluetoothNames = setOf("Coche"), geofence = GeoFence(40.4168, -3.7038), requireCharging = true)
        val exported = ProfileJson.fromJson(JSONObject(ProfileJson.toJson(profile, includePlaces = false).toString()))
        assertEquals(emptySet(), exported.wifiNames)
        assertEquals(emptySet(), exported.bluetoothNames)
        assertEquals(null, exported.geofence)
        assertEquals(true, exported.requireCharging)
        assertEquals(profile, roundTrip(profile))
    }

    @Test
    fun lasCategoriasSobrevivenAlViajeDeIdaYVuelta() {
        val profile = Profile(id = 5, title = "Sin redes", categories = linkedSetOf("social", "video"))
        assertEquals(profile, roundTrip(profile))
    }

    @Test
    fun lasSeccionesDentroDeAppsSobrevivenAlViajeDeIdaYVuelta() {
        val profile = Profile(id = 4, title = "Sin vídeos cortos", subApps = linkedSetOf("YOUTUBE_SHORTS", "INSTAGRAM_REELS"))
        assertEquals(profile, roundTrip(profile))
    }

    @Test
    fun lasCondicionesSobrevivenAlViajeDeIdaYVuelta() {
        val profile = Profile(
            id = 3,
            title = "Casa",
            wifiNames = linkedSetOf("Casa", "Oficina 5G"),
            bluetoothNames = linkedSetOf("Coche", "Auriculares de Ana"),
            requireCharging = true,
            geofence = GeoFence(40.4168, -3.7038, radiusMeters = 200, inverted = true),
            conditionMode = ConditionMode.ANY,
        )
        assertEquals(profile, roundTrip(profile))
    }

    @Test
    fun perfilMinimoUsaLosValoresPorDefecto() {
        val minimal = JSONObject().put("title", "Solo título")
        assertEquals(Profile(id = 0, title = "Solo título"), ProfileJson.fromJson(minimal))
    }

    @Test
    fun perfilSinTituloEsInvalido() {
        assertFailsWith<org.json.JSONException> { ProfileJson.fromJson(JSONObject()) }
    }

    @Test
    fun configuracionDelModoEstrictoSobreviveAlViaje() {
        val config = StrictConfig(
            enabled = true,
            intervals = listOf(ScheduleInterval(DayOfWeek.FRIDAY, 20 * 60, 2 * 60)),
            protections = setOf(StrictProtection.EDIT_LOCK, StrictProtection.RECENTS),
            exitMethods = listOf(ExitMethod.Timer(10), ExitMethod.Cooldown(30), ExitMethod.FollowSchedule, ExitMethod.Charger, ExitMethod.Pin, ExitMethod.Partner),
            pausedUntil = Instant.parse("2026-10-26T10:00:00Z"),
        )
        assertEquals(config, configFromJson(JSONObject(config.toJson().toString())))
        assertEquals(StrictConfig(), configFromJson(JSONObject(StrictConfig().toJson().toString())))
    }

    @Test
    fun laSolicitudDeSalidaRecuerdaLaAprobacionDelCompanero() {
        val at = Instant.parse("2026-10-26T10:00:00Z")
        listOf(
            ExitRequest(StrictAction.Disable, at),
            ExitRequest(StrictAction.Pause(15), at, partnerApproved = true),
        ).forEach { assertEquals(it, requestFromJson(JSONObject(it.toJson().toString()))) }
        // Una solicitud guardada por una versión anterior no traía el campo: no está aprobada.
        val old = JSONObject().put("action", "DISABLE").put("requestedAtMs", at.toEpochMilli())
        assertEquals(false, requestFromJson(old).partnerApproved)
    }
}
