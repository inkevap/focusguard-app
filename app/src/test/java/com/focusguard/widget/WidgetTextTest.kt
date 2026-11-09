package com.focusguard.widget

import com.focusguard.decision.WidgetStatus
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetTextTest {

    private val now = Instant.parse("2026-10-26T10:00:00Z")
    private val zone = ZoneOffset.UTC

    private fun status(
        lock: WidgetStatus.Lock? = null,
        titles: List<String> = emptyList(),
        next: WidgetStatus.Upcoming? = null,
    ) = WidgetStatus(lock, titles, next, emptySet<DayOfWeek>(), null)

    @Test
    fun sinNadaActivoLoDiceClaro() {
        assertEquals("Nada bloqueado ahora", WidgetText.title(status()))
        assertEquals("Sin horarios próximos", WidgetText.detail(status(), now, zone))
    }

    @Test
    fun cuentaLosPerfilesEnSingularYPlural() {
        assertEquals("1 perfil activo", WidgetText.title(status(titles = listOf("Trabajo"))))
        assertEquals("3 perfiles activos", WidgetText.title(status(titles = listOf("A", "B", "C"))))
    }

    @Test
    fun conMasDeDosPerfilesResumeElResto() {
        assertEquals("Activos: A, B y 2 más", WidgetText.detail(status(titles = listOf("A", "B", "C", "D")), now, zone))
    }

    @Test
    fun elSiguienteHorarioDiceCuando() {
        fun next(at: String) = status(next = WidgetStatus.Upcoming("Noche", Instant.parse(at)))
        assertEquals("Siguiente: Noche, hoy 20:00", WidgetText.detail(next("2026-10-26T20:00:00Z"), now, zone))
        assertEquals("Siguiente: Noche, mañana 08:00", WidgetText.detail(next("2026-10-27T08:00:00Z"), now, zone))
        assertEquals("Siguiente: Noche, Vie 08:00", WidgetText.detail(next("2026-10-30T08:00:00Z"), now, zone))
    }

    @Test
    fun activosYSiguienteVanEnDosLineas() {
        val s = status(titles = listOf("Trabajo"), next = WidgetStatus.Upcoming("Noche", Instant.parse("2026-10-26T20:00:00Z")))
        assertEquals("Activos: Trabajo\nSiguiente: Noche, hoy 20:00", WidgetText.detail(s, now, zone))
    }

    @Test
    fun elBloqueoSinSalidaMuestraSuTituloYHora() {
        val lock = WidgetStatus.Lock("Temporizador", Instant.parse("2026-10-26T10:25:00Z"))
        val s = status(lock = lock, titles = listOf("Trabajo"))
        assertEquals("Temporizador", WidgetText.title(s))
        assertEquals("Hasta las 10:25", WidgetText.lockUntil(lock, zone))
        assertEquals("Todo bloqueado menos llamadas y FocusGuard", WidgetText.detail(s, now, zone))
    }

    @Test
    fun loQueQuedaDelBloqueoNuncaEsNegativo() {
        fun left(seconds: Long) = WidgetText.lockRemaining(WidgetStatus.Lock("Dormir", now.plusSeconds(seconds)), now)
        assertEquals("25 min", left(25 * 60))
        assertEquals("1 min", left(1))
        assertEquals("2 min", left(61))
        assertEquals("1 h 0 min", left(3600))
        assertEquals("7 h 30 min", left(7 * 3600 + 30 * 60))
        // Ya pasó la hora (el widget se repintó tarde): ni «-0:12» ni «0 min».
        assertEquals("Terminando", left(0))
        assertEquals("Terminando", left(-12))
    }

    @Test
    fun cadaDiaSeDescribeParaElLectorDePantalla() {
        assertEquals("lunes, hoy, con horario", WidgetText.dayDescription(DayOfWeek.MONDAY, active = true, today = true))
        assertEquals("sábado, sin horario", WidgetText.dayDescription(DayOfWeek.SATURDAY, active = false, today = false))
    }
}
