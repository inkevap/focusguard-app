package com.focusguard.decision

import kotlin.math.abs

/**
 * La salida del modelo de GantMan: cinco probabilidades (softmax) en este orden, el de su `class_labels.txt`. Lo explícito es **porno** más
 * **hentai**; «sexy» (ropa de baño, ropa ajustada) y «dibujos» no cuentan, porque un bloqueo por cada una daría más avisos de más que aciertos.
 */
object GantManOutput {

    const val CLASS_COUNT = 5
    private const val DRAWINGS = 0
    private const val HENTAI = 1
    private const val NEUTRAL = 2
    private const val PORN = 3
    private const val SEXY = 4

    /** Una salida de softmax suma 1; se acepta algo de margen por redondeo, y nada más: si no suma así, no es lo que se espera. */
    private const val SUM_TOLERANCE = 0.05

    /** Probabilidad de que la imagen sea explícita, o null si [probabilities] no tiene la forma de la salida del modelo. */
    fun explicitProbability(probabilities: List<Double>): Double? {
        if (probabilities.size != CLASS_COUNT) return null
        if (probabilities.any { !it.isFinite() || it < 0.0 || it > 1.0 + SUM_TOLERANCE }) return null
        if (abs(probabilities.sum() - 1.0) > SUM_TOLERANCE) return null
        return (probabilities[PORN] + probabilities[HENTAI]).coerceIn(0.0, 1.0)
    }

    /** Probabilidad de que la imagen sea sugerente (la clase «sexy»: ropa de baño, ropa ajustada), o null si la salida no tiene la forma esperada. */
    fun suggestiveProbability(probabilities: List<Double>): Double? {
        if (explicitProbability(probabilities) == null) return null
        return probabilities[SEXY].coerceIn(0.0, 1.0)
    }

    /** El nombre de cada clase, solo para registrar resultados. */
    fun label(index: Int): String = when (index) {
        DRAWINGS -> "dibujos"
        HENTAI -> "hentai"
        NEUTRAL -> "neutro"
        PORN -> "porno"
        SEXY -> "sexy"
        else -> "?"
    }
}
