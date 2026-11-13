package com.focusguard.ui

/** Pantallas a las que se puede llegar desde fuera de la app (icono, ajustes rápidos). Solo las que no cambian nada sin confirmar. */
internal object Routes {
    const val PANIC = "panic"
    const val EMERGENCY = "emergency"
    const val TROUBLESHOOTER = "troubleshooter"
    const val NOTIFICATIONS = "notifications"
    const val ACCOUNT = "account"
    const val ADULT = "adult"
    const val DNS = "dns"
    const val DNS_LOG = "dns-log"
    const val IMAGES = "images"
    const val BACKUP = "backup"
    const val SLEEP = "sleep"
    const val STREAK = "streak"
    const val URGE = "urge"
    const val WEEKLY = "weekly"
    const val HABITS = "habits"
    const val JOURNAL = "journal"
    const val RECOVERY = "recovery"
    const val ASSESSMENT = "assessment"
    const val URGE_REPORT = "urge-report"
    const val STATS = "stats"
    const val QUICK_BLOCK = "session/quick"
    const val UNLOCK_ALL = "unlock_all"
    const val HELP = "help"
    const val TEMPLATES = "templates"

    /** Quick Block, Quitar bloqueos e Impulso solo abren su pantalla: no cambian nada hasta confirmar dentro de la app. */
    val externalTargets = setOf(PANIC, EMERGENCY, QUICK_BLOCK, UNLOCK_ALL, SLEEP, URGE)
}
