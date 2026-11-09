package com.focusguard.decision

/** Por qué no se captura la pantalla ahora: casi todas las capturas se ahorran antes de llegar a ningún modelo. */
enum class SkipReason { SCREEN_OFF, TRUSTED_APP, UNCHANGED }

object CaptureGate {

    /**
     * Null si hay que capturar. [packageName] es la app en primer plano (null si no se sabe: entonces no se da por de confianza), [trusted] las apps
     * que nunca se miran y [unchanged] que la pantalla es la misma que en la última captura (se reutiliza aquel resultado).
     */
    fun skipReason(screenOn: Boolean, packageName: String?, trusted: Set<String>, unchanged: Boolean): SkipReason? = when {
        !screenOn -> SkipReason.SCREEN_OFF
        packageName != null && packageName in trusted -> SkipReason.TRUSTED_APP
        unchanged -> SkipReason.UNCHANGED
        else -> null
    }
}

/**
 * Solo se actúa si [needed] de las últimas [of] capturas salieron marcadas y ninguna es más vieja que [maxAgeMs]: una sola captura no basta, y
 * una marca de hace rato no suma a la de ahora. Quien actúa llama a [clear] para no repetir el aviso con las mismas marcas.
 */
class ConfirmationWindow(private val needed: Int = 2, private val of: Int = 3, private val maxAgeMs: Long = 5 * 60_000L) {

    private val entries = ArrayDeque<Pair<Long, Boolean>>()

    init {
        require(needed in 1..of) { "se piden más marcas de las que caben" }
        require(maxAgeMs > 0) { "la antigüedad máxima debe ser positiva" }
    }

    /** Apunta una captura y dice si ya hay que actuar. */
    fun record(nowMs: Long, flagged: Boolean): Boolean {
        while (entries.isNotEmpty() && nowMs - entries.first().first > maxAgeMs) entries.removeFirst()
        entries.addLast(nowMs to flagged)
        while (entries.size > of) entries.removeFirst()
        return entries.count { it.second } >= needed
    }

    fun clear() = entries.clear()
}

/**
 * Cada cuánto capturar. Al entrar en una app o web se captura **tan seguido como se puede** ([minMs]) durante [holdMs] (dos minutos): es cuando más
 * importa mirar. Pasado ese tiempo, el intervalo **crece poco a poco**, [growth] veces con cada captura segura, hasta un tope de [maxMs] que depende
 * del riesgo de lo que hay en pantalla ([CaptureRisk.factor]). Algo sospechoso lo vuelve a poner en el mínimo otros [holdMs]. Si no se vuelve a esa app o
 * web en [holdMs], al volver se empieza de nuevo.
 */
data class CaptureTiming(
    val minMs: Long = 1_000L,
    val maxMs: Long = 102_000L,
    val growth: Double = 1.3,
    val holdMs: Long = 120_000L,
) {
    init {
        require(minMs > 0 && minMs <= maxMs) { "intervalos fuera de orden" }
        require(growth > 1.0 && growth.isFinite()) { "el tiempo tiene que crecer despacio, no quedarse igual ni acortarse" }
        require(holdMs >= 0) { "el tiempo al mínimo no puede ser negativo" }
    }
}

/**
 * Decide cuánto esperar entre capturas. Cada app o web (la clave de [CaptureTarget]) tiene su propio reloj: al mínimo durante [CaptureTiming.holdMs]
 * desde que se entra en ella, y después **crece poco a poco** con cada captura segura. Una sola captura dudosa o explícita **lo baja de golpe** y vuelve a
 * empezar el tiempo al mínimo. Se guarda solo en memoria y con un tope de claves, para que recorrer muchas webs no la haga crecer sin límite.
 */
class CaptureScheduler(private val timing: CaptureTiming = CaptureTiming()) {

    private class Entry(var fastUntilMs: Long, var lastSeenMs: Long, var safeStreak: Int = 0)

    private val entries = HashMap<String, Entry>()

    /** La entrada de [key] a las [nowMs]; si es nueva o se estuvo fuera más de [CaptureTiming.holdMs], empieza otra vez al mínimo. */
    private fun entry(key: String, nowMs: Long): Entry {
        val e = entries[key]
        if (e != null && nowMs - e.lastSeenMs <= timing.holdMs) {
            e.lastSeenMs = nowMs
            return e
        }
        if (entries.size >= MAX_KEYS && key !in entries) entries.clear()
        return Entry(nowMs + timing.holdMs, nowMs).also { entries[key] = it }
    }

    /**
     * Cuánto esperar desde la última captura estando ahora en [key] con [risk], a las [nowMs] (reloj monótono). Se puede preguntar a cada momento: cambia con lo
     * que hay en pantalla. [boost] (la vigilancia y el modo dinámico) acorta el tiempo, pero nunca por debajo del mínimo.
     */
    fun intervalMs(key: String, risk: CaptureRisk, nowMs: Long, boost: Double = 1.0): Long {
        val e = entry(key, nowMs)
        if (nowMs < e.fastUntilMs) return timing.minMs
        val cap = maxOf(timing.minMs.toDouble(), timing.maxMs * risk.factor)
        val raw = timing.minMs * Math.pow(timing.growth, (e.safeStreak + 1).coerceAtMost(MAX_STREAK).toDouble())
        return (raw.coerceIn(timing.minMs.toDouble(), cap) * boost.coerceIn(MIN_BOOST, 1.0)).coerceAtLeast(timing.minMs.toDouble()).toLong()
    }

    /**
     * Apunta el resultado de la última captura de [key] (null si no se pudo puntuar: no cambia nada). Con [fresh] en false el resultado es el de una captura anterior
     * reutilizada porque la pantalla no cambió: no es una observación nueva, así que cuenta como segura y nunca baja el tiempo.
     */
    fun record(key: String, result: ImageAssessment?, nowMs: Long, fresh: Boolean = true) {
        if (result == null) return
        val e = entry(key, nowMs)
        if (result.suspicious && fresh) {
            e.safeStreak = 0
            e.fastUntilMs = nowMs + timing.holdMs
        } else if (nowMs >= e.fastUntilMs) {
            e.safeStreak++
        }
    }

    private companion object {
        const val MAX_STREAK = 60
        const val MIN_BOOST = 0.1
        const val MAX_KEYS = 200
    }
}
