package com.focusguard.decision

/** Visitas y tiempo acumulados de una web, o del modo incógnito (sin dominio), desde el último vaciado. */
data class WebUsage(val visits: Int = 0, val millis: Long = 0)

/** Una web o el incógnito: en incógnito [host] va siempre vacío, para no guardar qué se visita. */
data class WebKey(val host: String, val incognito: Boolean)

/** Lo que el servicio ve en pantalla en este momento, sin decidir qué se guarda. */
sealed interface WebObservation {
    /** Ningún navegador a la vista, o con la pantalla apagada. */
    data object None : WebObservation

    /** Una web normal; [host] es null si la barra no muestra una dirección legible. */
    data class Web(val host: String?) : WebObservation

    data object Incognito : WebObservation
}

/**
 * Cuenta las visitas y el tiempo en webs a partir de lo que el servicio ve cada segundo (K2). Vive en memoria
 * y se vacía con [drain] para escribir a disco de vez en cuando. Una visita nueva es cambiar de web o volver tras
 * perder de vista el navegador.
 */
class WebUsageCounter {

    private val pending = mutableMapOf<WebKey, WebUsage>()
    private var current: WebKey? = null
    private var lastMs = 0L

    /** Anota lo observado si el usuario activó esa estadística. En incógnito nunca llega el dominio. */
    fun record(observation: WebObservation, settings: StatsSettings, nowMs: Long) {
        val key = when {
            observation is WebObservation.Web && settings.webs && observation.host != null -> WebKey(observation.host, incognito = false)
            observation is WebObservation.Incognito && settings.incognito -> WebKey("", incognito = true)
            else -> null
        }
        if (key == null) {
            current = null
            return
        }
        val previous = pending[key] ?: WebUsage()
        pending[key] = if (key == current) {
            previous.copy(millis = previous.millis + (nowMs - lastMs).coerceIn(0, UsageAccumulator.MAX_STEP_MS))
        } else {
            previous.copy(visits = previous.visits + 1)
        }
        current = key
        lastMs = nowMs
    }

    /** Lo acumulado desde la última vez; vuelve a cero. */
    fun drain(): Map<WebKey, WebUsage> = pending.toMap().also { pending.clear() }
}
