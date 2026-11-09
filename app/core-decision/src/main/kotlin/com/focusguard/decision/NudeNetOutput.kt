package com.focusguard.decision

/**
 * La salida de NudeNet 320n (YOLOv8n): 18 clases de partes del cuerpo. Quien lo ejecuta da la mayor puntuación de cada clase en toda la imagen, en este orden
 * (el de las etiquetas del modelo). **Explícito** es el máximo de las cinco partes íntimas *expuestas*; **sugerente**, el de las cuatro partes íntimas
 * *cubiertas*. Las demás (cara, pies, vientre, axilas, pecho masculino) no cuentan.
 */
object NudeNetOutput {

    const val CLASS_COUNT = 18

    /** Glúteos, pecho femenino, genitales femeninos, ano y genitales masculinos, expuestos. */
    private val EXPOSED = intArrayOf(2, 3, 4, 6, 14)

    /** Genitales femeninos, ano, pecho femenino y glúteos, cubiertos. */
    private val COVERED = intArrayOf(0, 15, 16, 17)

    private fun valid(maxPerClass: List<Double>) = maxPerClass.size == CLASS_COUNT && maxPerClass.all { it.isFinite() && it >= 0.0 && it <= 1.0 }

    fun explicitProbability(maxPerClass: List<Double>): Double? =
        if (valid(maxPerClass)) EXPOSED.maxOf { maxPerClass[it] } else null

    fun suggestiveProbability(maxPerClass: List<Double>): Double? =
        if (valid(maxPerClass)) COVERED.maxOf { maxPerClass[it] } else null
}

/** Qué modelos puntuaron una captura: los umbrales de la sensibilidad se calcularon para cada conjunto y no sirven para otro. */
enum class ImageModels { GANTMAN, GANTMAN_NUDENET }

/**
 * Pesos de cada modelo en el voto de cada nivel, por su fiabilidad medida con 83 imágenes del usuario (índice de Youden; en el nivel sugerente, contra las
 * imágenes dudosas). Provisionales.
 */
object ImageWeights {
    const val GANTMAN_EXPLICIT = 0.70
    const val NUDENET_EXPLICIT = 0.59
    const val GANTMAN_SUGGESTIVE = 0.30
    const val NUDENET_SUGGESTIVE = 0.70
}
