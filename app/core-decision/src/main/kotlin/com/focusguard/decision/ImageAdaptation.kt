package com.focusguard.decision

import kotlin.math.pow

/**
 * El modo dinámico: la detección se adapta a cómo se comporta el usuario. Cuantas más veces se bloquea algo explícito en un periodo, más sube el nivel de
 * alerta, y con él la vigilancia (se captura más seguido y el enfriamiento dura más) y la sensibilidad. Cuando pasa el tiempo sin incidentes, los incidentes
 * viejos dejan de contar y todo vuelve solo a lo que el usuario eligió.
 *
 * - [enabled]: de serie encendido (solo actúa con la detección de imágenes encendida).
 * - [windowHours]: cuánto tiempo cuenta un incidente. Opciones en [OPTIONS_HOURS].
 * - [maxLevel]: hasta qué nivel de alerta puede subir (1 a [MAX_LEVEL]).
 */
data class AdaptiveSettings(
    val enabled: Boolean = true,
    val windowHours: Int = DEFAULT_HOURS,
    val maxLevel: Int = DEFAULT_MAX_LEVEL,
) {
    init {
        require(windowHours in OPTIONS_HOURS) { "periodo no ofrecido: $windowHours" }
        require(maxLevel in 1..MAX_LEVEL) { "nivel máximo fuera de rango: $maxLevel" }
    }

    val windowMs: Long get() = windowHours * 3_600_000L

    fun encode(): String = "$enabled,$windowHours,$maxLevel"

    /** ¿Pasar a [next] debilita la protección? Apagarlo, acortar el periodo (cuentan menos incidentes) o bajar el nivel máximo. Si ya estaba apagado no hay nada que debilitar. */
    fun weakens(next: AdaptiveSettings): Boolean {
        if (!enabled) return false
        return !next.enabled || next.windowHours < windowHours || next.maxLevel < maxLevel
    }

    companion object {
        val OPTIONS_HOURS = listOf(6, 24, 72, 168)
        const val DEFAULT_HOURS = 6
        const val MAX_LEVEL = 3
        const val DEFAULT_MAX_LEVEL = 2

        fun decode(text: String): AdaptiveSettings? {
            val parts = text.trim().split(',')
            if (parts.size != 3) return null
            val enabled = parts[0].toBooleanStrictOrNull() ?: return null
            val hours = parts[1].toIntOrNull()?.takeIf { it in OPTIONS_HOURS } ?: return null
            val level = parts[2].toIntOrNull()?.takeIf { it in 1..MAX_LEVEL } ?: return null
            return AdaptiveSettings(enabled, hours, level)
        }
    }
}

object ImageAdaptation {

    /** Cuántos incidentes hacen falta para el nivel 1, el 2 y el 3. */
    private val STEPS = intArrayOf(1, 3, 6)

    /** Cada nivel acorta el tiempo entre capturas un 20 % más (0,8; 0,64; 0,51). */
    private const val INTERVAL_STEP = 0.8

    /** El enfriamiento de la vigilancia nunca pasa de dos horas, por mucho que crezca. */
    const val MAX_VIGILANCE_MINUTES = 120

    /** Los incidentes (cuándo se bloqueó algo explícito) que caen dentro del periodo hasta [nowMs]. Los de una hora futura (reloj movido) no cuentan. */
    fun incidentsIn(settings: AdaptiveSettings, incidents: List<Long>, nowMs: Long): Int = incidents.count { nowMs - it in 0..settings.windowMs }

    /** El nivel de alerta ahora: 0 sin incidentes recientes (o con el modo apagado), hasta [AdaptiveSettings.maxLevel]. */
    fun level(settings: AdaptiveSettings, incidents: List<Long>, nowMs: Long): Int {
        if (!settings.enabled) return 0
        val count = incidentsIn(settings, incidents, nowMs)
        return STEPS.count { count >= it }.coerceAtMost(settings.maxLevel)
    }

    /** Por cuánto multiplicar el tiempo entre capturas en este nivel (menos de 1: más seguido). */
    fun intervalFactor(level: Int): Double = INTERVAL_STEP.pow(level.coerceIn(0, AdaptiveSettings.MAX_LEVEL))

    /** La sensibilidad que se aplica: la elegida más un paso por nivel, hasta la máxima. Nunca baja de la elegida. */
    fun sensitivity(base: Int, level: Int): Int = ImageSensitivity.clamp(ImageSensitivity.clamp(base) + level.coerceAtLeast(0))

    /** Lo que dura la vigilancia en este nivel: la elegida más la mitad por nivel, con un tope de [MAX_VIGILANCE_MINUTES]. */
    fun vigilanceMinutes(base: Int, level: Int): Int = (base + base * level.coerceAtLeast(0) / 2).coerceAtMost(MAX_VIGILANCE_MINUTES)
}

/** El historial de incidentes, como texto para guardarlo: solo se guarda la hora de cada uno. */
object ImageIncidents {

    /** Se guardan los de la última semana (el periodo más largo que se ofrece) y, como mucho, estos. */
    private const val KEEP_MS = 168 * 3_600_000L
    private const val MAX = 200

    /** [incidents] más uno nuevo en [nowMs], sin los que ya no puede contar ningún periodo. */
    fun add(incidents: List<Long>, nowMs: Long): List<Long> = (incidents.filter { nowMs - it in 0..KEEP_MS } + nowMs).takeLast(MAX)

    fun encode(incidents: List<Long>): String = incidents.joinToString(",")

    /** Lo contrario de [encode]; lo que no se entiende se descarta. */
    fun decode(text: String): List<Long> = text.split(',').mapNotNull { it.trim().toLongOrNull() }.takeLast(MAX)
}
