package com.focusguard.decision

import com.focusguard.model.ForegroundState

/** Cuánto riesgo de contenido que dispare tiene lo que hay en pantalla; decide cada cuánto se captura (el factor multiplica el tiempo base). */
enum class CaptureRisk(val factor: Double) {
    /** Espaciado: un navegador en una web que no es de redes (las direcciones y las palabras ya cubren mucho de lo explícito). */
    LOW(2.0),

    NORMAL(1.0),

    /** Más seguido: redes sociales, vídeo, fotos y citas, en una app o en su web. Es donde más contenido que dispara aparece. */
    HIGH(0.5),
}

/**
 * Qué se está mirando: [key] distingue una app o una web de otra (la subida gradual del tiempo se lleva por cada una) y [risk] cuánto riesgo tiene.
 * En un navegador la clave es la web, no el navegador: cambiar de página empieza de nuevo.
 */
data class CaptureTarget(val key: String, val risk: CaptureRisk, val isBrowser: Boolean = false)

object CaptureRiskClassifier {

    /** Las categorías (ids de `AppCategory`) donde se mira más seguido. */
    val HIGH_RISK_CATEGORIES = setOf("social", "video", "photo", "dating")

    /**
     * En una app, el riesgo es el de su categoría. En un navegador manda la **web**: de redes, más seguido; cualquier otra, más espaciado. Si en un
     * navegador no se puede leer la dirección, se trata como una web cualquiera (espaciado): si algo dudoso aparece, el programador baja el tiempo igualmente.
     */
    fun classify(state: ForegroundState, categories: CategoryTable, highRisk: Set<String> = HIGH_RISK_CATEGORIES): CaptureTarget {
        if (state.isBrowser) {
            val host = UrlNormalizer.hostOrNull(state.url)
            val risk = if (state.url?.let(categories::ofUrl) in highRisk) CaptureRisk.HIGH else CaptureRisk.LOW
            return CaptureTarget(host ?: state.packageName, risk, isBrowser = true)
        }
        val risk = if (categories.ofPackage(state.packageName) in highRisk) CaptureRisk.HIGH else CaptureRisk.NORMAL
        return CaptureTarget(state.packageName, risk)
    }
}
