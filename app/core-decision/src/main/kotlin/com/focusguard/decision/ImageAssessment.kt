package com.focusguard.decision

/** Los dos niveles de la detección: lo explícito (partes íntimas, porno, hentai) y lo sugerente (ropa de baño, ropa ajustada, partes cubiertas). */
enum class ImageTier { EXPLICIT, SUGGESTIVE }

enum class ImageLevel { SAFE, SUGGESTIVE, EXPLICIT }

/**
 * Lo que se concluye de una captura con los dos niveles. Lo explícito manda sobre lo sugerente. [explicit] y [suggestive] son el resultado de la
 * cascada de cada nivel (null si ningún modelo pudo puntuarlo).
 */
data class ImageAssessment(val level: ImageLevel, val explicit: ImageResult?, val suggestive: ImageResult?) {
    /**
     * ¿Merece mirar más a menudo? Lo sugerente y lo explícito, sí; también lo dudoso de cualquiera de los dos niveles. **Depende de la sensibilidad**, a
     * propósito: con más sensibilidad los umbrales son más bajos, salen más capturas dudosas y por tanto se vigila más. Los tiempos de base, en cambio, no
     * dependen de ella ([CaptureTiming]).
     */
    val suspicious: Boolean get() = level != ImageLevel.SAFE || explicit?.borderline == true || suggestive?.borderline == true
}

/**
 * El control de sensibilidad: cinco pasos, de «Mínima» a «Máxima». Cada paso fija los umbrales de los dos niveles: a más sensibilidad, umbrales más bajos,
 * es decir, se marca más, con más falsos avisos. Los valores de partida salen de una evaluación con 83 imágenes del usuario, calculados
 * para cada conjunto de modelos ([ImageModels]); son **provisionales** y solo valen para el conjunto con el que se calcularon.
 */
object ImageSensitivity {

    const val MIN = 1
    const val MAX = 5
    /** De serie «Baja»: con la «Media» y el modo dinámico subiéndola, las capturas normales de Instagram acababan marcadas (ver [FLAGS]). */
    const val DEFAULT = 2

    /**
     * Umbral de marcado por paso, para cada conjunto de modelos. Con solo GantMan, el nivel explícito es porno más hentai (de 0 falsos avisos con margen a cerca del
     * 10 % de las seguras de la muestra) y el sugerente la clase «sexy». Con GantMan y NudeNet son los del voto ponderado de los dos, que se calcularon aparte
     * porque esa puntuación conjunta es mucho más baja: con 83 imágenes del usuario, los pasos 1 a 3 no marcan ninguna segura y el 4 y el 5 marcan el 10 % y el 20 %.
     *
     * Segunda tanda (2026-11-08): 13 capturas de Instagram que el usuario marcó como falsos avisos puntúan hasta 0,167 en lo explícito y 0,202 en lo sugerente.
     * Con los pasos 4 y 5 de antes (0,17 y 0,10; 0,21 y 0,10) se marcaban casi todas, y el modo dinámico lleva a esos pasos solo (base más un paso por nivel).
     * Se subieron los pasos 4 y 5 por encima de esas cifras; los pasos 1 a 3 quedan como estaban. Cuanto más se acercan a ellos, menos distingue el modo dinámico.
     */
    private val FLAGS: Map<Pair<ImageModels, ImageTier>, DoubleArray> = mapOf(
        (ImageModels.GANTMAN to ImageTier.EXPLICIT) to doubleArrayOf(0.92, 0.84, 0.72, 0.52, 0.38),
        (ImageModels.GANTMAN to ImageTier.SUGGESTIVE) to doubleArrayOf(0.60, 0.40, 0.25, 0.09, 0.03),
        (ImageModels.GANTMAN_NUDENET to ImageTier.EXPLICIT) to doubleArrayOf(0.45, 0.30, 0.21, 0.20, 0.18),
        (ImageModels.GANTMAN_NUDENET to ImageTier.SUGGESTIVE) to doubleArrayOf(0.60, 0.45, 0.35, 0.28, 0.24),
    )

    fun clamp(level: Int): Int = level.coerceIn(MIN, MAX)

    fun label(level: Int): String = when (clamp(level)) {
        1 -> "Mínima"
        2 -> "Baja"
        3 -> "Media"
        4 -> "Alta"
        else -> "Máxima"
    }

    fun thresholds(tier: ImageTier, level: Int, models: ImageModels = ImageModels.GANTMAN): ImageThresholds {
        val flagAt = FLAGS.getValue(models to tier)[clamp(level) - MIN]
        // Por debajo de la mitad del umbral, claramente seguro; por encima de medio camino hasta 0,99, claramente marcado; en medio, dudoso.
        return ImageThresholds(clearlySafe = flagAt / 2, flagAt = flagAt, clearlyExplicit = flagAt + (0.99 - flagAt) / 2)
    }

    /** Bajar la sensibilidad debilita la protección (se marca menos): es lo que pide aprobación. Subirla, no. */
    fun weakens(from: Int, to: Int): Boolean = clamp(to) < clamp(from)

    fun encode(level: Int): String = clamp(level).toString()

    fun decode(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in MIN..MAX }
}

object ImageAnalyzer {

    /**
     * Pasa una captura por la cascada de cada nivel. [score] da la puntuación de un modelo para un nivel (null si no pudo). Devuelve null si ningún modelo
     * pudo puntuar ninguno de los dos niveles: eso no es «seguro» y quien llama no lo cuenta.
     */
    fun analyze(
        explicitModels: List<ModelSpec>,
        suggestiveModels: List<ModelSpec>,
        sensitivity: Int,
        models: ImageModels = ImageModels.GANTMAN,
        score: (ModelSpec, ImageTier) -> Double?,
    ): ImageAssessment? {
        val explicit = ImageCascade.evaluate(explicitModels, ImageSensitivity.thresholds(ImageTier.EXPLICIT, sensitivity, models)) { score(it, ImageTier.EXPLICIT) }
        val suggestive = ImageCascade.evaluate(suggestiveModels, ImageSensitivity.thresholds(ImageTier.SUGGESTIVE, sensitivity, models)) { score(it, ImageTier.SUGGESTIVE) }
        if (explicit == null && suggestive == null) return null
        val level = when {
            explicit?.verdict == ImageVerdict.EXPLICIT -> ImageLevel.EXPLICIT
            suggestive?.verdict == ImageVerdict.EXPLICIT -> ImageLevel.SUGGESTIVE
            else -> ImageLevel.SAFE
        }
        return ImageAssessment(level, explicit, suggestive)
    }
}
