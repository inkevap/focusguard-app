package com.focusguard.decision

/**
 * Cómo se mezclan los ajustes de dos dispositivos al sincronizarlos (N4). Solo suma y nunca afloja: un interruptor queda encendido si lo
 * está en alguno, las palabras se unen, y de las políticas gana la más estricta. Lo que se quita en un teléfono no se quita en el otro (quitar
 * debilita, y debilitar pasa por sus aprobaciones, no por una sincronización). Los perfiles y el inicio del día no se sincronizan: los
 * perfiles viajan con la copia de seguridad, y mover el inicio del día puede levantar un límite agotado.
 * La mezcla es conmutativa y no cambia al repetirse, así que da igual el orden en que se junten los teléfonos.
 */
object SyncMerge {
    /** Tope de palabras propias en lo sincronizado, para que el documento no crezca sin fin. Se conservan las primeras en orden alfabético. */
    const val MAX_WORDS = 1_000

    fun merge(a: BackupSettings, b: BackupSettings): BackupSettings = BackupSettings(
        dayStartMinute = null,
        adultBlock = a.adultBlock || b.adultBlock,
        adultWords = (a.adultWords + b.adultWords).sorted().take(MAX_WORDS).toSet(),
        newApps = stricter(a.newApps, b.newApps),
        embeddedBrowsers = stricter(a.embeddedBrowsers, b.embeddedBrowsers),
        foldFloating = a.foldFloating || b.foldFloating,
    )

    private fun stricter(a: NewAppsPolicy?, b: NewAppsPolicy?): NewAppsPolicy? = when {
        a == null -> b
        b == null -> a
        // Con plazos distintos gana el más largo; un plazo apagado nunca gana a uno encendido.
        Weakening.newAppsWeakened(a, b) -> a
        Weakening.newAppsWeakened(b, a) -> b
        else -> if (a.enabled == b.enabled && a.hours >= b.hours) a else b
    }

    private fun stricter(a: EmbeddedBrowserPolicy?, b: EmbeddedBrowserPolicy?): EmbeddedBrowserPolicy? {
        if (a == null) return b
        if (b == null) return a
        val on = listOf(a, b).filter { it.enabled }
        if (on.isEmpty()) return if (a.apps.size >= b.apps.size) a else b
        return EmbeddedBrowserPolicy(enabled = true, allApps = on.any { it.allApps }, apps = on.flatMapTo(mutableSetOf()) { it.apps })
    }
}
