package com.focusguard.data

import android.content.Context
import com.focusguard.decision.SubAppDetector
import com.focusguard.model.SubAppRule
import org.json.JSONArray
import org.json.JSONObject

/** Tabla de secciones dentro de apps. Arranca con `assets/subapps.json` (de `data/seed/`); en F4 la sustituirá la del servidor. */
class SubAppCatalog(val rules: List<SubAppRule>) {

    val detector = SubAppDetector(rules)

    companion object {
        fun fromAssets(context: Context): SubAppCatalog {
            val json = context.assets.open("subapps.json").bufferedReader().use { it.readText() }
            return parse(JSONArray(json))
        }

        internal fun parse(array: JSONArray) = SubAppCatalog(
            (0 until array.length()).map { rule(array.getJSONObject(it)) },
        )

        private fun rule(item: JSONObject) = SubAppRule(
            id = item.getString("id"),
            label = item.getString("label"),
            packages = item.stringSet("packages"),
            viewIds = item.stringSet("viewIds"),
            activity = item.optString("activity").takeIf { it.isNotEmpty() },
            texts = item.stringSet("texts"),
        )
    }
}
