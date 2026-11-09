package com.focusguard.model

import java.time.Instant

/** Sucesos que se cuentan al compañero. `wire` es el nombre que usa el servidor (`POST /events`). */
enum class PartnerEventType(val wire: String) {
    BLOCKED("blocked"),
    SETTINGS_ATTEMPT("settings_attempt"),
    UNINSTALL_ATTEMPT("uninstall_attempt"),
    PROTECTION_OFF("protection_off"),
    PANIC("panic"),
    EMERGENCY_UNLOCK("emergency_unlock"),
    ICON_CHANGED("icon_changed"),

    /** Cuántas consultas DNS se bloquearon y se permitieron un día (`bloqueadas,permitidas`), sin dominios; solo cuenta en el informe diario. */
    DNS_COUNTS("dns_counts"),

    /** El resumen semanal de impulsos (`total,pasados,cedidos,hora,desencadenante`), solo si el usuario lo permite; cuenta en el informe diario. */
    URGE_REPORT("urge_report"),
}

/** Un suceso listo para enviar. `label` es solo el paquete o dominio, nunca texto de pantalla. */
data class PartnerEvent(val type: PartnerEventType, val at: Instant, val label: String? = null)
