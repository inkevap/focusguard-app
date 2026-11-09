package com.focusguard.decision

import com.focusguard.model.LimitKind
import com.focusguard.model.ProfileUsage
import com.focusguard.model.UsageLimit
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Cuánto lleva gastado un límite y cuánto le queda antes de bloquear. [used] y [left] son minutos (TIME, CONTINUOUS) o aperturas (OPENS).
 * Con las aperturas, [left] es cuántas más se pueden hacer: con 0 la siguiente ya bloquea.
 */
data class LimitStatus(val kind: LimitKind, val amount: Int, val used: Int, val left: Int, val exhausted: Boolean) {

    /** Fracción gastada, de 0 a 1. */
    val fraction: Float get() = (used.toFloat() / amount).coerceIn(0f, 1f)

    /** Texto corto para la notificación y las pantallas. */
    fun describe(): String = when (kind) {
        LimitKind.TIME -> if (exhausted) "Límite agotado: $used de $amount min" else "Llevas $used de $amount min · quedan $left min"
        LimitKind.OPENS -> when {
            exhausted -> "Límite agotado: $used aperturas (máximo $amount)"
            left == 0 -> "Llevas $used de $amount aperturas · la próxima bloquea"
            else -> "Llevas $used de $amount aperturas · quedan $left"
        }
        LimitKind.CONTINUOUS -> if (exhausted) "Llevas $used min seguidos: toca descansar" else "Llevas $used de $amount min seguidos · quedan $left min"
    }

    companion object {
        /** Estado del límite con el [usage] dado; un uso de otro periodo cuenta como cero. */
        fun of(
            limit: UsageLimit,
            usage: ProfileUsage?,
            nowMs: Long,
            now: LocalDateTime,
            dayStartMinute: Int = 0,
            zone: ZoneId = ZoneId.systemDefault(),
        ): LimitStatus {
            val current = usage?.takeIf { LimitEvaluator.isCurrent(limit, it, nowMs, now, dayStartMinute, zone) }
            val exhausted = LimitEvaluator.isExhausted(limit, usage, nowMs, now, dayStartMinute, zone)
            return when (limit.kind) {
                LimitKind.OPENS -> {
                    val used = current?.opens ?: 0
                    LimitStatus(limit.kind, limit.amount, used, (limit.amount - used).coerceAtLeast(0), exhausted)
                }
                LimitKind.TIME -> minutes(limit, current?.timeMs ?: 0, exhausted)
                LimitKind.CONTINUOUS -> minutes(limit, current?.continuousMs ?: 0, exhausted)
            }
        }

        private fun minutes(limit: UsageLimit, usedMs: Long, exhausted: Boolean): LimitStatus {
            val leftMs = (limit.amount * 60_000L - usedMs).coerceAtLeast(0)
            return LimitStatus(limit.kind, limit.amount, (usedMs / 60_000L).toInt(), ((leftMs + 59_999L) / 60_000L).toInt(), exhausted)
        }
    }
}
