package com.focusguard.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.focusguard.MainActivity
import com.focusguard.model.PartnerEventType

/** Los iconos con los que se puede ver la app en el lanzador (H10). [alias] es el nombre del `activity-alias` del manifiesto. */
enum class LauncherIcon(val alias: String, val label: String, val hint: String) {
    DEFAULT("IconDefault", "FocusGuard", "El escudo de siempre."),
    CALCULATOR("IconCalculator", "Calculadora", "Parece una calculadora."),
    NOTES("IconNotes", "Notas", "Parece una app de notas."),
    CLOCK("IconClock", "Reloj", "Parece un reloj."),
}

/**
 * Cambia el icono y el nombre de la app en el lanzador (H10): un solo alias activo cada vez. Solo cambia cómo se ve en el lanzador:
 * el bloqueo, los permisos y los avisos siguen igual. Algunos lanzadores tardan unos segundos en enseñar el cambio.
 */
class LauncherIconManager(private val context: Context, private val partnerEvents: PartnerEventOutbox) {

    /** Los alias están declarados con el espacio de nombres de la app, que no siempre es el identificador de la aplicación. */
    private fun component(icon: LauncherIcon) = ComponentName(context.packageName, "${MainActivity::class.java.name.substringBeforeLast('.')}.${icon.alias}")

    /** El icono activo; el de siempre si no hay ninguno marcado. */
    fun current(): LauncherIcon = LauncherIcon.entries.firstOrNull { isEnabled(it) } ?: LauncherIcon.DEFAULT

    private fun isEnabled(icon: LauncherIcon): Boolean {
        val state = context.packageManager.getComponentEnabledSetting(component(icon))
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT)
    }

    /**
     * Activa [icon] y apaga los demás. Se activa antes de apagar: nunca se queda la app sin icono. Disimular la app (cualquier icono que no sea
     * el de siempre) se cuenta al compañero: quien la disimula deja de reconocerla en el teléfono. Volver al de siempre es libre. Quien llama
     * ya pasó por el guardián de cambios si hacía falta.
     */
    fun set(icon: LauncherIcon) {
        // Aplicar dos veces lo mismo (una aprobación repetida) no cambia nada ni avisa de nuevo al compañero.
        if (current() == icon) return
        val pm = context.packageManager
        pm.setComponentEnabledSetting(component(icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        LauncherIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
        if (icon != LauncherIcon.DEFAULT) partnerEvents.record(PartnerEventType.ICON_CHANGED, icon.label)
    }
}
