package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageEnsembleTest {

    private val a = ModelSpec("a", stage = 1)
    private val b = ModelSpec("b", stage = 2)
    private val c = ModelSpec("c", stage = 2)
    private val thresholds = ImageThresholds()

    private fun run(models: List<ModelSpec>, scores: Map<String, Double?>, t: ImageThresholds = thresholds) =
        ImageCascade.evaluate(models, t) { scores[it.id] }

    @Test fun unSoloModeloVotaConSuPropiaPuntuacion() {
        assertEquals(0.7, EnsembleVote.pool(listOf(a to 0.7)), 1e-9)
    }

    @Test fun losPesosInclinanElVoto() {
        // a (peso 1) dice 0,9 y b (peso 3) dice 0,1: la media de logits es −ln 3, o sea 0,25.
        val heavy = ModelSpec("b", stage = 1, weight = 3.0)
        assertEquals(0.25, EnsembleVote.pool(listOf(a to 0.9, heavy to 0.1)), 1e-9)
    }

    @Test fun conLosMismosPesosEsLaMediaSimple() {
        assertEquals(0.5, EnsembleVote.pool(listOf(a to 0.9, ModelSpec("b", 1) to 0.1)), 1e-9)
    }

    @Test fun laCalibracionHaceComparablesLasPuntuaciones() {
        // Pendiente 2: la puntuación 0,75 de este modelo equivale a 0,9 en la escala común.
        val steep = ModelSpec("s", stage = 1, slope = 2.0)
        assertEquals(0.9, EnsembleVote.pool(listOf(steep to 0.75)), 1e-9)
    }

    @Test fun unaPuntuacionExtremaNoDaInfinitos() {
        assertEquals(0.5, EnsembleVote.pool(listOf(a to 1.0, ModelSpec("b", 1) to 0.0)), 1e-9)
        assertTrue(EnsembleVote.pool(listOf(a to 1.0)) < 1.0)
    }

    @Test fun siElPrimerModeloEstaSeguroNoSeConsultaNadaMas() {
        val result = run(listOf(a, b, c), mapOf("a" to 0.05, "b" to 0.99, "c" to 0.99))!!
        assertEquals(ImageVerdict.SAFE, result.verdict)
        assertEquals(listOf("a"), result.modelsRun)
        assertFalse(result.suspicious)
    }

    @Test fun siElPrimerModeloEstaMuySeguroDeQueEsExplicitoTampocoSeConsultaNadaMas() {
        val result = run(listOf(a, b, c), mapOf("a" to 0.97, "b" to 0.0, "c" to 0.0))!!
        assertEquals(ImageVerdict.EXPLICIT, result.verdict)
        assertEquals(listOf("a"), result.modelsRun)
        assertTrue(result.suspicious)
    }

    @Test fun siElPrimerModeloDudaVotanTodosLosDelSegundoNivel() {
        val result = run(listOf(a, b, c), mapOf("a" to 0.5, "b" to 0.9, "c" to 0.9))!!
        assertEquals(listOf("a", "b", "c"), result.modelsRun)
        assertEquals(ImageVerdict.EXPLICIT, result.verdict)
        assertEquals(0.8125, result.probability, 1e-3)
        assertTrue(result.borderline)
    }

    @Test fun elSegundoNivelPuedeDesmentirAlPrimero() {
        val result = run(listOf(a, b, c), mapOf("a" to 0.5, "b" to 0.02, "c" to 0.02))!!
        assertEquals(ImageVerdict.SAFE, result.verdict)
        assertEquals(listOf("a", "b", "c"), result.modelsRun)
        assertFalse(result.borderline)
    }

    @Test fun alFinalMandaElUmbralDeMarcado() {
        val low = ImageThresholds(flagAt = 0.3)
        assertEquals(ImageVerdict.EXPLICIT, run(listOf(a), mapOf("a" to 0.4), low)!!.verdict)
        assertEquals(ImageVerdict.SAFE, run(listOf(a), mapOf("a" to 0.4))!!.verdict)
        assertTrue(run(listOf(a), mapOf("a" to 0.4))!!.suspicious)
    }

    @Test fun unModeloQueFallaSeIgnoraYLosDemasSiguen() {
        val result = run(listOf(a, b, c), mapOf("a" to 0.5, "b" to null, "c" to 0.95))!!
        assertEquals(listOf("a", "c"), result.modelsRun)
        assertEquals(ImageVerdict.EXPLICIT, result.verdict)
    }

    @Test fun siFallaElPrimerNivelElSegundoDecide() {
        val result = run(listOf(a, b), mapOf("a" to null, "b" to 0.03))!!
        assertEquals(listOf("b"), result.modelsRun)
        assertEquals(ImageVerdict.SAFE, result.verdict)
    }

    @Test fun siNingunModeloPuedePuntuarNoSeDaNadaPorSeguro() {
        assertNull(run(listOf(a, b), mapOf("a" to null, "b" to null)))
        assertNull(run(emptyList(), emptyMap()))
    }

    @Test fun unaPuntuacionFueraDeRangoONoNumericaCuentaComoFallo() {
        assertNull(run(listOf(a), mapOf("a" to 1.5)))
        assertNull(run(listOf(a), mapOf("a" to Double.NaN)))
        assertNull(run(listOf(a), mapOf("a" to -0.1)))
    }

    @Test fun losModelosNoSePuedenRepetir() {
        assertFailsWith<IllegalArgumentException> { run(listOf(a, ModelSpec("a", stage = 2)), mapOf("a" to 0.5)) }
    }

    @Test fun losUmbralesYModelosInvalidosSeRechazan() {
        assertFailsWith<IllegalArgumentException> { ImageThresholds(clearlySafe = 0.6, flagAt = 0.5) }
        assertFailsWith<IllegalArgumentException> { ImageThresholds(clearlyExplicit = 1.0) }
        assertFailsWith<IllegalArgumentException> { ModelSpec("x", stage = 0) }
        assertFailsWith<IllegalArgumentException> { ModelSpec("x", stage = 1, weight = 0.0) }
        assertFailsWith<IllegalArgumentException> { ModelSpec(" ", stage = 1) }
    }
}
