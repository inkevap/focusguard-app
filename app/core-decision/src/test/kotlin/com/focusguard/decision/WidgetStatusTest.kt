package com.focusguard.decision

import com.focusguard.model.LimitKind
import com.focusguard.model.PomodoroPlan
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetStatusTest {

    /** Lunes 26 de octubre de 2026, 10:00. */
    private val now = Instant.parse("2026-10-26T10:00:00Z")

    private fun status(vararg profiles: Profile, at: Instant = now) = WidgetStatus.of(profiles.toList(), at, ZoneOffset.UTC)

    private fun work(vararg intervals: ScheduleInterval, title: String = "Trabajo", id: Long = 1) =
        Profile(id, title, intervals = intervals.toList())

    @Test
    fun sinPerfilesNoHayNada() {
        val s = status()
        assertNull(s.lock)
        assertEquals(0, s.activeCount)
        assertNull(s.nextStart)
        assertTrue(s.activeDays.isEmpty())
        assertNull(s.refreshAt)
    }

    @Test
    fun elPanicSeMuestraConSuFinal() {
        val until = now.plusSeconds(1800)
        val s = status(Profile.panic(until, "Dormir").copy(id = 9))
        assertEquals(WidgetStatus.Lock("Dormir", until), s.lock)
        assertEquals(0, s.activeCount)
    }

    @Test
    fun conUnBloqueoSinSalidaSeRepintaCadaMinutoYAlAcabar() {
        val lejos = status(Profile.panic(now.plusSeconds(1800), "Dormir").copy(id = 9))
        assertEquals(Instant.parse("2026-10-26T10:01:00Z"), lejos.refreshAt)
        // Si acaba antes del próximo minuto, se repinta justo al acabar y no un minuto después.
        val until = now.plusSeconds(20)
        assertEquals(until, status(Profile.panic(until, "Dormir").copy(id = 9)).refreshAt)
    }

    @Test
    fun unPanicCaducadoNoCuenta() {
        assertNull(status(Profile.panic(now.minusSeconds(5)).copy(id = 9)).lock)
    }

    @Test
    fun cuentaLosPerfilesActivosAhoraYDaElFinDelHorario() {
        val activo = work(ScheduleInterval(DayOfWeek.MONDAY, 9 * 60, 12 * 60))
        val s = status(activo)
        assertEquals(listOf("Trabajo"), s.activeTitles)
        assertNull(s.nextStart)
        assertEquals(Instant.parse("2026-10-26T12:00:00Z"), s.refreshAt)
    }

    @Test
    fun elSiguienteHorarioEsElMasCercano() {
        val tarde = work(ScheduleInterval(DayOfWeek.MONDAY, 20 * 60, 22 * 60), title = "Noche", id = 1)
        val manana = work(ScheduleInterval(DayOfWeek.TUESDAY, 8 * 60, 9 * 60), title = "Mañana", id = 2)
        val s = status(manana, tarde)
        assertEquals(WidgetStatus.Upcoming("Noche", Instant.parse("2026-10-26T20:00:00Z")), s.nextStart)
        assertEquals(s.nextStart?.at, s.refreshAt)
    }

    @Test
    fun elSiguienteHorarioDaLaVueltaALaSemana() {
        val s = status(work(ScheduleInterval(DayOfWeek.MONDAY, 8 * 60, 9 * 60)))
        assertEquals(Instant.parse("2026-11-02T08:00:00Z"), s.nextStart?.at)
    }

    @Test
    fun unPerfilEnPausaNoEsElSiguiente() {
        val pausado = work(ScheduleInterval(DayOfWeek.MONDAY, 20 * 60, 22 * 60)).copy(pausedUntil = now.plusSeconds(3600))
        val s = status(pausado)
        assertNull(s.nextStart)
        assertEquals(now.plusSeconds(3600), s.refreshAt)
    }

    @Test
    fun unPerfilApagadoNoCuentaParaNada() {
        val s = status(work(ScheduleInterval(DayOfWeek.MONDAY, 9 * 60, 12 * 60)).copy(enabled = false))
        assertEquals(0, s.activeCount)
        assertNull(s.nextStart)
        assertTrue(s.activeDays.isEmpty())
    }

    @Test
    fun losDiasActivosSalenDeLosHorarios() {
        val s = status(
            work(ScheduleInterval(DayOfWeek.MONDAY, 9 * 60, 12 * 60), ScheduleInterval(DayOfWeek.WEDNESDAY, 9 * 60, 12 * 60)),
        )
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), s.activeDays)
    }

    @Test
    fun unHorarioQueCruzaLaMedianocheTocaDosDias() {
        val s = status(work(ScheduleInterval(DayOfWeek.FRIDAY, 22 * 60, 6 * 60)))
        assertEquals(setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), s.activeDays)
        val domingo = status(work(ScheduleInterval(DayOfWeek.SUNDAY, 22 * 60, 6 * 60)))
        assertEquals(setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY), domingo.activeDays)
    }

    @Test
    fun unPerfilSinHorarioCuentaTodosLosDias() {
        assertEquals(DayOfWeek.values().toSet(), status(Profile(1, "Siempre")).activeDays)
    }

    @Test
    fun losBloqueosRapidosCuentanComoActivosPeroNoMarcanDias() {
        val rapido = Profile(3, "Bloquear ya", kind = ProfileKind.QUICK_BLOCK, activeUntil = now.plusSeconds(600))
        val s = status(rapido)
        assertEquals(listOf("Bloquear ya"), s.activeTitles)
        assertTrue(s.activeDays.isEmpty())
        assertEquals(now.plusSeconds(600), s.refreshAt)
    }

    @Test
    fun lasCondicionesDelTelefonoSeDanPorCumplidasYMandaElHorario() {
        val enElCargador = work(ScheduleInterval(DayOfWeek.MONDAY, 9 * 60, 12 * 60)).copy(requireCharging = true, wifiNames = setOf("Casa"))
        assertEquals(listOf("Trabajo"), status(enElCargador).activeTitles)
        val fueraDeHorario = work(ScheduleInterval(DayOfWeek.TUESDAY, 9 * 60, 12 * 60)).copy(requireCharging = true)
        assertEquals(0, status(fueraDeHorario).activeCount)
    }

    @Test
    fun unPerfilConLimiteDeUsoNoCuentaComoActivo() {
        val limite = Profile(1, "Instagram 30 min", limit = UsageLimit(LimitKind.TIME, 30))
        assertEquals(0, status(limite).activeCount)
    }

    @Test
    fun unPerfilTemporalCaducadoNoAnunciaSiguienteHorario() {
        val caducado = work(ScheduleInterval(DayOfWeek.MONDAY, 20 * 60, 22 * 60)).copy(activeUntil = now.minusSeconds(60))
        assertNull(status(caducado).nextStart)
    }

    @Test
    fun elPomodoroAvisaDelCambioDeFase() {
        val pomodoro = Profile(5, "Enfoque", kind = ProfileKind.POMODORO, pomodoro = PomodoroPlan(now.minusSeconds(10 * 60), 25, 5, 4))
        val s = status(pomodoro)
        assertEquals(listOf("Enfoque"), s.activeTitles)
        assertEquals(now.plusSeconds(15 * 60), s.refreshAt)
    }
}
