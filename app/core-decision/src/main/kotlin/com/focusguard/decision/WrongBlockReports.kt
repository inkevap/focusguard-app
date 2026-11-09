package com.focusguard.decision

import com.focusguard.model.BlockReason

/** Un bloqueo que el usuario cree erróneo (B10): qué clase de cosa es y su valor, que es lo único que sale del teléfono. */
data class WrongBlockReport(val kind: Kind, val value: String) {
    /** Los nombres que admite el servidor (`server/src/routes/recovery.ts`). */
    enum class Kind(val wire: String) { APP("app"), DOMAIN("domain") }
}

/**
 * Solo se pueden reportar los bloqueos que vienen de las listas del servidor (contenido adulto): son los que una revisión puede corregir.
 * Los de las listas del propio usuario, los límites o el modo estricto son decisiones suyas. Sale el dominio o el paquete, nunca la
 * dirección completa ni el texto de la pantalla.
 */
object WrongBlockReports {
    private const val MAX_VALUE = 200

    fun of(reason: BlockReason, target: String, targetIsApp: Boolean): WrongBlockReport? {
        if (reason != BlockReason.ADULT_CONTENT || target.isBlank()) return null
        return WrongBlockReport(if (targetIsApp) WrongBlockReport.Kind.APP else WrongBlockReport.Kind.DOMAIN, target.take(MAX_VALUE))
    }
}
