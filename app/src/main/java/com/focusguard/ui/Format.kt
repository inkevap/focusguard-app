package com.focusguard.ui

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** "5:03", o "1:05:03" con horas; [alwaysHours] las muestra aunque sean cero. */
internal fun formatRemaining(duration: Duration, alwaysHours: Boolean = false): String {
    val seconds = duration.seconds.coerceAtLeast(0)
    return if (alwaysHours || seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

internal fun plural(count: Int, one: String, many: String) = if (count == 1) "1 $one" else "$count $many"

/** Como "09:00", para los campos de hora. */
internal fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/** "HH:mm" → minutos desde las 00:00, o null si no es válido. "24:00" se admite como fin de día. */
internal fun parseMinutes(text: String): Int? {
    val parts = text.trim().split(':')
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (m !in 0..59 || h !in 0..24 || (h == 24 && m != 0)) return null
    return h * 60 + m
}

/** Una hora del día escrita a mano ("HH:mm", de 00:00 a 23:59), en minutos; "24:00" no vale. */
internal fun parseTimeOfDay(text: String): Int? = parseMinutes(text)?.takeIf { it < 24 * 60 }

/** "1 h", "90 min" o "1 h 30 min". */
internal fun formatAmountOfTime(minutes: Long): String = when {
    minutes % 60 == 0L -> "${minutes / 60} h"
    minutes > 60 -> "${minutes / 60} h ${minutes % 60} min"
    else -> "$minutes min"
}

/** «lunes 10:00» o solo «10:00», en la hora del teléfono. */
internal fun formatLocal(instant: Instant, withDay: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): String {
    val local = instant.atZone(zone)
    val time = formatMinutes(local.hour * 60 + local.minute)
    return if (withDay) "${Days.word(local.dayOfWeek)} $time" else time
}

/** Nombres de los días en español, en tres tamaños: «Lun», «L» y «lunes». */
internal object Days {
    private val short = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    private val letters = listOf("L", "M", "X", "J", "V", "S", "D")
    private val words = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

    fun short(day: DayOfWeek) = short[day.value - 1]
    fun short(dayValue: Int) = short[dayValue - 1]
    fun letter(day: DayOfWeek) = letters[day.value - 1]
    fun word(day: DayOfWeek) = words[day.value - 1]
}
