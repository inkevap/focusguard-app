package com.focusguard.decision

import kotlin.math.abs

/** Qué hacer con una captura: nada, bloquear lo que hay en pantalla, u ofrecer bloquearlo con una notificación. */
enum class ImageAction { NONE, BLOCK, OFFER }

/**
 * Cada cuánto como mucho se ofrece bloquear algo sugerente. Si ya salió una notificación en este plazo, no sale otra; si no, sale. Se elige en
 * Ajustes entre unas opciones (de serie 5 minutos).
 */
object ImageAlerts {
    const val DEFAULT_MINUTES = 5
    val OPTIONS = listOf(1, 2, 5, 10, 15, 30)

    /** La opción más cercana a [minutes], por si lo guardado ya no es una de las ofrecidas. */
    fun clamp(minutes: Int): Int = OPTIONS.minByOrNull { abs(it - minutes) } ?: DEFAULT_MINUTES

    fun toMs(minutes: Int): Long = clamp(minutes) * 60_000L
}

/**
 * Convierte lo que se ve en la pantalla en una acción:
 *
 * - **Explícito: bloquea.** Si la puntuación es clara (no está en la zona dudosa), al momento; si es dudosa, hace falta que lo confirmen 2 de las
 *   últimas 3 capturas, de no más de 5 minutos, para que un falso aviso suelto no cierre la pantalla.
 * - **Sugerente: ofrece bloquear** con una notificación, como mucho una cada [ImageAlerts] plazo.
 * - **Seguro: nada.**
 *
 * Guarda solo en memoria cuándo fue el último ofrecimiento y las últimas capturas.
 */
class ImageResponder(private val window: ConfirmationWindow = ConfirmationWindow(needed = 2, of = 3, maxAgeMs = 5 * 60_000L)) {

    private var lastOfferAtMs: Long? = null

    /** [assessment] es null si no se pudo puntuar: eso no cambia nada. [offerEveryMs] es el plazo entre ofrecimientos ([ImageAlerts.toMs]). */
    fun decide(nowMs: Long, assessment: ImageAssessment?, offerEveryMs: Long): ImageAction {
        if (assessment == null) return ImageAction.NONE
        return when (assessment.level) {
            ImageLevel.EXPLICIT -> {
                val confirmed = window.record(nowMs, flagged = true)
                val clear = assessment.explicit?.borderline == false
                if (clear || confirmed) {
                    window.clear()
                    ImageAction.BLOCK
                } else {
                    ImageAction.NONE
                }
            }
            ImageLevel.SUGGESTIVE -> {
                window.record(nowMs, flagged = false)
                offerIfDue(nowMs, offerEveryMs)
            }
            ImageLevel.SAFE -> {
                window.record(nowMs, flagged = false)
                ImageAction.NONE
            }
        }
    }

    private fun offerIfDue(nowMs: Long, offerEveryMs: Long): ImageAction {
        val last = lastOfferAtMs
        if (last != null && nowMs - last in 0 until offerEveryMs) return ImageAction.NONE
        lastOfferAtMs = nowMs
        return ImageAction.OFFER
    }
}
