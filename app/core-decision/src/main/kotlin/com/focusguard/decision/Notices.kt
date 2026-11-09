package com.focusguard.decision

import com.focusguard.model.LimitKind
import com.focusguard.model.LockMotivation
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.model.ProfileUsage
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class NoticeKind { SCHEDULE_STARTING, LIMIT_NEARING, SCHEDULE_ENDED, DAILY_MOTIVATION, WEEKLY_REPORT, PAUSE_ENDING, PAUSE_ENDED, QUICK_BLOCK_ENDED, STREAK_AT_RISK, STREAK_MILESTONE, REMINDER, MOOD_PROMPT, TIME_NUDGE }

/** Un aviso que toca dar ahora. [key] es el mismo mientras se trate del mismo suceso, para no repetirlo. */
data class Notice(
    val key: String,
    val kind: NoticeKind,
    /** De qué perfil es; 0 y vacío en los avisos que no son de un perfil (frase diaria, resumen semanal). */
    val profileId: Long = 0,
    val profileTitle: String = "",
    val minutes: Int = 0,
    /** Frase de la motivación diaria. */
    val text: String = "",
    /** Bloqueos de la semana en el resumen semanal: lo rellena quien lo publica, porque aquí no se consulta nada. */
    val count: Int = 0,
)

/** Lo que hace falta para los recordatorios propios y la pregunta de ánimo. */
data class HabitInputs(
    val reminders: List<Reminder> = emptyList(),
    val moodPrompt: MoodPromptSettings = MoodPromptSettings(),
    val moodLoggedToday: Boolean = false,
)

/** Qué avisos quiere el usuario (J3). */
data class NoticeSettings(
    val scheduleStarting: Boolean = true,
    val limitNearing: Boolean = true,
    /** Una notificación fija, mientras usas una app con límite, que dice cuánto llevas y cuánto te queda. */
    val limitStatus: Boolean = true,
    val scheduleEnded: Boolean = true,
    val pauseEnding: Boolean = true,
    val dailyMotivation: Boolean = false,
    val weeklyReport: Boolean = false,
    /** Avisos de la racha: en riesgo al caer la tarde y al llegar a un hito. */
    val streak: Boolean = true,
)

/**
 * Avisos de lo que pasa con los horarios y los límites (E10, J3): que empiece un horario, que se agote un límite de tiempo
 * o que termine un horario, y los avisos de cada día y de cada semana. No miran las condiciones de red, cargador o lugar:
 * avisan de lo que dice el horario.
 */
object Notices {

    const val LEAD_MINUTES = 10
    const val LIMIT_LEFT_MINUTES = 5

    /** Un horario recién terminado se avisa durante estos minutos; pasado ese rato ya no es noticia. */
    const val ENDED_WINDOW_MINUTES = 2

    /** Se avisa de que una pausa va a terminar cuando quedan estos minutos o menos. */
    const val PAUSE_LEAD_MINUTES = 2

    /** Hora de la motivación diaria y del informe semanal (los lunes), y cuánto rato después sigue valiendo. */
    const val DIGEST_MINUTE = 9 * 60
    const val DIGEST_WINDOW_MINUTES = 60

    fun due(
        profiles: List<Profile>,
        usage: Map<Long, ProfileUsage>,
        now: Instant,
        localNow: LocalDateTime,
        settings: NoticeSettings,
        dayStartMinute: Int = 0,
        zone: ZoneId = ZoneId.systemDefault(),
        /** La racha de ahora, si hace falta (solo con los avisos de racha encendidos); sin ella no hay avisos de racha. */
        streak: StreakState? = null,
        /** Los recordatorios propios y la pregunta de ánimo (K5, J4). */
        habits: HabitInputs = HabitInputs(),
    ): List<Notice> = profiles.filter { it.noticeable(now) }.flatMap { profile ->
        listOfNotNull(
            if (settings.scheduleStarting) scheduleStarting(profile, localNow) else null,
            if (settings.limitNearing) limitNearing(profile, usage[profile.id], localNow, dayStartMinute, zone) else null,
            if (settings.scheduleEnded) scheduleEnded(profile, localNow) else null,
        )
    } + (if (settings.pauseEnding) profiles.mapNotNull { pauseNotice(it, now) } else emptyList()) + listOfNotNull(
        if (settings.dailyMotivation) dailyMotivation(localNow) else null,
        if (settings.weeklyReport) weeklyReport(localNow) else null,
    ) + (streak?.let { Streaks.notices(it, Streaks.dayOf(localNow, dayStartMinute), localNow, dayStartMinute) } ?: emptyList()) +
        Reminders.due(habits.reminders, localNow) +
        listOfNotNull(Moods.promptDue(habits.moodPrompt, habits.moodLoggedToday, Streaks.dayOf(localNow, dayStartMinute), localNow))

