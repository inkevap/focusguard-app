package com.focusguard.decision

import com.focusguard.model.AppCategory
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import java.time.DayOfWeek

/** Cuándo quiere el usuario que bloquee su primer perfil (O3). */
enum class OnboardingWhen(val label: String, val hint: String) {
    ALWAYS("Siempre", "Sin horario: bloquea a todas horas."),
    WORK_HOURS("En horas de trabajo", "De lunes a viernes, de 9:00 a 17:00."),
    EVENINGS("Por las tardes", "Todos los días, de 18:00 a 23:00."),
    NIGHT("De noche", "Todos los días, de 22:00 a 7:00."),
}

/** Lo que el usuario quiere conseguir (paso 2 del asistente): rellena de antemano qué cuidar y cuándo, y luego se ajusta. */
enum class OnboardingGoal(val label: String, val hint: String, categories: List<AppCategory>, val whenToBlock: OnboardingWhen) {
    REDUCE_SOCIAL("Reducir redes sociales y vídeo", "Bloquea redes y plataformas de vídeo por las tardes.", listOf(AppCategory.SOCIAL, AppCategory.VIDEO), OnboardingWhen.EVENINGS),
    FOCUS("Estudiar o trabajar mejor", "Bloquea lo que distrae en horas de trabajo.", listOf(AppCategory.SOCIAL, AppCategory.VIDEO, AppCategory.GAMES, AppCategory.NEWS), OnboardingWhen.WORK_HOURS),
    SLEEP("Dormir mejor", "Bloquea de noche lo que te mantiene despierto.", listOf(AppCategory.SOCIAL, AppCategory.VIDEO, AppCategory.GAMES), OnboardingWhen.NIGHT),
    ADULT("Dejar el contenido adulto", "El filtro ya viene activado: no hace falta elegir nada más.", emptyList(), OnboardingWhen.ALWAYS),
    EVERYTHING("Un poco de todo", "Redes, vídeo, juegos, compras y noticias, por las tardes.", Onboarding.SUGGESTED_CATEGORIES, OnboardingWhen.EVENINGS),
    ;

    val categoryIds: Set<String> = categories.mapTo(linkedSetOf()) { it.id }
}

/** Primer perfil a partir de dos preguntas: qué cuidar y cuándo. */
object Onboarding {

    /** Lo que más suele quitar el tiempo; el resto de categorías se elige después al editar el perfil. */
    val SUGGESTED_CATEGORIES = listOf(AppCategory.SOCIAL, AppCategory.VIDEO, AppCategory.GAMES, AppCategory.SHOPPING, AppCategory.NEWS)

    fun firstProfile(categories: Set<String>, whenToBlock: OnboardingWhen): Profile = Profile(
        id = 0,
        title = "Mi primer perfil",
        emoji = "🌱",
        categories = categories,
        intervals = when (whenToBlock) {
            OnboardingWhen.ALWAYS -> emptyList()
            OnboardingWhen.WORK_HOURS -> DayOfWeek.entries.take(5).map { ScheduleInterval(it, 9 * 60, 17 * 60) }
            OnboardingWhen.EVENINGS -> DayOfWeek.entries.map { ScheduleInterval(it, 18 * 60, 23 * 60) }
            OnboardingWhen.NIGHT -> ScheduleTemplates.all.first { it.id == "night_relax" }.intervals
        },
    )
}
