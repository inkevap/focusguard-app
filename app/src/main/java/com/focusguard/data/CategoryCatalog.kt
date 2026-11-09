package com.focusguard.data

import android.content.Context
import com.focusguard.decision.CategoryTable

/** Tabla de categorías de apps y webs. Arranca con `assets/apps_webs_categories.csv` (de `data/seed/`); en F4 la sustituirá la del servidor. */
object CategoryCatalog {

    fun fromAssets(context: Context): CategoryTable =
        parse(context.assets.open("apps_webs_categories.csv").bufferedReader().use { it.readText() })

    /** Líneas `paquete,dominio,categoría`; las categorías «none» no sirven para bloquear y se dejan fuera. */
    internal fun parse(csv: String): CategoryTable {
        val packages = mutableMapOf<String, String>()
        val domains = mutableMapOf<String, String>()
        csv.lineSequence()
            .map { line -> line.split(',').map { it.trim() } }
            .filter { it.size >= 3 && it[2] != "none" }
            .forEach { (pkg, domain, category) ->
                if (pkg.isNotEmpty()) packages[pkg] = category
                if (domain.isNotEmpty()) domains[domain] = category
            }
        return CategoryTable(packages, domains)
    }
}
