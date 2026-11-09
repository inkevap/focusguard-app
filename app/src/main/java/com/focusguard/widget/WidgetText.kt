package com.focusguard.widget

import com.focusguard.decision.WidgetStatus
import com.focusguard.ui.Days
import com.focusguard.ui.formatLocal
import com.focusguard.ui.plural
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Los textos de los widgets, aparte para probarlos sin Android. */
internal object WidgetText {
    private const val MAX_TITLES = 2

    fun title(status: WidgetStatus): String {
        val lock = status.lock
        return when {
            lock != null -> lock.title
            status.activeCount == 0 -> "Nada bloqueado ahora"
            else -> plural(status.activeCount, "perfil activo", "perfiles activos")
        }
    }

    /** Hasta qué hora dura el bloqueo sin salida; el resto de líneas se arman en [detail]. */
    fun lockUntil(lock: WidgetStatus.Lock, zone: ZoneId): String = "Hasta las ${formatLocal(lock.until, zone = zone)}"

    /**
     * Lo que queda del bloqueo sin salida, en minutos redondeados hacia arriba. **Nunca negativo**: es un texto fijo, no un cronómetro. El cronómetro de
     * cuenta atrás de un widget sigue contando por debajo de cero si el repintado llega tarde (la alarma es aproximada), y se veía «-0:12».
     */
    fun lockRemaining(lock: WidgetStatus.Lock, now: Instant): String {
        val seconds = Duration.between(now, lock.until).seconds
        if (seconds <= 0) return "Terminando"
        val minutes = ((seconds + 59) / 60).toInt()
        return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    }

    fun detail(status: WidgetStatus, now: Instant, zone: ZoneId): String {
        if (status.lock != null) return "Todo bloqueado menos llamadas y FocusGuard"
        val lines = buildList {
            if (status.activeCount > 0) add("Activos: " + names(status.activeTitles))
            status.nextStart?.let { add("Siguiente: ${it.title}, ${whenWord(it.at, now, zone)}") }
        }
        return lines.joinToString("\n").ifEmpty { "Sin horarios próximos" }
    }

    /** Lo que lee el lector de pantalla de cada día: el círculo solo se ve. */
    fun dayDescription(day: DayOfWeek, active: Boolean, today: Boolean): String =
        Days.word(day) + (if (today) ", hoy" else "") + if (active) ", con horario" else ", sin horario"

    private fun names(titles: List<String>): String {
        val shown = titles.take(MAX_TITLES).joinToString(", ")
        val more = titles.size - MAX_TITLES
        return if (more > 0) "$shown y $more más" else shown
    }

    /** «hoy 20:00», «mañana 08:00» o «lun 08:00». */
    fun whenWord(at: Instant, now: Instant, zone: ZoneId): String {
        val day = at.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val time = formatLocal(at, zone = zone)
        return when (day) {
            today -> "hoy $time"
            today.plusDays(1) -> "mañana $time"
            else -> "${Days.short(day.dayOfWeek)} $time"
        }
    }
}
