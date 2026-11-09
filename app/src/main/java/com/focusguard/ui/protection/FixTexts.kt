package com.focusguard.ui.protection

import com.focusguard.decision.Fix
import com.focusguard.permissions.Permission
import com.focusguard.ui.PermissionInfo
import com.focusguard.ui.info

/** El permiso que se concede para arreglar cada problema; null si se arregla en los ajustes de la app. */
internal fun Fix.permission(): Permission? = when (this) {
    Fix.ACCESSIBILITY, Fix.RESTART_SERVICE -> Permission.ACCESSIBILITY
    Fix.USAGE_STATS -> Permission.USAGE_STATS
    Fix.OVERLAY -> Permission.OVERLAY
    Fix.BATTERY -> Permission.BATTERY
    Fix.ADMIN -> Permission.ADMIN
    Fix.LOCATION -> Permission.LOCATION
    Fix.BLUETOOTH -> Permission.BLUETOOTH
    Fix.NOTIFICATIONS, Fix.AUTOSTART -> null
}

/** Título, motivo y botón de cada problema; los permisos reutilizan los textos de la pantalla de permisos. */
internal fun Fix.texts(): PermissionInfo = when (this) {
    Fix.RESTART_SERVICE -> PermissionInfo(
        "El servicio no responde",
        "Está activado pero no da señales. Desactívalo y vuelve a activarlo.",
        "Abrir accesibilidad",
    )
    Fix.NOTIFICATIONS -> PermissionInfo(
        "Avisos desactivados",
        "Para saber cuándo cambia la fase del Pomodoro o termina un bloqueo.",
        "Abrir ajustes de la app",
    )
    Fix.AUTOSTART -> PermissionInfo(
        "Inicio automático",
        "Tu marca lo exige para que FocusGuard vuelva a funcionar tras reiniciar. Android no deja comprobarlo: ábrelo y actívalo si existe.",
        "Abrir ajustes de la app",
    )
    Fix.ACCESSIBILITY -> Permission.ACCESSIBILITY.info()
    Fix.USAGE_STATS -> Permission.USAGE_STATS.info()
    Fix.OVERLAY -> Permission.OVERLAY.info()
    Fix.BATTERY -> Permission.BATTERY.info()
    Fix.ADMIN -> Permission.ADMIN.info()
    Fix.LOCATION -> Permission.LOCATION.info()
    Fix.BLUETOOTH -> Permission.BLUETOOTH.info()
}
