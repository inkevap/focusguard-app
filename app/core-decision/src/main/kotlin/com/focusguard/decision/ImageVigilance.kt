package com.focusguard.decision

/** Qué se vigila más seguido durante la vigilancia: lo que puede ser un riesgo (redes, vídeo, fotos, citas y cualquier web de un navegador) o todo. */
enum class VigilanceScope { RISKY, ALL }

/**
 * La vigilancia tras lo explícito: si hay contenido explícito en pantalla es probable que sea un momento de impulso, así que durante un tiempo («enfriamiento»)
 * se captura más seguido lo que puede ser un riesgo. Todo se elige en Ajustes:
 *
 * - [enabled]: de serie encendida (solo actúa con la detección de imágenes encendida).
 * - [minutes]: cuánto dura; cada nuevo suceso lo renueva desde ese momento. Opciones en [OPTIONS_MINUTES].
 * - [intensity]: 1 (suave, un 30 % más seguido), 2 (media, el doble de seguido) o 3 (alta, el triple).
 * - [scope]: qué se vigila más seguido.
 * - [afterSuggestive]: también tras lo sugerente (de serie solo tras lo explícito).
 */
data class VigilanceSettings(
    val enabled: Boolean = true,
    val minutes: Int = DEFAULT_MINUTES,
    val intensity: Int = DEFAULT_INTENSITY,
    val scope: VigilanceScope = VigilanceScope.RISKY,
    val afterSuggestive: Boolean = false,
) {
    init {
        require(minutes in OPTIONS_MINUTES) { "duración no ofrecida: $minutes" }
        require(intensity in MIN_INTENSITY..MAX_INTENSITY) { "intensidad fuera de rango: $intensity" }
    }

    /** Por cuánto se multiplica el tiempo entre capturas durante la vigilancia (menos de 1: más seguido). */
    val intervalFactor: Double get() = when (intensity) {
        1 -> 0.7
        2 -> 0.5
        else -> 0.33
    }

    fun encode(): String = "$enabled,$minutes,$intensity,${scope.name},$afterSuggestive"

    /**
     * ¿Pasar de este ajuste a [next] debilita la protección? Apagarla, acortarla, bajar la intensidad, vigilar menos cosas o dejar de vigilar tras lo
     * sugerente. Si ya estaba apagada no hay nada que debilitar.
     */
    fun weakens(next: VigilanceSettings): Boolean {
        if (!enabled) return false
        return !next.enabled ||
            next.minutes < minutes ||
            next.intensity < intensity ||
            (scope == VigilanceScope.ALL && next.scope == VigilanceScope.RISKY) ||
            (afterSuggestive && !next.afterSuggestive)
    }

    companion object {
        val OPTIONS_MINUTES = listOf(5, 10, 15, 30, 60)
        const val DEFAULT_MINUTES = 15
        const val MIN_INTENSITY = 1
        const val MAX_INTENSITY = 3
        const val DEFAULT_INTENSITY = 2

        /** Lo contrario de [encode]; null si el texto no es un ajuste válido. */
        fun decode(text: String): VigilanceSettings? {
            val parts = text.trim().split(',')
            if (parts.size != 5) return null
            val enabled = parts[0].toBooleanStrictOrNull() ?: return null
            val minutes = parts[1].toIntOrNull()?.takeIf { it in OPTIONS_MINUTES } ?: return null
            val intensity = parts[2].toIntOrNull()?.takeIf { it in MIN_INTENSITY..MAX_INTENSITY } ?: return null
            val scope = VigilanceScope.entries.firstOrNull { it.name == parts[3] } ?: return null
            val afterSuggestive = parts[4].toBooleanStrictOrNull() ?: return null
            return VigilanceSettings(enabled, minutes, intensity, scope, afterSuggestive)
        }
    }
}

/** El reloj del enfriamiento: guarda solo en memoria hasta cuándo dura la vigilancia. */
class ImageVigilance {

    private var untilMs = 0L

    /**
     * Algo lo justifica (lo explícito, o lo sugerente si el ajuste lo pide): empieza o se renueva el enfriamiento desde [nowMs]. Dura [minutes], que de serie son los
     * del ajuste y con el modo dinámico pueden ser más ([ImageAdaptation.vigilanceMinutes]).
     */
    fun trigger(nowMs: Long, settings: VigilanceSettings, minutes: Int = settings.minutes) {
        if (!settings.enabled) return
        untilMs = nowMs + minutes * 60_000L
    }

    /** Recupera el enfriamiento que había antes de un reinicio del servicio ([untilMs] en el mismo reloj que se usa para consultarlo). */
    fun restore(untilMs: Long) {
        this.untilMs = untilMs
    }

    /** Hasta cuándo dura, para guardarlo. */
    fun untilMs(): Long = untilMs

    fun remainingMs(nowMs: Long): Long = (untilMs - nowMs).coerceAtLeast(0)

    fun active(nowMs: Long): Boolean = remainingMs(nowMs) > 0

    /** Por cuánto multiplicar el tiempo entre capturas de [target] ahora: 1 si no hay vigilancia o no le toca a esto. */
    fun factor(nowMs: Long, settings: VigilanceSettings, target: CaptureTarget): Double {
        if (!settings.enabled || !active(nowMs)) return 1.0
        return if (appliesTo(settings, target)) settings.intervalFactor else 1.0
    }

    fun clear() {
        untilMs = 0
    }

    companion object {
        /** ¿Le toca a [target] según el alcance elegido? Lo de riesgo (redes, vídeo, fotos, citas y cualquier web de un navegador) o todo. */
        fun appliesTo(settings: VigilanceSettings, target: CaptureTarget): Boolean =
            settings.scope == VigilanceScope.ALL || target.risk == CaptureRisk.HIGH || target.isBrowser
    }
}
