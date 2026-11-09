package com.focusguard.decision

import java.time.LocalDate

enum class UrgeOutcome {
    /** En curso. */
    ACTIVE,

    /** Se pasó (lo dijo el usuario, o acabó la cuenta atrás y siguió firme). */
    PASSED,

    /** Cedió al impulso: se guarda tal cual, sin juicio; sirve para entender cuándo pasa. */
    GAVE_IN,

    /** Se dejó sin contestar y pasó mucho tiempo. */
    ABANDONED,
}

/** Lo que el usuario cree que lo desencadenó (L7 lo enseñará en el informe). */
enum class UrgeTrigger(val label: String) {
    BOREDOM("Aburrimiento"),
    STRESS("Estrés"),
    LONELINESS("Soledad"),
    TIREDNESS("Cansancio"),
    ANXIETY("Ansiedad"),
    OTHER("Otra cosa"),
}

data class Distraction(val title: String, val hint: String)

/**
 * Modo impulsos (L3): una cuenta atrás de lo que suele durar un impulso, mensajes que se van turnando y actividades para distraerse.
 * Todo se mide con la hora de confianza que da quien llama.
 */
object Urges {
    /** «Un impulso dura unos 20 minutos»: la cuenta atrás es esa. */
    const val DURATION_MS = 20 * 60_000L

    /** Un impulso sin contestar pasado este tiempo se da por abandonado: no se retoma una cuenta atrás de hace horas. */
    const val STALE_AFTER_MS = 60 * 60_000L

    const val MESSAGE_EVERY_MS = 60_000L

    fun remainingMs(startedMs: Long, nowMs: Long): Long = (DURATION_MS - (nowMs - startedMs)).coerceIn(0, DURATION_MS)

    fun isStale(startedMs: Long, nowMs: Long): Boolean = nowMs - startedMs > STALE_AFTER_MS

    val MESSAGES = listOf(
        "Un impulso dura unos 20 minutos y luego baja. No tienes que hacer nada con él, solo dejarlo pasar.",
        "Sentir el impulso no te obliga a actuar. Puedes notarlo y seguir con otra cosa.",
        "Respira despacio. El impulso sube, llega a su punto más alto y se va, como una ola.",
        "Ya has aguantado otras veces. Esta también pasa.",
        "Cambia de sitio: levántate, abre una ventana, sal un momento de la habitación.",
        "Piensa en cómo te vas a sentir esta noche si lo dejas pasar.",
        "No hace falta que sea perfecto. Solo llegar al final de estos minutos.",
        "Si puedes, habla con alguien. Un mensaje corto ya cuenta.",
        "Bebe agua y deja el teléfono un minuto. Tu cuerpo también necesita un respiro.",
        "Lo difícil es el principio. Cada minuto que pasa, el impulso pierde fuerza.",
    )

    fun messageAt(elapsedMs: Long): String =
        MESSAGES[((elapsedMs.coerceAtLeast(0) / MESSAGE_EVERY_MS) % MESSAGES.size).toInt()]

    val DISTRACTIONS = listOf(
        Distraction("Beber un vaso de agua", "Despacio, a sorbos, sin el teléfono en la mano."),
        Distraction("Salir a caminar", "Cinco minutos fuera de casa cambian el ánimo."),
        Distraction("Ducha o agua fría en la cara", "Un cambio brusco de sensación te saca del bucle."),
        Distraction("Hacer ejercicio corto", "Diez flexiones, sentadillas o estiramientos."),
        Distraction("Llamar o escribir a alguien", "A un amigo, a tu compañero o a la familia."),
        Distraction("Ordenar algo", "Un cajón, la mesa o la mochila: algo con principio y final."),
        Distraction("Escribir lo que sientes", "Tres frases en el diario de FocusGuard."),
        Distraction("Preparar algo de comer", "Manos ocupadas y la cabeza en otra cosa."),
    )
}

enum class BreathPhase(val label: String) { INHALE("Inspira"), HOLD("Sostén"), EXHALE("Espira"), REST("Sostén") }

/** Un instante del ejercicio de respiración. [scale] va de 0,5 (pulmones vacíos) a 1 (llenos) para dibujar el círculo. */
data class BreathState(val phase: BreathPhase, val scale: Float, val secondsLeft: Int)

/** Respiración 4-4-6: inspirar 4 s, sostener 4 s y espirar 6 s, en bucle. */
object Breathing {
    const val INHALE_MS = 4_000L
    const val HOLD_MS = 4_000L
    const val EXHALE_MS = 6_000L
    const val CYCLE_MS = INHALE_MS + HOLD_MS + EXHALE_MS
    private const val EMPTY = 0.5f

    fun at(elapsedMs: Long): BreathState {
        val t = elapsedMs.coerceAtLeast(0) % CYCLE_MS
        return when {
            t < INHALE_MS -> BreathState(BreathPhase.INHALE, EMPTY + (1 - EMPTY) * t / INHALE_MS, left(INHALE_MS - t))
            t < INHALE_MS + HOLD_MS -> BreathState(BreathPhase.HOLD, 1f, left(INHALE_MS + HOLD_MS - t))
            else -> BreathState(BreathPhase.EXHALE, 1f - (1 - EMPTY) * (t - INHALE_MS - HOLD_MS) / EXHALE_MS, left(CYCLE_MS - t))
        }
    }

    private fun left(ms: Long) = ((ms + 999) / 1000).toInt()
}

/** Diario (L4) y planificador de actividades (L4). */
object Journal {
    const val MAX_ENTRY_CHARS = 2_000
    const val MAX_ACTIVITIES_PER_DAY = 3
    const val MAX_ACTIVITY_CHARS = 80

    fun cleanEntry(text: String) = text.trim().take(MAX_ENTRY_CHARS)

    private val WHITESPACE = Regex("\\s+")

    fun cleanActivity(text: String) = text.trim().replace(WHITESPACE, " ").take(MAX_ACTIVITY_CHARS)

    /** Se planifica para hoy o para días que vienen, hasta tres por día; lo ya planificado no se edita ni se borra. */
    fun canPlan(day: LocalDate, today: LocalDate, alreadyPlanned: Int, text: String): Boolean =
        !day.isBefore(today) && alreadyPlanned < MAX_ACTIVITIES_PER_DAY && cleanActivity(text).isNotEmpty()

    /** Se marca como hecha el mismo día. */
    fun canComplete(day: LocalDate, today: LocalDate) = day == today
}
