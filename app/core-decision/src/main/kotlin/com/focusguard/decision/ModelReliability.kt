package com.focusguard.decision

import kotlin.math.floor
import kotlin.math.nextUp

/** Una imagen de evaluación: lo que puntuó un modelo y lo que es de verdad. */
data class LabeledScore(val probability: Double, val explicit: Boolean)

/** Cuánto fiarse de cada modelo, medido con imágenes de evaluación con respuesta conocida. */
object ModelReliability {

    /** Un modelo malo no pesa cero ni negativo: aporta poco, pero su voto no se invierte. */
    const val MIN_WEIGHT = 0.05

    /**
     * Índice de Youden con el umbral [at]: aciertos entre las explícitas más aciertos entre las seguras, menos uno. Va de −1 (siempre se equivoca)
     * a 1 (perfecto); 0 es lo mismo que lanzar una moneda. Necesita ejemplos de las dos clases.
     */
    fun youden(samples: List<LabeledScore>, at: Double = 0.5): Double {
        val explicit = samples.filter { it.explicit }
        val safe = samples.filterNot { it.explicit }
        require(explicit.isNotEmpty() && safe.isNotEmpty()) { "hacen falta imágenes explícitas y seguras" }
        val truePositives = explicit.count { it.probability >= at }.toDouble() / explicit.size
        val trueNegatives = safe.count { it.probability < at }.toDouble() / safe.size
        return truePositives + trueNegatives - 1
    }

    /** El peso de un modelo: su índice de Youden, con un mínimo. Un modelo perfecto pesa 1. */
    fun weight(samples: List<LabeledScore>, at: Double = 0.5): Double = youden(samples, at).coerceAtLeast(MIN_WEIGHT)

    /**
     * El umbral más bajo con el que, entre las imágenes seguras de evaluación ([safeScores], la puntuación conjunta), se marca como explícita
     * como mucho la fracción [maxFalsePositiveRate]. Más bajo detecta más pero avisa de más; esto fija cuánto se tolera avisar de más.
     */
    fun flagAtForFalsePositiveRate(safeScores: List<Double>, maxFalsePositiveRate: Double): Double {
        require(safeScores.isNotEmpty()) { "hacen falta imágenes seguras" }
        require(maxFalsePositiveRate in 0.0..1.0) { "la tasa va de 0 a 1" }
        val allowed = floor(maxFalsePositiveRate * safeScores.size + 1e-9).toInt()
        if (allowed >= safeScores.size) return 0.0
        // Se marca lo que llega al umbral: justo por encima de la puntuación que ya no se tolera.
        return safeScores.sortedDescending()[allowed].nextUp()
    }
}
