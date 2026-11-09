package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageAssessmentTest {

    private val explicitModel = ModelSpec("g.explicit", stage = 1)
    private val suggestiveModel = ModelSpec("g.suggestive", stage = 1)

    private fun analyze(explicit: Double?, suggestive: Double?, level: Int = ImageSensitivity.DEFAULT) =
        ImageAnalyzer.analyze(listOf(explicitModel), listOf(suggestiveModel), level) { _, tier -> if (tier == ImageTier.EXPLICIT) explicit else suggestive }

    /**
     * Las 13 capturas de Instagram que el usuario marcó como falsos avisos (2026-11-08), puntuadas con los modelos de la app:
     * GantMan explícito, NudeNet expuestas, GantMan «sexy» y NudeNet cubiertas. Con los dos modelos no deben marcarse en ningún paso de sensibilidad.
     */
    private val falsePositives = listOf(
        listOf(0.063, 0.076, 0.001, 0.261), listOf(0.495, 0.007, 0.000, 0.003), listOf(0.204, 0.084, 0.003, 0.007), listOf(0.004, 0.002, 0.001, 0.726),
        listOf(0.045, 0.005, 0.008, 0.115), listOf(0.164, 0.126, 0.007, 0.004), listOf(0.012, 0.021, 0.001, 0.192), listOf(0.575, 0.012, 0.000, 0.126),
        listOf(0.782, 0.006, 0.002, 0.023), listOf(0.267, 0.023, 0.016, 0.276), listOf(0.219, 0.034, 0.011, 0.238), listOf(0.650, 0.009, 0.079, 0.023),
        listOf(0.203, 0.132, 0.001, 0.227),
    )

    @Test fun losFalsosAvisosDeInstagramNoSeMarcanEnNingunPasoConLosDosModelos() {
        val explicit = listOf(ModelSpec("g.explicit", 1, ImageWeights.GANTMAN_EXPLICIT), ModelSpec("n.explicit", 1, ImageWeights.NUDENET_EXPLICIT))
        val suggestive = listOf(ModelSpec("g.suggestive", 1, ImageWeights.GANTMAN_SUGGESTIVE), ModelSpec("n.suggestive", 1, ImageWeights.NUDENET_SUGGESTIVE))
        for ((i, shot) in falsePositives.withIndex()) for (level in ImageSensitivity.MIN..ImageSensitivity.MAX) {
            val a = ImageAnalyzer.analyze(explicit, suggestive, level, ImageModels.GANTMAN_NUDENET) { spec, tier ->
                val gantman = spec.id.startsWith("g.")
                if (tier == ImageTier.EXPLICIT) (if (gantman) shot[0] else shot[1]) else (if (gantman) shot[2] else shot[3])
            }!!
            assertEquals(ImageLevel.SAFE, a.level, "captura ${i + 1} en el paso $level")
        }
    }

    @Test fun unaImagenNeutraEsSegura() {
        val a = analyze(explicit = 0.02, suggestive = 0.01)!!
        assertEquals(ImageLevel.SAFE, a.level)
        assertFalse(a.suspicious)
    }

    @Test fun loExplicitoEsExplicito() {
        assertEquals(ImageLevel.EXPLICIT, analyze(explicit = 0.95, suggestive = 0.0)!!.level)
    }

    @Test fun loSugerenteSinSerExplicitoEsSugerente() {
        val a = analyze(explicit = 0.05, suggestive = 0.8)!!
        assertEquals(ImageLevel.SUGGESTIVE, a.level)
        assertTrue(a.suspicious)
    }

    @Test fun loExplicitoMandaSobreLoSugerente() {
        assertEquals(ImageLevel.EXPLICIT, analyze(explicit = 0.95, suggestive = 0.9)!!.level)
    }

    @Test fun unNivelQueNoSePudoPuntuarNoAnulaAlOtro() {
        assertEquals(ImageLevel.SUGGESTIVE, analyze(explicit = null, suggestive = 0.9)!!.level)
        assertEquals(ImageLevel.EXPLICIT, analyze(explicit = 0.95, suggestive = null)!!.level)
    }

    @Test fun siNingunNivelSePuedePuntuarNoSeDaNadaPorSeguro() {
        assertNull(analyze(explicit = null, suggestive = null))
    }

    @Test fun unaPuntuacionDudosaHaceSospechosaUnaImagenSegura() {
        // 0,6 está entre «claramente seguro» y el umbral (0,72) con sensibilidad media: se queda segura, pero se mira más de cerca.
        val a = analyze(explicit = 0.6, suggestive = 0.0)!!
        assertEquals(ImageLevel.SAFE, a.level)
        assertTrue(a.suspicious)
    }

    @Test fun conMasSensibilidadHayMasCapturasDudosasYPorTantoMasVigilancia() {
        // Una puntuación explícita de 0,3: con la sensibilidad máxima ya cae en la zona dudosa y se vigila más; con la media o la mínima, no.
        assertTrue(analyze(0.3, 0.0, ImageSensitivity.MAX)!!.suspicious)
        assertFalse(analyze(0.3, 0.0, ImageSensitivity.DEFAULT)!!.suspicious)
        assertFalse(analyze(0.3, 0.0, ImageSensitivity.MIN)!!.suspicious)
        // Y nunca al revés: subir la sensibilidad no hace que una captura deje de ser sospechosa.
        for ((explicit, suggestive) in listOf(0.02 to 0.0, 0.3 to 0.0, 0.6 to 0.0, 0.8 to 0.0, 0.95 to 0.0, 0.0 to 0.2, 0.0 to 0.8, 0.1 to 0.05)) {
            val suspicion = (ImageSensitivity.MIN..ImageSensitivity.MAX).map { analyze(explicit, suggestive, it)!!.suspicious }
            assertEquals(suspicion.sortedBy { it }, suspicion, "explícito $explicit, sugerente $suggestive: $suspicion")
        }
    }

    @Test fun losTiemposDeBaseNoDependenDeLaSensibilidad() {
        // El programador no recibe la sensibilidad: con el mismo riesgo y la misma cuenta de seguras, el tiempo es el mismo.
        val a = CaptureScheduler()
        val b = CaptureScheduler()
        assertEquals(a.intervalMs("x", CaptureRisk.NORMAL, 0), b.intervalMs("x", CaptureRisk.NORMAL, 0))
        assertEquals(1_000L, a.intervalMs("x", CaptureRisk.NORMAL, 0))
    }

    @Test fun conMasSensibilidadSeMarcaLoQueConMenosNo() {
        // Una puntuación explícita de 0,8: la marca la sensibilidad media (umbral 0,72) y no la baja (0,84).
        assertEquals(ImageLevel.EXPLICIT, analyze(explicit = 0.8, suggestive = 0.0, level = 3)!!.level)
        assertEquals(ImageLevel.SAFE, analyze(explicit = 0.8, suggestive = 0.0, level = 2)!!.level)
        assertEquals(ImageLevel.EXPLICIT, analyze(explicit = 0.8, suggestive = 0.0, level = 5)!!.level)
        assertEquals(ImageLevel.SAFE, analyze(explicit = 0.8, suggestive = 0.0, level = 1)!!.level)
    }

    @Test fun elNivelSugerenteSeMarcaMasFacilQueElExplicito() {
        // 0,3 de «sexy» ya es sugerente con la sensibilidad media (umbral 0,25), pero 0,3 de explícito no es nada.
        assertEquals(ImageLevel.SUGGESTIVE, analyze(explicit = 0.3, suggestive = 0.3, level = 3)!!.level)
        assertEquals(ImageLevel.SAFE, analyze(explicit = 0.3, suggestive = 0.0, level = 3)!!.level)
    }

    @Test fun losUmbralesBajanAlSubirLaSensibilidadEnLosDosNiveles() {
        for (tier in ImageTier.entries) {
            val flags = (ImageSensitivity.MIN..ImageSensitivity.MAX).map { ImageSensitivity.thresholds(tier, it).flagAt }
            assertEquals(flags.sortedDescending(), flags)
            assertEquals(flags.size, flags.toSet().size)
        }
    }

    @Test fun cadaPasoDaUmbralesEnOrdenValido() {
        for (tier in ImageTier.entries) for (level in ImageSensitivity.MIN..ImageSensitivity.MAX) {
            val t = ImageSensitivity.thresholds(tier, level)
            assertTrue(t.clearlySafe < t.flagAt && t.flagAt <= t.clearlyExplicit && t.clearlyExplicit < 1.0)
        }
    }

    @Test fun unPasoFueraDeRangoSeAjusta() {
        assertEquals(ImageSensitivity.thresholds(ImageTier.EXPLICIT, 1), ImageSensitivity.thresholds(ImageTier.EXPLICIT, -4))
        assertEquals(ImageSensitivity.thresholds(ImageTier.EXPLICIT, 5), ImageSensitivity.thresholds(ImageTier.EXPLICIT, 99))
        assertEquals(1, ImageSensitivity.clamp(0))
        assertEquals(5, ImageSensitivity.clamp(7))
    }

    @Test fun cadaPasoTieneNombre() {
        assertEquals(listOf("Mínima", "Baja", "Media", "Alta", "Máxima"), (1..5).map(ImageSensitivity::label))
    }

    @Test fun soloBajarLaSensibilidadDebilita() {
        assertTrue(ImageSensitivity.weakens(3, 2))
        assertTrue(ImageSensitivity.weakens(5, 1))
        assertFalse(ImageSensitivity.weakens(3, 4))
        assertFalse(ImageSensitivity.weakens(3, 3))
        // Un valor fuera de rango se compara ya ajustado: pedir 0 estando en 1 no es bajar.
        assertFalse(ImageSensitivity.weakens(1, 0))
    }

    @Test fun elPasoSeGuardaYSeLeeComoTexto() {
        assertEquals("4", ImageSensitivity.encode(4))
        assertEquals(4, ImageSensitivity.decode("4"))
        assertEquals(2, ImageSensitivity.decode(" 2 "))
        assertNull(ImageSensitivity.decode("9"))
        assertNull(ImageSensitivity.decode("alta"))
        assertNull(ImageSensitivity.decode(""))
    }

    @Test fun laClaseSexyDeGantManEsElNivelSugerente() {
        // Orden: dibujos, hentai, neutro, porno, sexy.
        assertEquals(0.7, GantManOutput.suggestiveProbability(listOf(0.0, 0.0, 0.3, 0.0, 0.7))!!, 1e-9)
        assertNull(GantManOutput.suggestiveProbability(listOf(0.5, 0.5)))
        assertNull(GantManOutput.suggestiveProbability(listOf(0.9, 0.9, 0.9, 0.9, 0.9)))
    }
}
