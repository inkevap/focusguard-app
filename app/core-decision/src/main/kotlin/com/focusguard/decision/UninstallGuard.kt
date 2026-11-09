package com.focusguard.decision

import java.time.Duration

/**
 * El administrador del dispositivo es lo que impide desinstalar la app: Android no deja hacerlo mientras esté activo. Desactivarlo tiene que
 * costar, y cuesta igual que cualquier cambio que afloja una protección: una aprobación (espera, frase, pasos o compañero). Aprobada, se abre
 * una ventana corta en la que la pantalla de ajustes que lo desactiva no se cierra.
 */
object UninstallGuard {

    val GRANT = Duration.ofMinutes(10)

    /** ¿Hay que cerrar la pantalla de desactivar el administrador? Solo si está activo y no hay una ventana abierta. */
    fun shouldGuard(adminActive: Boolean, grantUntilMs: Long, nowMs: Long): Boolean = adminActive && nowMs >= grantUntilMs

    /** Hasta cuándo vale la ventana concedida a las [nowMs]. */
    fun grantUntil(nowMs: Long): Long = nowMs + GRANT.toMillis()
}
