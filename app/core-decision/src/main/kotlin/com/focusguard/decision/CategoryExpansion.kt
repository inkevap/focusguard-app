package com.focusguard.decision

import com.focusguard.model.WebMatchType
import com.focusguard.model.WebRule

/**
 * Al marcar una categoría en un perfil, lo que contiene se **añade a sus listas** como entradas explícitas: así el usuario ve qué se
 * bloquea y puede quitarlo o completarlo. Solo se añade lo que aún no está: lo que ya había (o lo que cubre una regla web más amplia)
 * no se repite. El vínculo con la categoría lo conserva el perfil y es el que hace que las apps nuevas entren al instalarse.
 */
object CategoryExpansion {

    data class Added(val apps: Set<String> = emptySet(), val webs: List<WebRule> = emptyList()) {
        val isEmpty: Boolean get() = apps.isEmpty() && webs.isEmpty()

        operator fun plus(other: Added) = Added(apps + other.apps, webs + other.webs)
    }

    /** Las apps **instaladas** de [categories] y sus webs que no están ya en [apps] ni en [webs]. */
    fun expand(
        categories: Set<String>,
        table: CategoryTable,
        installed: Set<String>,
        apps: Set<String>,
        webs: List<WebRule>,
    ): Added {
        val newApps = linkedSetOf<String>()
        val newWebs = mutableListOf<WebRule>()
        for (category in categories.sorted()) {
            table.packagesOf(category).filter { it in installed && it !in apps }.sorted().forEach(newApps::add)
            table.domainsOf(category).sorted().forEach { domain ->
                if (WebMatcher.firstMatch(webs, domain) == null && WebMatcher.firstMatch(newWebs, domain) == null) {
                    newWebs += WebRule(domain, WebMatchType.EXACT)
                }
            }
        }
        return Added(newApps, newWebs)
    }
}
