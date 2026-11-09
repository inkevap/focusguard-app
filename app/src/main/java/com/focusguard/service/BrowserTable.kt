package com.focusguard.service

import android.content.Context
import org.json.JSONArray

/**
 * Tabla navegador → identificador de la barra de direcciones. Arranca con
 * `assets/browser_view_ids.json` (de `data/seed/`); en F4 se sustituirá por la del servidor.
 */
class BrowserTable(private val addressBarIds: Map<String, String>, private val incognitoIds: Map<String, String> = emptyMap()) {

    fun isBrowser(packageName: String) = packageName in addressBarIds

    /** Identificador completo (`paquete:id/nombre`) de la barra de direcciones, si se conoce. */
    fun addressBarViewId(packageName: String): String? =
        addressBarIds[packageName]?.let { packageName + it }

    /** Identificador completo de la insignia de incógnito del navegador, si se conoce. */
    fun incognitoViewId(packageName: String): String? = incognitoIds[packageName]?.let { packageName + it }

    companion object {
        fun fromAssets(context: Context) = BrowserTable(readIds(context, "browser_view_ids.json"), readIds(context, "incognito_view_ids.json"))

        private fun readIds(context: Context, asset: String): Map<String, String> {
            val array = JSONArray(context.assets.open(asset).bufferedReader().use { it.readText() })
            return buildMap {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    put(item.getString("packageName"), item.getString("viewId"))
                }
            }
        }
    }
}
