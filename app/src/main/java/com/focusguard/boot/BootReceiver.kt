package com.focusguard.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusguard.permissions.Permission
import com.focusguard.permissions.PermissionChecker

/**
 * Al terminar de arrancar el teléfono arranca el escudo (H5). La decisión de si hace falta (hay algo que proteger y el servicio aún no
 * está listo) la toma el propio servicio: aquí no se toca la base de datos con el sistema todavía arrancando.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!PermissionChecker(context).isGranted(Permission.OVERLAY)) return
        // Un receptor de arranque puede iniciar un servicio mientras atiende el aviso, sin ser de primer plano.
        runCatching { context.startService(Intent(context, BootShieldService::class.java)) }
    }
}
