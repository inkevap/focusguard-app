package com.focusguard.decision

import com.focusguard.model.PartnerEventType
import com.focusguard.model.StrictProtection

/**
 * Lo que se elige para cada forma de tener compañero: «Avisarle lo que pasa» y el modo parental. En cada una:
 * - [noticesOff]: avisos que el teléfono **no** manda (de serie, ninguno: se manda todo lo que el compañero quiera recibir).
 * - [protections]: protecciones del modo estricto que valen siempre que haya compañero, sin necesitar el modo estricto. Con la de editar,
 *   los cambios de perfiles pasan por la aprobación del compañero en vez de rechazarse.
 */
data class PartnerChoices(
    val noticesOff: Set<PartnerEventType> = emptySet(),
    val protections: Set<StrictProtection> = emptySet(),
) {
    fun allows(type: PartnerEventType) = type !in noticesOff
}

data class PartnerPanel(
    val watch: PartnerChoices = PartnerChoices(),
    val parental: PartnerChoices = PartnerChoices(),
) {
    /** Lo que vale ahora: el modo parental si está encendido, y si no, «Avisarle lo que pasa». */
    fun active(parentalOn: Boolean) = if (parentalOn) parental else watch

    /** ¿Pasar a [next] debilita algo? Apagar un aviso o quitar una protección, en cualquiera de las dos. Añadir es libre. */
    fun weakens(next: PartnerPanel): Boolean = weakens(watch, next.watch) || weakens(parental, next.parental)

    private fun weakens(old: PartnerChoices, new: PartnerChoices) =
        (new.noticesOff - old.noticesOff).isNotEmpty() || (old.protections - new.protections).isNotEmpty()

    fun encode(): String = listOf(watch, parental).joinToString("|") { c ->
        c.noticesOff.map { it.name }.sorted().joinToString(",") + ";" + c.protections.map { it.name }.sorted().joinToString(",")
    }

    companion object {
        /** Los avisos que el usuario puede elegir (los que manda el teléfono). */
        val NOTICE_TYPES: List<PartnerEventType> = PartnerEventType.entries

        /** Las protecciones que se pueden poner sin el modo estricto. */
        val PROTECTIONS: List<StrictProtection> = StrictProtection.entries

        fun decode(text: String): PartnerPanel? {
            val parts = text.split('|')
            if (parts.size != 2) return null
            val choices = parts.map { part ->
                val halves = part.split(';')
                if (halves.size != 2) return null
                val notices = halves[0].split(',').filter { it.isNotBlank() }.map { n -> PartnerEventType.entries.firstOrNull { it.name == n } ?: return null }
                val protections = halves[1].split(',').filter { it.isNotBlank() }.map { n -> StrictProtection.entries.firstOrNull { it.name == n } ?: return null }
                PartnerChoices(notices.toSet(), protections.toSet())
            }
            return PartnerPanel(choices[0], choices[1])
        }
    }
}
