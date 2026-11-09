package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelReliabilityTest {

    private fun samples(explicit: List<Double>, safe: List<Double>) =
        explicit.map { LabeledScore(it, true) } + safe.map { LabeledScore(it, false) }

    @Test fun unModeloPerfectoPesaUno() {
        val s = samples(explicit = List(5) { 0.9 }, safe = List(5) { 0.1 })
        assertEquals(1.0, ModelReliability.youden(s), 1e-9)
        assertEquals(1.0, ModelReliability.weight(s), 1e-9)
    }

    @Test fun unModeloQueLoMarcaTodoNoAportaNada() {
        val s = samples(explicit = List(5) { 0.9 }, safe = List(5) { 0.6 })
        assertEquals(0.0, ModelReliability.youden(s), 1e-9)
        assertEquals(ModelReliability.MIN_WEIGHT, ModelReliability.weight(s), 1e-9)
    }

    @Test fun unModeloAlRevesNuncaInvierteSuVoto() {
        val s = samples(explicit = List(5) { 0.1 }, safe = List(5) { 0.9 })
        assertEquals(-1.0, ModelReliability.youden(s), 1e-9)
        assertEquals(ModelReliability.MIN_WEIGHT, ModelReliability.weight(s), 1e-9)
    }

    @Test fun unModeloQueAciertaLaMayoriaPesaEnProporcion() {
        val s = samples(explicit = listOf(0.9, 0.9, 0.9, 0.2), safe = listOf(0.1, 0.1, 0.6, 0.1))
        assertEquals(0.5, ModelReliability.weight(s), 1e-9)
    }

    @Test fun haceFaltaVerLasDosClases() {
        assertFailsWith<IllegalArgumentException> { ModelReliability.youden(samples(explicit = listOf(0.9), safe = emptyList())) }
        assertFailsWith<IllegalArgumentException> { ModelReliability.youden(samples(explicit = emptyList(), safe = listOf(0.1))) }
    }

    private val safeScores = listOf(0.05, 0.1, 0.1, 0.2, 0.2, 0.3, 0.3, 0.4, 0.6, 0.7)

    private fun falsePositiveRate(threshold: Double) = safeScores.count { it >= threshold }.toDouble() / safeScores.size

    @Test fun elUmbralRespetaLaTasaDeFalsosAvisosPedida() {
        val at = ModelReliability.flagAtForFalsePositiveRate(safeScores, 0.2)
        assertEquals(0.2, falsePositiveRate(at), 1e-9)
        // Es el más bajo posible: con uno menos, se tolerarían más avisos de más.
        assertTrue(at > 0.4 && at <= 0.6)
    }

    @Test fun conCeroFalsosAvisosNoSeMarcaNingunaSegura() {
        val at = ModelReliability.flagAtForFalsePositiveRate(safeScores, 0.0)
        assertEquals(0.0, falsePositiveRate(at), 1e-9)
    }

    @Test fun siSeToleraTodoElUmbralEsCero() {
        assertEquals(0.0, ModelReliability.flagAtForFalsePositiveRate(safeScores, 1.0), 1e-9)
    }

    @Test fun losParametrosInvalidosSeRechazan() {
        assertFailsWith<IllegalArgumentException> { ModelReliability.flagAtForFalsePositiveRate(emptyList(), 0.1) }
        assertFailsWith<IllegalArgumentException> { ModelReliability.flagAtForFalsePositiveRate(safeScores, 1.5) }
    }
}
