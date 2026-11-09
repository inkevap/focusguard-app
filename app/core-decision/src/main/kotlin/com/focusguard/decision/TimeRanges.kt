package com.focusguard.decision

import com.focusguard.model.ScheduleInterval

/** Cómo se dibuja y se cuenta un rango de horas del día (minutos desde las 00:00, de 0 a 1440). */
object TimeRanges {

    private const val DAY = ScheduleInterval.MINUTES_PER_DAY

    /** ¿Sigue al día siguiente? Es la regla de [ScheduleInterval]: si «hasta» no es mayor que «desde», cruza la medianoche. */
    fun crossesMidnight(startMinute: Int, endMinute: Int) = endMinute <= startMinute

    /** Los tramos del día que cubre, como fracciones de 0 a 1 de las 24 h: uno, o dos si cruza la medianoche. */
    fun segments(startMinute: Int, endMinute: Int): List<ClosedFloatingPointRange<Float>> = if (crossesMidnight(startMinute, endMinute)) {
        listOfNotNull(
            (startMinute.toFloat() / DAY)..1f,
            if (endMinute > 0) 0f..(endMinute.toFloat() / DAY) else null,
        )
    } else {
        listOf((startMinute.toFloat() / DAY)..(endMinute.toFloat() / DAY))
    }

    /** Cuánto dura, en minutos; igual desde y hasta es el día entero. */
    fun lengthMinutes(startMinute: Int, endMinute: Int) = if (endMinute > startMinute) endMinute - startMinute else DAY - startMinute + endMinute

    /** «De 23:00 a 07:00 · 8 h · sigue al día siguiente». */
    fun describe(startMinute: Int, endMinute: Int): String {
        val length = lengthMinutes(startMinute, endMinute)
        val duration = if (length % 60 == 0) "${length / 60} h" else if (length > 60) "${length / 60} h ${length % 60} min" else "$length min"
        val tail = if (crossesMidnight(startMinute, endMinute)) " · sigue al día siguiente" else ""
        return "De ${clock(startMinute)} a ${clock(endMinute)} · $duration$tail"
    }

    private fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)
}
