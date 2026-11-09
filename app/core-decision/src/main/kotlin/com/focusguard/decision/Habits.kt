package com.focusguard.decision

import java.time.LocalDate
import java.time.LocalDateTime

/** Aviso de tiempo (K7): qué apps cuentan. Apagado por defecto. */
data class NudgeSettings(val enabled: Boolean = false, val apps: Set<String> = emptySet())

/**
 * Avisa sin bloquear cuando el uso de las apps elegidas suma 15 minutos dentro de la última hora, y espera una hora antes de repetir
 * (`ESPECIFICACION.md` 10). Cuenta el uso por minutos y olvida lo que sale de la ventana. Si el servicio se reinicia, empieza de cero.
 */
class NudgeTracker {
    private val buckets = ArrayDeque<Pair<Long, Long>>()
    private var lastTickMs: Long? = null
    private var lastNudgeMs: Long? = null

    /** Suma lo que pasó desde la lectura anterior, con el mismo tope por lectura que los límites de uso. */
    fun tick(nowMs: Long, inUse: Boolean) {
        val previous = lastTickMs
        lastTickMs = nowMs
        prune(nowMs)
        if (!inUse || previous == null) return
        val delta = (nowMs - previous).coerceIn(0, UsageAccumulator.MAX_STEP_MS)
        if (delta == 0L) return
        val bucket = nowMs / BUCKET_MS
        val last = buckets.lastOrNull()
        if (last != null && last.first == bucket) buckets[buckets.lastIndex] = bucket to last.second + delta else buckets.addLast(bucket to delta)
    }

    /** ¿Toca avisar ahora? Si sí, lo da por avisado: no vuelve a salir hasta pasada la espera. */
    fun shouldNudge(nowMs: Long): Boolean {
        prune(nowMs)
        if (lastNudgeMs?.let { nowMs - it < COOLDOWN_MS } == true) return false
        if (buckets.sumOf { it.second } < THRESHOLD_MS) return false
        lastNudgeMs = nowMs
        return true
    }

    /** Minutos de uso dentro de la ventana, para el texto del aviso. */
    fun usedMinutes(): Int = (buckets.sumOf { it.second } / 60_000).toInt()

    private fun prune(nowMs: Long) {
        while (buckets.isNotEmpty() && (buckets.first().first + 1) * BUCKET_MS <= nowMs - WINDOW_MS) buckets.removeFirst()
    }

    companion object {
        const val WINDOW_MS = 60 * 60_000L
        const val THRESHOLD_MS = 15 * 60_000L
        const val COOLDOWN_MS = 60 * 60_000L
        private const val BUCKET_MS = 60_000L
    }
}

/** Un recordatorio propio, a la misma hora todos los días (J4). */
data class Reminder(val id: Long, val minuteOfDay: Int, val text: String, val enabled: Boolean = true)

object Reminders {
    const val MAX = 10
    const val MAX_TEXT_CHARS = 100

    /** Un recordatorio sigue valiendo este rato después de su hora (por si el teléfono estaba apagado o la app cerrada un momento). */
    const val WINDOW_MINUTES = 30

    private val WHITESPACE = Regex("\\s+")

    fun clean(text: String) = text.trim().replace(WHITESPACE, " ").take(MAX_TEXT_CHARS)

    fun canAdd(current: Int, minuteOfDay: Int, text: String) = current < MAX && minuteOfDay in 0 until 24 * 60 && clean(text).isNotEmpty()

    /** Los que tocan ahora. La clave lleva el día en que empezó la ventana: uno de las 23:50 no se repite pasada la medianoche. */
    fun due(reminders: List<Reminder>, localNow: LocalDateTime): List<Notice> {
        val minute = localNow.hour * 60 + localNow.minute
        return reminders.filter { it.enabled && inWindow(localNow, it.minuteOfDay, WINDOW_MINUTES) }.map {
            val startDay = if (minute >= it.minuteOfDay) localNow.toLocalDate() else localNow.toLocalDate().minusDays(1)
            Notice("reminder:${it.id}:$startDay", NoticeKind.REMINDER, profileId = it.id, text = it.text)
        }
    }
}

/** Cómo se siente el usuario (K5), de 1 a 5. El emoji y el nombre están en la capa de pantalla. */
enum class Mood(val level: Int) {
    VERY_BAD(1),
    BAD(2),
    OK(3),
    GOOD(4),
    GREAT(5),
    ;

    companion object {
        fun of(level: Int) = entries.firstOrNull { it.level == level }
    }
}

/** La pregunta de ánimo que aparece sola (K5). Apagada por defecto. */
data class MoodPromptSettings(val enabled: Boolean = false, val minuteOfDay: Int = 20 * 60)

object Moods {
    /** La pregunta vale este rato desde su hora (si es muy tarde, hasta la medianoche). */
    const val WINDOW_MINUTES = 60

    /** La pregunta de ánimo, si está encendida, ya es la hora y hoy aún no se ha respondido. */
    fun promptDue(settings: MoodPromptSettings, loggedToday: Boolean, day: LocalDate, localNow: LocalDateTime): Notice? {
        if (!settings.enabled || loggedToday) return null
        // Sin cruzar la medianoche: la respuesta de «hoy» pertenece a un día, y pasada la medianoche sería otro día distinto.
        val minute = localNow.hour * 60 + localNow.minute
        if (minute < settings.minuteOfDay || minute >= settings.minuteOfDay + WINDOW_MINUTES) return null
        return Notice("mood:$day", NoticeKind.MOOD_PROMPT)
    }
}

/** ¿Es la hora [startMinute] o han pasado menos de [windowMinutes] desde ella (también pasada la medianoche)? */
private fun inWindow(localNow: LocalDateTime, startMinute: Int, windowMinutes: Int): Boolean =
    Math.floorMod(localNow.hour * 60 + localNow.minute - startMinute, 24 * 60) < windowMinutes
