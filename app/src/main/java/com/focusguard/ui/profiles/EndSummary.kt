package com.focusguard.ui.profiles

import com.focusguard.model.EndPolicy
import com.focusguard.model.Profile
import com.focusguard.ui.formatLocal
import java.time.Instant
import java.time.ZoneId

/** Cómo se puede terminar un Bloquear ya, en una línea para Inicio; null si es lo de siempre (con «Terminar», cuando se quiera). */
internal fun endSummary(profile: Profile, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String? = when (profile.endPolicy) {
    EndPolicy.ANYTIME -> null
    EndPolicy.TIME_ONLY -> "Solo termina al acabar el tiempo"
    EndPolicy.CONDITIONS -> {
        val ends = profile.endConditions
        val reads = buildList {
            if (ends.place != null) add("llegas a un lugar")
            if (ends.charging) add("conectas el cargador")
            if (ends.wifiNames.isNotEmpty()) add(if (ends.wifiNames.size == 1) "te conectas a ${ends.wifiNames.first()}" else "te conectas a una de ${ends.wifiNames.size} redes")
            if (ends.bluetoothNames.isNotEmpty()) add(if (ends.bluetoothNames.size == 1) "conectas ${ends.bluetoothNames.first()}" else "conectas uno de ${ends.bluetoothNames.size} dispositivos")
        }
        val wait = ends.waitUntil?.let { until ->
            if (now.isBefore(until)) "podrás pedir terminarlo a las ${formatLocal(until, zone = zone)}" else "ya puedes pedir terminarlo"
        }
        when {
            reads.isEmpty() && wait != null -> wait.replaceFirstChar { it.uppercase() }
            wait == null -> "Termina antes si " + joinOr(reads)
            else -> "Termina antes si " + joinOr(reads) + "; " + wait
        }
    }
}

private fun joinOr(items: List<String>): String =
    if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " o " + items.last()
