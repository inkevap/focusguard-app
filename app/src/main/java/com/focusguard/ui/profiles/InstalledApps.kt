package com.focusguard.ui.profiles

import android.content.Context
import android.content.Intent

data class InstalledApp(val packageName: String, val label: String)

/** Los paquetes de las apps con icono en el lanzador. */
fun installedPackages(context: Context): Set<String> = loadInstalledApps(context).mapTo(mutableSetOf()) { it.packageName }

/** Apps con icono en el lanzador, ordenadas por nombre. Requiere `<queries>` en el manifiesto. */
fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}
