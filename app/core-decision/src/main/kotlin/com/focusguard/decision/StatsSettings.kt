package com.focusguard.decision

/** Qué estadísticas de navegación se guardan (K2). Cada una se activa por separado y se queda en el teléfono. */
data class StatsSettings(val webs: Boolean = false, val incognito: Boolean = false) {
    val any: Boolean get() = webs || incognito
}