    /** Perfiles de verdad (no sesiones) y encendidos. */
    private fun Profile.watchable() = kind == ProfileKind.NORMAL && enabled

    private fun Profile.noticeable(now: Instant) = watchable() && pausedUntil?.isAfter(now) != true

    /** Una pausa a punto de terminar, o que acaba de terminar. Los perfiles en pausa no pasan por el resto de avisos. */
    private fun pauseNotice(profile: Profile, now: Instant): Notice? {
        val until = profile.pausedUntil?.takeIf { profile.watchable() } ?: return null
        val leftSeconds = Duration.between(now, until).seconds
        return when {
            leftSeconds in 1..PAUSE_LEAD_MINUTES * 60L ->
                Notice("pauseend:${profile.id}:$until", NoticeKind.PAUSE_ENDING, profile.id, profile.title, ((leftSeconds + 59) / 60).toInt())
            leftSeconds <= 0 && -leftSeconds <= ENDED_WINDOW_MINUTES * 60L ->
                Notice("pauseended:${profile.id}:$until", NoticeKind.PAUSE_ENDED, profile.id, profile.title)
            else -> null
        }
    }

    /** Minutos hasta el primer horario del perfil que empieza dentro de [LEAD_MINUTES], si ahora no está dentro de ninguno. */
    private fun scheduleStarting(profile: Profile, localNow: LocalDateTime): Notice? {
        if (profile.intervals.isEmpty() || ScheduleEvaluator.isWithin(profile.intervals, localNow)) return null
        val minutes = profile.intervals
            .map { ScheduleEvaluator.minutesUntilStart(it, localNow) }
            .filter { it in 1..LEAD_MINUTES }
            .minOrNull() ?: return null
        val startsAt = localNow.truncatedTo(ChronoUnit.MINUTES).plusMinutes(minutes.toLong())
        return Notice("start:${profile.id}:$startsAt", NoticeKind.SCHEDULE_STARTING, profile.id, profile.title, minutes)
    }

    /** Un horario que acaba de terminar, durante unos minutos. */
    private fun scheduleEnded(profile: Profile, localNow: LocalDateTime): Notice? {
        val since = ScheduleEvaluator.minutesSinceEnd(profile.intervals, localNow)?.takeIf { it <= ENDED_WINDOW_MINUTES } ?: return null
        val endedAt = localNow.truncatedTo(ChronoUnit.MINUTES).minusMinutes(since.toLong())
        return Notice("end:${profile.id}:$endedAt", NoticeKind.SCHEDULE_ENDED, profile.id, profile.title, since)
    }

    private fun inDigestWindow(localNow: LocalDateTime): Boolean {
        val minute = localNow.hour * 60 + localNow.minute
        return minute in DIGEST_MINUTE until DIGEST_MINUTE + DIGEST_WINDOW_MINUTES
    }

    private fun dailyMotivation(localNow: LocalDateTime): Notice? {
        if (!inDigestWindow(localNow)) return null
        val day = localNow.toLocalDate()
        return Notice("daily:$day", NoticeKind.DAILY_MOTIVATION, text = LockMotivation.pick(day.dayOfYear))
    }

    private fun weeklyReport(localNow: LocalDateTime): Notice? {
        if (localNow.dayOfWeek != DayOfWeek.MONDAY || !inDigestWindow(localNow)) return null
        return Notice("weekly:${localNow.toLocalDate()}", NoticeKind.WEEKLY_REPORT)
    }

    /** Solo con límites más largos que el margen: en uno de 5 minutos o menos el aviso saltaría nada más empezar. */
    private fun limitNearing(profile: Profile, usage: ProfileUsage?, localNow: LocalDateTime, dayStartMinute: Int, zone: ZoneId): Notice? {
        val limit = profile.limit?.takeIf { it.kind == LimitKind.TIME && it.amount > LIMIT_LEFT_MINUTES } ?: return null
        val used = usage ?: return null
        val leftMs = limit.amount * 60_000L - used.timeMs
        if (leftMs <= 0 || leftMs > LIMIT_LEFT_MINUTES * 60_000L) return null
        val periodStart = Periods.startMs(limit.period, localNow, dayStartMinute, zone)
        if (used.periodStartMs != periodStart) return null
        return Notice("limit:${profile.id}:$periodStart", NoticeKind.LIMIT_NEARING, profile.id, profile.title, ((leftMs + 59_999) / 60_000).toInt())
    }
}
