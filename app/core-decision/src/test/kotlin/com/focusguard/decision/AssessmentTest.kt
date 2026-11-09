package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssessmentTest {
    private fun answers(value: Int?) = List(Assessment.QUESTIONS.size) { value }

    @Test fun haceFaltaContestarLasDiezYElMaximoEsCuarenta() {
        assertEquals(10, Assessment.QUESTIONS.size)
        assertEquals(5, Assessment.OPTIONS.size)
        assertEquals(Assessment.MAX_SCORE, Assessment.score(answers(4)))
        assertEquals(0, Assessment.score(answers(0)))
    }

    @Test fun laPuntuacionEsLaSumaDeLasRespuestas() {
        assertEquals(1 + 2 + 3 + 4 + 0 + 1 + 2 + 3 + 4 + 0, Assessment.score(listOf(1, 2, 3, 4, 0, 1, 2, 3, 4, 0)))
    }

    @Test fun sinTodasLasRespuestasOFueraDeRangoNoHayPuntuacion() {
        assertNull(Assessment.score(emptyList()))
        assertNull(Assessment.score(answers(2).dropLast(1)))
        assertNull(Assessment.score(answers(2) + 1))
        assertNull(Assessment.score(answers(2).toMutableList().also { it[3] = null }))
        assertNull(Assessment.score(answers(2).toMutableList().also { it[0] = 5 }))
        assertNull(Assessment.score(answers(2).toMutableList().also { it[0] = -1 }))
    }

    @Test fun losTramosCubrenDe0A40SinHuecosNiSolapes() {
        assertEquals(AssessmentBand.LOW, Assessment.band(0))
        assertEquals(AssessmentBand.LOW, Assessment.band(9))
        assertEquals(AssessmentBand.MODERATE, Assessment.band(10))
        assertEquals(AssessmentBand.MODERATE, Assessment.band(19))
        assertEquals(AssessmentBand.HIGH, Assessment.band(20))
        assertEquals(AssessmentBand.HIGH, Assessment.band(29))
        assertEquals(AssessmentBand.VERY_HIGH, Assessment.band(30))
        assertEquals(AssessmentBand.VERY_HIGH, Assessment.band(Assessment.MAX_SCORE))
    }

    @Test fun elTramoMasAltoRecomiendaHablarConUnProfesional() {
        assertTrue("profesional" in AssessmentBand.VERY_HIGH.advice)
        assertTrue(AssessmentBand.entries.all { it.label.isNotBlank() && it.advice.isNotBlank() })
        assertTrue("diagnóstico" in Assessment.DISCLAIMER)
    }

    @Test fun lasComparacionesSeDicenSinJuzgar() {
        assertNull(Assessment.comparison(null, 20))
        assertEquals("Mejor que la última vez: 5 puntos menos.", Assessment.comparison(25, 20))
        assertEquals("Mejor que la última vez: 1 punto menos.", Assessment.comparison(21, 20))
        assertTrue(Assessment.comparison(10, 12)!!.startsWith("Un poco más que la última vez: 2 puntos más."))
        assertEquals("Igual que la última vez.", Assessment.comparison(7, 7))
    }
}
