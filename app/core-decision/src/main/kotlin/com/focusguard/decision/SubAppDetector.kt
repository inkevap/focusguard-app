package com.focusguard.decision

import com.focusguard.model.SubAppRule

/** Reconoce qué sección de una app está en pantalla. La lectura de la pantalla la aporta quien llama. */
class SubAppDetector(rules: List<SubAppRule>) {

    private val byPackage: Map<String, List<SubAppRule>> =
        rules.flatMap { rule -> rule.packages.map { it to rule } }.groupBy({ it.first }, { it.second })

    /** ¿Alguna regla habla de esta app? Evita leer la pantalla del resto. */
    fun covers(packageName: String) = packageName in byPackage

    /**
     * La primera regla activada que coincide. Coincide si aparece alguno de sus identificadores de vista,
     * o si la actividad es la indicada y todos sus textos están visibles.
     */
    fun detect(
        enabled: Set<String>,
        packageName: String,
        className: String?,
        hasViewId: (String) -> Boolean,
        hasText: (String) -> Boolean,
    ): SubAppRule? = byPackage[packageName]?.firstOrNull { rule ->
        rule.id in enabled && (
            rule.viewIds.any { hasViewId("$packageName:id/$it") } ||
                (rule.activity != null && className == rule.activity && rule.texts.all(hasText))
            )
    }
}
