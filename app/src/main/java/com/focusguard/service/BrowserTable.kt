package com.focusguard.service

import android.content.Context
import org.json.JSONArray

/**
 * Tabla navegador → identificador de la barra de direcciones. Arranca con
 * `assets/browser_view_ids.json` (de `data/seed/`); en F4 se sustituirá por la del servidor.
 */
class BrowserTable(private val addressBarIds: Map<String, List<String>>, private val incognitoIds: Map<String, List<String>> = emptyMap()) {

    fun isBrowser(packageName: String) = packageName in addressBarIds

    /**
     * Identificadores completos (`paquete:id/nombre`) de la barra de direcciones, en el orden de la tabla. Un navegador puede tener varios
     * (Chrome: `url_bar` en sus pestañas y `origin` en las pestañas personalizadas); quien lee prueba uno tras otro.
     */
    fun addressBarViewIds(packageName: String): List<String> = addressBarIds[packageName].orEmpty().map { packageName + it }

    /** Identificador completo de la insignia de incógnito del navegador, si se conoce. */
    fun incognitoViewId(packageName: String): String? = incognitoIds[packageName]?.firstOrNull()?.let { packageName + it }

    companion object {
        fun fromAssets(context: Context) = BrowserTable(readIds(context, "browser_view_ids.json"), readIds(context, "incognito_view_ids.json"))

        /** Cada paquete se queda con **todos** sus identificadores de vista (antes una fila repetida pisaba a la anterior: Chrome se quedaba con `origin`). */
        private fun readIds(context: Context, asset: String): Map<String, List<String>> {
            val array = JSONArray(context.assets.open(asset).bufferedReader().use { it.readText() })
            val result = LinkedHashMap<String, MutableList<String>>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val ids = result.getOrPut(item.getString("packageName")) { mutableListOf() }
                // Las filas que no son un id de vista (una etiqueta de Compose) no se pueden buscar así; el paquete sigue contando como navegador.
                if (item.optString("detectionMethod", "VIEW_ID") == "VIEW_ID") ids += item.getString("viewId")
            }
            return result
        }
    }
}
