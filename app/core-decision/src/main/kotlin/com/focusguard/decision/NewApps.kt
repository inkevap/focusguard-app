package com.focusguard.decision

import java.time.Duration
import java.time.Instant

/** Ajuste global de «apps recién instaladas» (A11): cuántas horas queda bloqueada una app tras instalarla. */
data class NewAppsPolicy(val enabled: Boolean = false, val hours: Int = DEFAULT_HOURS) {

    /** Las horas del plazo; negativas si está apagado, para no perderlas al volver a encenderlo. */
    fun encode(): String = (if (enabled) hours else -hours).toString()

    companion object {
        const val DEFAULT_HOURS = 24
        val HOURS_OPTIONS = listOf(12, 24, 48, 72)

        /** Null si el texto no sale de [encode]: una solicitud con otro contenido no se aplica. */
        fun decode(text: String): NewAppsPolicy? {
            val value = text.toIntOrNull() ?: return null
            val hours = kotlin.math.abs(value)
            return if (hours in HOURS_OPTIONS) NewAppsPolicy(value > 0, hours) else null
        }
    }
}

object NewApps {

    /** Hasta cuándo sigue bloqueada una app instalada en [installedAtMs]; null si ya no lo está o no se sabe cuándo se instaló. */
    fun blockedUntil(policy: NewAppsPolicy, installedAtMs: Long?, now: Instant): Instant? {
        if (!policy.enabled || installedAtMs == null) return null
        val until = Instant.ofEpochMilli(installedAtMs).plus(Duration.ofHours(policy.hours.toLong()))
        return until.takeIf { now.isBefore(it) }
    }
}
