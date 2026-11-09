package com.focusguard.decision

import com.focusguard.model.BlockMode
import com.focusguard.model.ConditionMode
import com.focusguard.model.LimitScope
import com.focusguard.model.Profile

/**
 * Qué cambios de un perfil existente debilitan una protección y, por tanto, piden aprobación
 * (ESPECIFICACION 7: añadir o endurecer es libre). Cada cambio devuelve su código de acción.
 */
object Weakening {

    const val ACTION_PROFILE_MODE = 2003
    const val ACTION_LIMIT = 2004
    const val ACTION_SCHEDULE = 2005
    const val ACTION_CONDITION = 2009
    const val ACTION_SUBAPP = 2010
    const val ACTION_CATEGORY = 2011
    const val ACTION_NEW_APPS = 2012
    const val ACTION_EMBEDDED_BROWSERS = 2013
    const val ACTION_FOLD_FLOATING = 2014
    const val ACTION_PROFILE_PAUSE = 2015

    fun of(old: Profile, new: Profile): List<Int> = buildList {
        if (old.mode != new.mode) add(ACTION_PROFILE_MODE)

        if (old.mode == BlockMode.BLOCK && new.mode == BlockMode.BLOCK) {
            // Quitar algo de la lista de bloqueo bloquea menos.
            if ((old.apps - new.apps).isNotEmpty()) add(Approvals.ACTION_REMOVE_BLOCKED_APP)
            if ((old.webs.toSet() - new.webs.toSet()).isNotEmpty()) add(Approvals.ACTION_REMOVE_BLOCKED_WEB)
            if ((old.categories - new.categories).isNotEmpty()) add(ACTION_CATEGORY)
        }
        if (old.mode == BlockMode.ALLOW_ONLY && new.mode == BlockMode.ALLOW_ONLY) {
            // Añadir algo a la lista de permitidos bloquea menos.
            if ((new.apps - old.apps).isNotEmpty()) add(Approvals.ACTION_REMOVE_ALLOWED_APP)
            if ((new.webs.toSet() - old.webs.toSet()).isNotEmpty()) add(Approvals.ACTION_REMOVE_ALLOWED_WEB)
        }

        val oldLimit = old.limit
        val newLimit = new.limit
        // Un perfil con límite solo bloquea cuando se agota: ponérselo a uno que bloqueaba siempre lo debilita.
        if (oldLimit == null && newLimit != null) add(ACTION_LIMIT)
        // Pasar de un contador compartido a uno por app da más tiempo en total.
        if (oldLimit != null && newLimit != null && oldLimit.scope == LimitScope.SHARED && newLimit.scope == LimitScope.PER_TARGET) add(ACTION_LIMIT)
        if (oldLimit != null && (newLimit == null || relaxes(oldLimit.amount, newLimit.amount, oldLimit.kind == newLimit.kind && oldLimit.period == newLimit.period))) {
            add(ACTION_LIMIT)
        }

        // Sin intervalos el perfil está activo siempre: el primero lo limita a unas horas.
        if (old.intervals.isEmpty() && new.intervals.isNotEmpty()) add(ACTION_SCHEDULE)
        // Cualquier intervalo que desaparece o cambia puede dejar horas sin bloqueo.
        if ((old.intervals.toSet() - new.intervals.toSet()).isNotEmpty()) add(ACTION_SCHEDULE)
        if (conditionsWeakened(old, new)) add(ACTION_CONDITION)
        if ((old.subApps - new.subApps).isNotEmpty()) add(ACTION_SUBAPP)
    }.distinct()

    /**
     * Con «todas», añadir una condición hace que el perfil se active menos; con «alguna», lo hace quitarla.
     * Quitar redes o mover el lugar estrecha la condición en los dos modos; pasar de «alguna» a «todas» también.
     */
    private fun conditionsWeakened(old: Profile, new: Profile): Boolean {
        if (old.conditionMode == ConditionMode.ANY && new.conditionMode == ConditionMode.ALL) return true
        if ((old.wifiNames - new.wifiNames).isNotEmpty() || (old.bluetoothNames - new.bluetoothNames).isNotEmpty()) return true
        return if (new.conditionMode == ConditionMode.ALL) {
            (old.wifiNames.isEmpty() && new.wifiNames.isNotEmpty()) ||
                (old.bluetoothNames.isEmpty() && new.bluetoothNames.isNotEmpty()) ||
                (!old.requireCharging && new.requireCharging) ||
                (new.geofence != null && new.geofence != old.geofence)
        } else {
            (old.requireCharging && !new.requireCharging) || (old.geofence != null && new.geofence != old.geofence)
        }
    }

    /** Apagar las apps nuevas o acortar su plazo las deja pasar antes. */
    fun newAppsWeakened(old: NewAppsPolicy, new: NewAppsPolicy): Boolean = old.enabled && (!new.enabled || new.hours < old.hours)

    /** Apagar el ajuste, quitar apps o pasar de «todas» a «las elegidas» deja pasar vistas web que antes se bloqueaban. */
    fun embeddedBrowsersWeakened(old: EmbeddedBrowserPolicy, new: EmbeddedBrowserPolicy): Boolean = old.enabled && when {
        !new.enabled -> true
        new.allApps -> false
        old.allApps -> true
        else -> (old.apps - new.apps).isNotEmpty()
    }

    private fun relaxes(oldAmount: Int, newAmount: Int, sameKindAndPeriod: Boolean) =
        !sameKindAndPeriod || newAmount > oldAmount
}
