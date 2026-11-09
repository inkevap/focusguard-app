package com.focusguard.decision

import com.focusguard.model.LimitKind
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.model.ProfileUsage
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NoticesTest {

    private val zone = ZoneOffset.UTC
    private val monday = LocalDateTime.of(2026, 10, 26, 8, 55) // lunes
    private val nine = ScheduleInterval(DayOfWeek.MONDAY, 9 * 60, 17 * 60)

    private fun due(
        profiles: List<Profile>,
        at: LocalDateTime = monday,
        usage: Map<Long, ProfileUsage> = emptyMap(),
        settings: NoticeSettings = NoticeSettings(),
    ) = Notices.due(profiles, usage, at.toInstant(zone), at, settings, zone = zone)

    @Test
    fun avisaDiezMinutosAntesDeQueEmpieceUnHorario() {
        val notice = due(listOf(Profile(1, "Estudio", intervals = listOf(nine)))).single()
        assertEquals(NoticeKind.SCHEDULE_STARTING, notice.kind)
        assertEquals(5, notice.minutes)
        assertEquals("Estudio", notice.profileTitle)
    }

    @Test
    fun noAvisaSiFaltaMasOSiYaEstaDentro() {
        val profile = Profile(1, "Estudio", intervals = listOf(nine))
        assertTrue(due(listOf(profile), at = LocalDateTime.of(2026, 10, 26, 8, 40)).isEmpty())
        assertTrue(due(listOf(profile), at = LocalDateTime.of(2026, 10, 26, 9, 0)).isEmpty())
        assertTrue(due(listOf(profile), at = LocalDateTime.of(2026, 10, 26, 12, 0)).isEmpty())
    }

    @Test
    fun elMismoSucesoTieneLaMismaClaveYOtroDiaOtra() {
        val profile = Profile(1, "Estudio", intervals = DayOfWeek.entries.map { ScheduleInterval(it, 9 * 60, 17 * 60) })
        val first = due(listOf(profile)).single().key
        val later = due(listOf(profile), at = monday.plusMinutes(2)).single().key
        val nextDay = due(listOf(profile), at = monday.plusDays(1)).single().key
        assertEquals(first, later)
        assertNotEquals(first, nextDay)
    }

    @Test
    fun avisaCuandoLeQuedanPocosMinutosDeLimite() {
        val profile = Profile(1, "Juegos", limit = UsageLimit(LimitKind.TIME, 60))
        val start = Periods.startMs(profile.limit!!.period, monday, 0, zone)
        val nearly = mapOf(1L to ProfileUsage(periodStartMs = start, timeMs = 57 * 60_000L + 30_000L))
        val notice = due(listOf(profile), usage = nearly).single()
        assertEquals(NoticeKind.LIMIT_NEARING, notice.kind)
        assertEquals(3, notice.minutes)
        val far = mapOf(1L to ProfileUsage(periodStartMs = start, timeMs = 30 * 60_000L))
        val over = mapOf(1L to ProfileUsage(periodStartMs = start, timeMs = 61 * 60_000L))
        val old = mapOf(1L to ProfileUsage(periodStartMs = start - 86_400_000L, timeMs = 58 * 60_000L))
        listOf(far, over, old).forEach { assertTrue(due(listOf(profile), usage = it).isEmpty()) }
    }

    @Test
    fun unLimiteCortoNoAvisaNadaMasEmpezar() {
        val short = Profile(1, "Juegos", limit = UsageLimit(LimitKind.TIME, 5))
        val start = Periods.startMs(short.limit!!.period, monday, 0, zone)
        assertTrue(due(listOf(short), usage = mapOf(1L to ProfileUsage(periodStartMs = start, timeMs = 60_000L))).isEmpty())
    }

    @Test
    fun respetaLosAjustesYLosPerfilesApagadosOTemporales() {
        val schedule = Profile(1, "Estudio", intervals = listOf(nine))
        assertTrue(due(listOf(schedule), settings = NoticeSettings(scheduleStarting = false)).isEmpty())
        assertTrue(due(listOf(schedule.copy(enabled = false))).isEmpty())
        assertTrue(due(listOf(schedule.copy(kind = ProfileKind.QUICK_BLOCK))).isEmpty())
        assertTrue(due(listOf(schedule.copy(pausedUntil = Instant.parse("2026-10-26T10:00:00Z")))).isEmpty())
    }

    @Test
    fun avisaDeQueUnHorarioHaTerminadoSoloUnosMinutos() {
        val p = Profile(1, "Estudio", intervals = listOf(nine))
        val at = { h: Int, m: Int -> LocalDateTime.of(2026, 10, 26, h, m) }
        val notice = due(listOf(p), at(17, 1)).single()
        assertEquals(NoticeKind.SCHEDULE_ENDED, notice.kind)
        assertEquals(1, notice.minutes)
        assertEquals(due(listOf(p), at(17, 1)).single().key, due(listOf(p), at(17, 2)).single().key)
        assertEquals(emptyList(), due(listOf(p), at(17, 3)))
        assertEquals(emptyList(), due(listOf(p), at(16, 59)))
        assertEquals(emptyList(), due(listOf(p), at(17, 1), settings = NoticeSettings(scheduleEnded = false)))
    }

    @Test
    fun minutosDesdeQueTerminoElUltimoHorario() {
        val night = listOf(ScheduleInterval(DayOfWeek.SUNDAY, 22 * 60, 7 * 60))
        assertEquals(30, ScheduleEvaluator.minutesSinceEnd(night, LocalDateTime.of(2026, 10, 26, 7, 30)))
        assertEquals(null, ScheduleEvaluator.minutesSinceEnd(night, LocalDateTime.of(2026, 10, 26, 6, 30)))
        assertEquals(null, ScheduleEvaluator.minutesSinceEnd(emptyList(), monday))
    }

    @Test
    fun laMotivacionDiariaSaleUnaVezAlDiaALaHora() {
        val on = NoticeSettings(dailyMotivation = true)
        val nineOne = LocalDateTime.of(2026, 10, 27, 9, 1)
        val first = due(emptyList(), nineOne, settings = on).single()
        assertEquals(NoticeKind.DAILY_MOTIVATION, first.kind)
        assertTrue(first.text.isNotBlank())
        assertEquals(first.key, due(emptyList(), LocalDateTime.of(2026, 10, 27, 9, 59), settings = on).single().key)
        assertEquals(emptyList(), due(emptyList(), LocalDateTime.of(2026, 10, 27, 10, 0), settings = on))
        assertEquals(emptyList(), due(emptyList(), LocalDateTime.of(2026, 10, 27, 8, 59), settings = on))
        assertEquals(emptyList(), due(emptyList(), nineOne))
    }

    @Test
    fun elResumenSemanalSaleLosLunesALaHoraYSinCifra() {
        val on = NoticeSettings(weeklyReport = true)
        fun report(at: LocalDateTime) = Notices.due(emptyList(), emptyMap(), at.toInstant(zone), at, on, zone = zone)
        assertEquals(emptyList(), report(LocalDateTime.of(2026, 10, 27, 9, 30)))
        assertEquals(emptyList(), report(LocalDateTime.of(2026, 10, 26, 10, 0)))
        val notice = report(LocalDateTime.of(2026, 10, 26, 9, 30)).single()
        assertEquals(NoticeKind.WEEKLY_REPORT, notice.kind)
        assertEquals("weekly:2026-10-26", notice.key)
    }

    @Test
    fun avisaCuandoUnaPausaVaATerminarYCuandoTermina() {
        val until = monday.toInstant(zone).plusSeconds(90)
        val paused = Profile(1, "Estudio", pausedUntil = until)
        fun at(offsetSeconds: Long) = monday.plusSeconds(offsetSeconds).let { Notices.due(listOf(paused), emptyMap(), it.toInstant(zone), it, NoticeSettings(), zone = zone) }
        assertEquals(emptyList(), at(-60)) // faltan 150 s: aún no
        val ending = at(0).single()
        assertEquals(NoticeKind.PAUSE_ENDING, ending.kind)
        assertEquals(2, ending.minutes)
        assertEquals(ending.key, at(30).single().key)
        val ended = at(100).single()
        assertEquals(NoticeKind.PAUSE_ENDED, ended.kind)
        assertEquals(emptyList(), at(90 + 121))
        assertEquals(emptyList(), Notices.due(listOf(paused), emptyMap(), monday.toInstant(zone), monday, NoticeSettings(pauseEnding = false), zone = zone))
    }
}
