package com.focusguard

import android.content.Context

/** Nombre visible de una app instalada; si [target] no es un paquete (por ejemplo un dominio), se devuelve tal cual. */
internal fun Context.appLabel(target: String): String = runCatching {
    packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
}.getOrDefault(target)
