package com.focusguard.decision

import kotlin.math.exp
import kotlin.math.ln

/**
 * Un modelo que puede puntuar una captura. [stage] es el nivel de la cascada en que entra (1 = en todas las capturas, 2 = solo si el nivel 1 duda).
 * [weight] es lo que pesa su voto, según lo fiable que haya sido ([ModelReliability]). [slope] y [offset] calibran su puntuación (Platt): las
 * probabilidades de modelos distintos no significan lo mismo, y así «0,8» quiere decir lo mismo en todos. Sin calibrar (1 y 0) la puntuación pasa tal cual.
 */
data class ModelSpec(
    val id: String,
    val stage: Int,
    val weight: Double = 1.0,
    val slope: Double = 1.0,
    val offset: Double = 0.0,
) {
    init {
        require(id.isNotBlank()) { "el modelo necesita un nombre" }
        require(stage >= 1) { "el nivel empieza en 1" }
        require(weight > 0 && weight.isFinite()) { "el peso debe ser positivo" }
        require(slope > 0 && slope.isFinite() && offset.isFinite()) { "calibración no válida" }
    }
}

/**
 * Dónde se decide. Entre [clearlySafe] y [clearlyExplicit] la puntuación se considera dudosa y se pide opinión al siguiente nivel; al final de la
 * cascada manda [flagAt]. Los valores de serie son de partida: se ajustan con imágenes de evaluación ([ModelReliability.flagAtForFalsePositiveRate]).
 */
data class ImageThresholds(
    val clearlySafe: Double = 0.15,
    val flagAt: Double = 0.5,
    val clearlyExplicit: Double = 0.85,
) {
    init {
        require(clearlySafe > 0 && clearlySafe <= flagAt && flagAt <= clearlyExplicit && clearlyExplicit < 1) { "umbrales fuera de orden" }
    }
}

enum class ImageVerdict { SAFE, EXPLICIT }

/**
 * [probability] es la puntuación conjunta de los modelos que votaron ([modelsRun]). [borderline]: la decisión la tomó el umbral final y no una
 * puntuación clara; sirve para vigilar más de cerca (ver [CaptureScheduler]).
 */
data class ImageResult(val verdict: ImageVerdict, val probability: Double, val modelsRun: List<String>, val borderline: Boolean = false) {
    /** ¿Merece mirar más a menudo? Lo explícito y lo dudoso, sí; lo claramente seguro, no. */
    val suspicious: Boolean get() = verdict == ImageVerdict.EXPLICIT || borderline
}

/** Voto ponderado: la media de las puntuaciones calibradas, ponderadas por el peso de cada modelo, en escala logarítmica. */
object EnsembleVote {

    /** Evita infinitos: una probabilidad de 0 o 1 exactos pesaría sin límite en la media. */
    private const val EPSILON = 1e-4

    fun logit(p: Double): Double {
        val clamped = p.coerceIn(EPSILON, 1 - EPSILON)
        return ln(clamped / (1 - clamped))
    }

    fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    /** La puntuación de [spec] ya calibrada, en escala logarítmica. */
    fun calibratedLogit(spec: ModelSpec, probability: Double): Double = spec.slope * logit(probability) + spec.offset

    /** La probabilidad conjunta de los [votes] (modelo y su puntuación). Normaliza por los pesos de quien votó: no depende de cuántos modelos haya. */
    fun pool(votes: List<Pair<ModelSpec, Double>>): Double {
        require(votes.isNotEmpty()) { "nadie votó" }
        val totalWeight = votes.sumOf { it.first.weight }
        val mean = votes.sumOf { (spec, p) -> spec.weight * calibratedLogit(spec, p) } / totalWeight
        return sigmoid(mean)
    }
}

/** La cascada: el nivel 1 puntúa siempre y solo si duda se consultan los siguientes, votando todos los que ya hablaron. */
object ImageCascade {

    /**
     * [score] devuelve la puntuación de un modelo (probabilidad de explícito, entre 0 y 1) o null si no pudo (modelo ausente, error). Una puntuación
     * fuera de rango o no numérica se trata como un fallo. Devuelve null si ningún modelo pudo puntuar: eso no es «seguro», y quien llama no lo cuenta.
     */
    fun evaluate(models: List<ModelSpec>, thresholds: ImageThresholds, score: (ModelSpec) -> Double?): ImageResult? {
        require(models.map { it.id }.toSet().size == models.size) { "modelos repetidos" }
        val votes = mutableListOf<Pair<ModelSpec, Double>>()
        for (stage in models.groupBy { it.stage }.toSortedMap().values) {
            for (spec in stage) {
                val p = score(spec)?.takeIf { it.isFinite() && it in 0.0..1.0 } ?: continue
                votes += spec to p
            }
            if (votes.isEmpty()) continue
            val pooled = EnsembleVote.pool(votes)
            val ran = votes.map { it.first.id }
            if (pooled < thresholds.clearlySafe) return ImageResult(ImageVerdict.SAFE, pooled, ran)
            if (pooled >= thresholds.clearlyExplicit) return ImageResult(ImageVerdict.EXPLICIT, pooled, ran)
        }
        if (votes.isEmpty()) return null
        val pooled = EnsembleVote.pool(votes)
        val verdict = if (pooled >= thresholds.flagAt) ImageVerdict.EXPLICIT else ImageVerdict.SAFE
        return ImageResult(verdict, pooled, votes.map { it.first.id }, borderline = true)
    }
}
