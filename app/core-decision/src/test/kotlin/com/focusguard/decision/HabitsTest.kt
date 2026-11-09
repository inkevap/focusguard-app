package com.focusguard.decision

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitsTest {

    private val min = 60_000L

    /** Usa [minutes] minutos seguidos empezando en [fromMs], leyendo cada segundo. */
    private fun use(tracker: NudgeTracker, fromMs: Long, minutes: Int) {
        var t = fromMs
        tracker.tick(t, true)
        repeat(minutes * 60) { t += 1_000; tracker.tick(t, true) }
    }

    @Test
    fun avisaAlSumarQuinceMinutosEnLaUltimaHora() {
        val tracker = NudgeTracker()
        use(tracker, 0, 14)
        assertFalse(tracker.shouldNudge(14 * min))
        use(tracker, 14 * min, 2)
        assertTrue(tracker.shouldNudge(16 * min))
        assertTrue(tracker.usedMinutes() >= 15)
    }

    @Test
    fun esperaUnaHoraAntesDeVolverAAvisar() {
        val tracker = NudgeTracker()
        use(tracker, 0, 16)
        assertTrue(tracker.shouldNudge(16 * min))
        use(tracker, 16 * min, 20)
        assertFalse(tracker.shouldNudge(36 * min))
        assertFalse(tracker.shouldNudge(75 * min))
        // Pasada la hora de espera y con quince minutos dentro de la ventana, vuelve a avisar.
        use(tracker, 76 * min, 16)
        assertTrue(tracker.shouldNudge(92 * min))
    }

    @Test
    fun elUsoViejoSaleDeLaVentana() {
        val tracker = NudgeTracker()
        use(tracker, 0, 10)
        use(tracker, 70 * min, 10)
        // Los 10 primeros minutos ya pasaron hace más de una hora: solo cuentan los últimos 10.
        assertFalse(tracker.shouldNudge(80 * min))
    }

    @Test
    fun sinUsoDeLasAppsElegidasNoSeCuentaNada() {
        val tracker = NudgeTracker()
        var t = 0L
        repeat(1_200) { t += 1_000; tracker.tick(t, false) }
        assertFalse(tracker.shouldNudge(t))
        assertEquals(0, tracker.usedMinutes())
    }

    @Test
    fun unHuecoLargoEntreLecturasNoRegalaTiempo() {
        val tracker = NudgeTracker()
        tracker.tick(0, true)
        tracker.tick(30 * min, true)
        assertEquals(0, tracker.usedMinutes())
    }

    private val now = LocalDateTime.of(2026, 10, 28, 9, 10)

    @Test
    fun unRecordatorioSaleDesdeSuHoraDuranteMediaHora() {
        val reminders = listOf(Reminder(1, 9 * 60, "Beber agua"), Reminder(2, 12 * 60, "Caminar"), Reminder(3, 9 * 60, "Apagado", enabled = false))
        val due = Reminders.due(reminders, now)
        assertEquals(listOf("Beber agua"), due.map { it.text })
        assertEquals("reminder:1:2026-10-28", due.single().key)
        assertEquals(NoticeKind.REMINDER, due.single().kind)
        assertTrue(Reminders.due(reminders, LocalDateTime.of(2026, 10, 28, 8, 59)).isEmpty())
        assertTrue(Reminders.due(reminders, LocalDateTime.of(2026, 10, 28, 9, 30)).isEmpty())
    }

    @Test
    fun seLimitanLosRecordatorios() {
        assertTrue(Reminders.canAdd(9, 600, "Hola"))
        assertFalse(Reminders.canAdd(10, 600, "Hola"))
        assertFalse(Reminders.canAdd(0, 24 * 60, "Hola"))
        assertFalse(Reminders.canAdd(0, 600, "   "))
        assertEquals("a b", Reminders.clean("  a   b "))
        assertEquals(Reminders.MAX_TEXT_CHARS, Reminders.clean("x".repeat(400)).length)
    }

    private val day = LocalDate.of(2026, 10, 28)

    @Test
    fun laPreguntaDeAnimoSoloSaleSiEstaEncendidaEsLaHoraYFaltaResponder() {
        val on = MoodPromptSettings(enabled = true, minuteOfDay = 20 * 60)
        val evening = LocalDateTime.of(2026, 10, 28, 20, 5)
        assertEquals("mood:2026-10-28", Moods.promptDue(on, false, day, evening)?.key)
        assertNull(Moods.promptDue(on, true, day, evening))
        assertNull(Moods.promptDue(on.copy(enabled = false), false, day, evening))
        assertNull(Moods.promptDue(on, false, day, LocalDateTime.of(2026, 10, 28, 19, 59)))
        assertNull(Moods.promptDue(on, false, day, LocalDateTime.of(2026, 10, 28, 21, 0)))
    }

    @Test
    fun losAnimosVanDelUnoAlCinco() {
        assertEquals(listOf(1, 2, 3, 4, 5), Mood.entries.map { it.level })
        assertEquals(Mood.GOOD, Mood.of(4))
        assertNull(Mood.of(9))
    }

    @Test
    fun noticesDueIncluyeLosRecordatoriosYLaPregunta() {
        val zone = java.time.ZoneOffset.UTC
        val local = LocalDateTime.of(2026, 10, 28, 20, 5)
        val habits = HabitInputs(listOf(Reminder(1, 20 * 60, "Meditar")), MoodPromptSettings(enabled = true), moodLoggedToday = false)
        val due = Notices.due(emptyList(), emptyMap(), local.toInstant(zone), local, NoticeSettings(), zone = zone, habits = habits)
        assertEquals(listOf(NoticeKind.REMINDER, NoticeKind.MOOD_PROMPT), due.map { it.kind })
    }

    @Test
    fun unRecordatorioTardioSigueValiendoPasadaLaMedianocheSinRepetirse() {
        val late = listOf(Reminder(1, 23 * 60 + 50, "Dormir"))
        val before = Reminders.due(late, LocalDateTime.of(2026, 10, 28, 23, 55))
        val after = Reminders.due(late, LocalDateTime.of(2026, 10, 29, 0, 10))
        assertEquals("reminder:1:2026-10-28", before.single().key)
        // Es el mismo aviso: el libro de avisos ya vistos lo reconoce y no vuelve a salir.
        assertEquals(before.single().key, after.single().key)
        assertTrue(Reminders.due(late, LocalDateTime.of(2026, 10, 29, 0, 20)).isEmpty())
        assertEquals(1, before.single().profileId.toInt())
    }
}
