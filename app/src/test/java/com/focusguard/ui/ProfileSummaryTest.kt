package com.focusguard.ui

import com.focusguard.model.BlockMode
import com.focusguard.model.ConditionMode
import com.focusguard.model.GeoFence
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebRule
import com.focusguard.ui.profiles.conditionsSummary
import com.focusguard.ui.profiles.contentSummary
import com.focusguard.ui.profiles.dayRangeLabel
import com.focusguard.ui.profiles.limitSummary
import com.focusguard.ui.profiles.scheduleSummary
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileSummaryTest {

    @Test
    fun losDiasSeguidosSeAbrevian() {
        assertEquals("Lun a vie", dayRangeLabel(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)))
        assertEquals("Sáb, Dom", dayRangeLabel(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)))
        assertEquals("Lun, Mié, Vie", dayRangeLabel(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        assertEquals("Todos los días", dayRangeLabel(DayOfWeek.entries.toSet()))
    }

    @Test
    fun elHorarioSeLeeComoUnaFrase() {
        val weekdays = (DayOfWeek.MONDAY.value..DayOfWeek.FRIDAY.value).map { ScheduleInterval(DayOfWeek.of(it), 9 * 60, 17 * 60) }
        assertEquals("Lun a vie · 9:00–17:00", scheduleSummary(weekdays))
        assertEquals("Siempre activo", scheduleSummary(emptyList()))
        assertEquals("Todos los días · 23:00–7:00", scheduleSummary(DayOfWeek.entries.map { ScheduleInterval(it, 23 * 60, 7 * 60) }))
        assertEquals("Lun · Todo el día", scheduleSummary(listOf(ScheduleInterval(DayOfWeek.MONDAY, 0, 24 * 60))))
    }

    @Test
    fun lasSeccionesDentroDeAppsCuentanEnElResumen() {
        val profile = Profile(1, "p", apps = setOf("a"), subApps = setOf("YOUTUBE_SHORTS", "INSTAGRAM_REELS"))
        assertEquals("Bloquea 1 app y 2 secciones", contentSummary(profile))
    }

    @Test
    fun lasCategoriasCuentanEnElResumen() {
        assertEquals("Bloquea 2 categorías", contentSummary(Profile(1, "p", categories = setOf("social", "video"))))
    }

    @Test
    fun lasCondicionesSeLeenComoUnaFrase() {
        val base = Profile(id = 1, title = "Noche")
        assertNull(conditionsSummary(base))
        assertEquals("Con la red Casa", conditionsSummary(base.copy(wifiNames = setOf("Casa"))))
        assertEquals("Con Coche conectado", conditionsSummary(base.copy(bluetoothNames = setOf("Coche"))))
        assertEquals("Con 2 dispositivos Bluetooth y cargando", conditionsSummary(base.copy(bluetoothNames = setOf("A", "B"), requireCharging = true)))
        assertEquals("Con 2 redes Wi-Fi y cargando", conditionsSummary(base.copy(wifiNames = setOf("A", "B"), requireCharging = true)))
        assertEquals(
            "Cargando o en un lugar",
            conditionsSummary(base.copy(requireCharging = true, geofence = GeoFence(40.0, -3.7), conditionMode = ConditionMode.ANY)),
        )
        assertEquals("Fuera de un lugar", conditionsSummary(base.copy(geofence = GeoFence(40.0, -3.7, inverted = true))))
    }

    @Test
    fun conMuchosHorariosDistintosSoloSeCuentan() {
        val many = listOf(
            ScheduleInterval(DayOfWeek.MONDAY, 8 * 60, 9 * 60),
            ScheduleInterval(DayOfWeek.TUESDAY, 10 * 60, 11 * 60),
            ScheduleInterval(DayOfWeek.WEDNESDAY, 12 * 60, 13 * 60),
        )
        assertEquals("3 horarios distintos", scheduleSummary(many))
    }

    @Test
    fun elContenidoDiceQueHaceElPerfil() {
        assertEquals("Bloquea 4 apps y 2 webs", contentSummary(Profile(1, "p", apps = setOf("a", "b", "c", "d"), webs = listOf(WebRule("x.com"), WebRule("y.com")))))
        assertEquals("Bloquea 1 app", contentSummary(Profile(1, "p", apps = setOf("a"))))
        assertEquals("Permite solo 1 web", contentSummary(Profile(1, "p", mode = BlockMode.ALLOW_ONLY, webs = listOf(WebRule("x.com")))))
        assertEquals("Bloquea: aún nada elegido", contentSummary(Profile(1, "p")))
    }

    @Test
    fun losLimitesSeLeenEnClaro() {
        assertEquals("Límite: 1 h al día", limitSummary(UsageLimit(LimitKind.TIME, 60)))
        assertEquals("Límite: 1 h 30 min a la semana", limitSummary(UsageLimit(LimitKind.TIME, 90, LimitPeriod.WEEKLY)))
        assertEquals("Límite: 30 min por hora", limitSummary(UsageLimit(LimitKind.TIME, 30, LimitPeriod.HOURLY)))
        assertEquals("Límite: 3 aperturas al día", limitSummary(UsageLimit(LimitKind.OPENS, 3)))
        assertEquals("Límite: 20 min seguidos", limitSummary(UsageLimit(LimitKind.CONTINUOUS, 20)))
        assertNull(limitSummary(null))
    }
}
