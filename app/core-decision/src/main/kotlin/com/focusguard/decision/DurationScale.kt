package com.focusguard.decision

/**
 * Las duraciones que se eligen con el control deslizante y los pasos de «− +»: más finas al principio, donde importan los minutos, y
 * más gruesas después, donde una hora arriba o abajo da igual. Así 24 h caben en un deslizador que se puede manejar con el pulgar.
 */
object DurationScale {

    /** De 5 en 5 hasta 2 h, de 15 en 15 hasta 6 h y de 30 en 30 hasta [maxMinutes] (siempre incluido si cabe). */
    fun stops(maxMinutes: Int): List<Int> {
        require(maxMinutes >= 5) { "el máximo debe ser al menos 5 minutos" }
        val values = (5..120 step 5) + (135..360 step 15) + (390..1440 step 30)
        return (values.filter { it <= maxMinutes } + maxMinutes).distinct().sorted()
    }

    /** Posición de [stops] más cercana a [minutes]; un valor entre dos se queda con el más bajo. */
    fun nearestIndex(stops: List<Int>, minutes: Int): Int {
        val clamped = minutes.coerceIn(stops.first(), stops.last())
        return stops.indices.minBy { Math.abs(stops[it] - clamped) }
    }

    /** El valor tras pulsar «+» ([direction] 1) o «−» (-1); [current] vacío o inválido cuenta como el mínimo. */
    fun step(current: Int?, direction: Int, step: Int, minimum: Int, maximum: Int): Int {
        val from = current ?: minimum
        return (from + direction * step).coerceIn(minimum, maximum)
    }
}
