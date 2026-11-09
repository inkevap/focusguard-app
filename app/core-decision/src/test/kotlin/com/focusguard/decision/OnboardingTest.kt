package com.focusguard.decision

import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OnboardingTest {

    @Test
    fun elPrimerPerfilLlevaLasCategoriasYElHorarioElegidos() {
        val p = Onboarding.firstProfile(setOf("social", "games"), OnboardingWhen.WORK_HOURS)
        assertEquals(setOf("social", "games"), p.categories)
        assertEquals(5, p.intervals.size)
        assertTrue(p.intervals.none { it.day == DayOfWeek.SATURDAY || it.day == DayOfWeek.SUNDAY })
        assertTrue(p.enabled)
        assertEquals(0, p.id)
    }

    @Test
    fun lasCategoriasSugeridasNoSeRepitenYTienenNombre() {
        assertEquals(Onboarding.SUGGESTED_CATEGORIES.size, Onboarding.SUGGESTED_CATEGORIES.map { it.id }.toSet().size)
        assertTrue(Onboarding.SUGGESTED_CATEGORIES.all { it.label.isNotBlank() })
    }

    @Test
    fun cadaMetaProponeCategoriasConocidasYUnHorario() {
        val known = com.focusguard.model.AppCategory.entries.map { it.id }.toSet()
        OnboardingGoal.entries.forEach { goal ->
            assertTrue(goal.label.isNotBlank() && goal.hint.isNotBlank(), goal.name)
            assertTrue(known.containsAll(goal.categoryIds), goal.name)
        }
        assertEquals(OnboardingWhen.NIGHT, OnboardingGoal.SLEEP.whenToBlock)
        assertEquals(OnboardingWhen.WORK_HOURS, OnboardingGoal.FOCUS.whenToBlock)
        assertTrue(OnboardingGoal.ADULT.categoryIds.isEmpty())
        assertEquals(Onboarding.SUGGESTED_CATEGORIES.map { it.id }.toSet(), OnboardingGoal.EVERYTHING.categoryIds)
    }

    @Test
    fun cadaOpcionDeCuandoDaSuHorario() {
        assertEquals(emptyList(), Onboarding.firstProfile(emptySet(), OnboardingWhen.ALWAYS).intervals)
        assertEquals(7, Onboarding.firstProfile(emptySet(), OnboardingWhen.EVENINGS).intervals.size)
        val night = Onboarding.firstProfile(emptySet(), OnboardingWhen.NIGHT).intervals.first()
        assertEquals(22 * 60, night.startMinute)
        assertEquals(7 * 60, night.endMinute)
    }
}
