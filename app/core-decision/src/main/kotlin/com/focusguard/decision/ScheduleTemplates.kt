package com.focusguard.decision

import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import java.time.DayOfWeek

/** Punto de partida para un perfil: el nombre, el horario y, a veces, un límite. Las apps y webs las elige el usuario. */
data class ScheduleTemplate(
    val id: String,
    val title: String,
    val emoji: String,
    val description: String,
    val intervals: List<ScheduleInterval> = emptyList(),
    val limit: UsageLimit? = null,
) {
    /** Aplica la plantilla a [base] sin tocar sus apps, webs ni modo. */
    fun applyTo(base: Profile): Profile =
        base.copy(title = title, emoji = emoji, intervals = intervals, limit = limit)
}

/** Las 13 plantillas de horario (E14). */
object ScheduleTemplates {

    private val weekdays = DayOfWeek.entries.take(5)
    private val weekend = DayOfWeek.entries.drop(5)
    private val everyDay = DayOfWeek.entries
    private val friday = listOf(DayOfWeek.FRIDAY)

    /** Si [toHour] es menor o igual que [fromHour], el tramo cruza la medianoche. */
    private fun at(days: List<DayOfWeek>, fromHour: Int, toHour: Int) =
        days.map { ScheduleInterval(it, fromHour * 60, toHour * 60) }

    val all: List<ScheduleTemplate> = listOf(
        ScheduleTemplate("back_to_school", "Vuelta al cole", "🎒", "Lunes a viernes de 8:00 a 15:00, sin distracciones en clase.", at(weekdays, 8, 15)),
        ScheduleTemplate("black_friday", "Viernes negro", "🛍️", "Todo el viernes, para no comprar por impulso.", at(friday, 0, 24)),
        ScheduleTemplate("new_year", "Año nuevo", "🎆", "Un límite de una hora al día para empezar el año con menos pantalla.", limit = UsageLimit(LimitKind.TIME, 60, LimitPeriod.DAILY)),
        ScheduleTemplate("spring_renewal", "Renovación de primavera", "🌱", "Las mañanas del fin de semana, de 9:00 a 13:00, para salir y hacer otras cosas.", at(weekend, 9, 13)),
        ScheduleTemplate("summer_vacation", "Vacaciones de verano", "🏖️", "Todos los días de 10:00 a 14:00, para disfrutar el día.", at(everyDay, 10, 14)),
        ScheduleTemplate("focus", "Enfoque", "🎯", "Lunes a viernes de 9:00 a 13:00 y de 15:00 a 18:00.", at(weekdays, 9, 13) + at(weekdays, 15, 18)),
        ScheduleTemplate("app_limit", "Límite de apps", "⏱️", "Media hora al día entre todas las apps elegidas.", limit = UsageLimit(LimitKind.TIME, 30, LimitPeriod.DAILY)),
        ScheduleTemplate("me_time", "Tiempo para mí", "🧘", "Todos los días de 20:00 a 22:00.", at(everyDay, 20, 22)),
        ScheduleTemplate("at_home", "En casa", "🏠", "Lunes a viernes de 18:00 a 22:00, para estar con quien está contigo.", at(weekdays, 18, 22)),
        ScheduleTemplate("study", "Estudio", "📚", "Lunes a viernes de 16:00 a 20:00.", at(weekdays, 16, 20)),
        ScheduleTemplate("digital_detox", "Desintoxicación digital", "🌿", "Sábado y domingo enteros.", at(weekend, 0, 24)),
        ScheduleTemplate("night_relax", "Relajación nocturna", "🌙", "Todos los días de 22:00 a 7:00.", at(everyDay, 22, 7)),
        ScheduleTemplate("mindful_morning", "Mañana consciente", "☀️", "Todos los días de 6:00 a 9:00, para empezar sin el teléfono.", at(everyDay, 6, 9)),
    )

    init {
        require(all.map { it.id }.toSet().size == all.size) { "plantillas con identificador repetido" }
    }
}
