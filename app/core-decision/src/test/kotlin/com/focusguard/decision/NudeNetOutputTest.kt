package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NudeNetOutputTest {

    private fun scores(vararg set: Pair<Int, Double>): List<Double> = MutableList(NudeNetOutput.CLASS_COUNT) { 0.0 }.also { list -> set.forEach { (i, v) -> list[i] = v } }

    @Test fun loExplicitoEsElMaximoDeLasPartesExpuestas() {
        // 2 glúteos expuestos, 3 pecho femenino expuesto, 4 genitales femeninos expuestos, 6 ano expuesto, 14 genitales masculinos expuestos.
        assertEquals(0.7, NudeNetOutput.explicitProbability(scores(2 to 0.3, 3 to 0.7, 14 to 0.1))!!, 1e-9)
        assertEquals(0.9, NudeNetOutput.explicitProbability(scores(14 to 0.9))!!, 1e-9)
        assertEquals(0.4, NudeNetOutput.explicitProbability(scores(6 to 0.4))!!, 1e-9)
        assertEquals(0.5, NudeNetOutput.explicitProbability(scores(4 to 0.5))!!, 1e-9)
    }

    @Test fun loSugerenteEsElMaximoDeLasPartesCubiertas() {
        // 0 genitales femeninos cubiertos, 15 ano cubierto, 16 pecho femenino cubierto, 17 glúteos cubiertos.
        assertEquals(0.6, NudeNetOutput.suggestiveProbability(scores(16 to 0.6, 17 to 0.2))!!, 1e-9)
        assertEquals(0.8, NudeNetOutput.suggestiveProbability(scores(0 to 0.8))!!, 1e-9)
        assertEquals(0.3, NudeNetOutput.suggestiveProbability(scores(15 to 0.3))!!, 1e-9)
    }

    @Test fun lasDemasPartesNoCuentan() {
        // 1 cara, 5 pecho masculino, 7 y 9 pies, 8 y 13 vientre, 10 y 11 axilas, 12 cara masculina.
        val others = scores(1 to 0.99, 5 to 0.99, 7 to 0.99, 8 to 0.99, 9 to 0.99, 10 to 0.99, 11 to 0.99, 12 to 0.99, 13 to 0.99)
        assertEquals(0.0, NudeNetOutput.explicitProbability(others)!!, 1e-9)
        assertEquals(0.0, NudeNetOutput.suggestiveProbability(others)!!, 1e-9)
    }

    @Test fun unaSalidaConLaFormaEquivocadaNoSePuntua() {
        assertNull(NudeNetOutput.explicitProbability(List(5) { 0.1 }))
        assertNull(NudeNetOutput.suggestiveProbability(emptyList()))
        assertNull(NudeNetOutput.explicitProbability(List(19) { 0.1 }))
    }

    @Test fun losValoresNoNumericosONegativosOMayoresQueUnoSeRechazan() {
        assertNull(NudeNetOutput.explicitProbability(scores(2 to Double.NaN)))
        assertNull(NudeNetOutput.explicitProbability(scores(2 to -0.1)))
        assertNull(NudeNetOutput.suggestiveProbability(scores(16 to 1.5)))
        assertNull(NudeNetOutput.explicitProbability(scores(2 to Double.POSITIVE_INFINITY)))
    }

    @Test fun losPesosDeCadaModeloSonLosMedidos() {
        assertEquals(0.70, ImageWeights.GANTMAN_EXPLICIT, 1e-9)
        assertEquals(0.59, ImageWeights.NUDENET_EXPLICIT, 1e-9)
        assertEquals(0.30, ImageWeights.GANTMAN_SUGGESTIVE, 1e-9)
        assertEquals(0.70, ImageWeights.NUDENET_SUGGESTIVE, 1e-9)
    }

    @Test fun losUmbralesDeCadaConjuntoDeModelosSonValidosYDecrecen() {
        for (models in ImageModels.entries) for (tier in ImageTier.entries) {
            val flags = (ImageSensitivity.MIN..ImageSensitivity.MAX).map { ImageSensitivity.thresholds(tier, it, models) }.map { it.flagAt }
            assertEquals(flags.sortedDescending(), flags, "$models $tier")
        }
    }

    @Test fun conLosDosModelosLosUmbralesSonMasBajosQueConUnoSolo() {
        // La puntuación conjunta de dos modelos que casi nunca coinciden en un falso aviso es mucho más baja.
        val one = ImageSensitivity.thresholds(ImageTier.EXPLICIT, ImageSensitivity.DEFAULT, ImageModels.GANTMAN).flagAt
        val two = ImageSensitivity.thresholds(ImageTier.EXPLICIT, ImageSensitivity.DEFAULT, ImageModels.GANTMAN_NUDENET).flagAt
        assertEquals(0.30, two, 1e-9)
        assertEquals(0.84, one, 1e-9)
    }
}
