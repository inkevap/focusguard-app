package com.focusguard.decision

import com.focusguard.model.BlockReason
import com.focusguard.model.PartnerEventType
import com.focusguard.model.StrictProtection

/**
 * Cada función de la app cuyo cambio puede pasar por el compañero. Con un área elegida, aflojarla o apagarla pide siempre su aprobación,
 * aunque no haya modo estricto ni el método de aprobaciones sea otro. Cada área agrupa las claves de las solicitudes de [Approvals].
 */
enum class PartnerArea {
    PROFILES_EDIT,
    PROFILES_PAUSE,
    ADULT_FILTER,
    DNS_FILTER,
    IMAGES,
    NEW_APPS,
    EMBEDDED_BROWSERS,
    FLOATING_WINDOWS,
    APPROVAL_MODE,
    DAY_START,
    SERVER,
    PARTNER_SETTINGS,
    LAUNCHER_ICON,
    STREAK,
    ;

    companion object {
        /** El área a la que pertenece una solicitud, o null si no es de ninguna. */
        fun of(actionKey: String): PartnerArea? = when {
            actionKey.startsWith("profile_edit:") || actionKey.startsWith("profile_off:") || actionKey.startsWith("profile_delete:") -> PROFILES_EDIT
            actionKey.startsWith("profile_pause:") || actionKey.startsWith("session_end:") -> PROFILES_PAUSE
            actionKey in setOf("adult_off", "adult_word_remove", "adult_allow", "adult_text_exempt") -> ADULT_FILTER
            actionKey == "dns_off" || actionKey == "dns_exclude" -> DNS_FILTER
            actionKey.startsWith("image_") -> IMAGES
            actionKey == "new_apps" -> NEW_APPS
            actionKey == "embedded_browsers" -> EMBEDDED_BROWSERS
            actionKey == "fold_floating" -> FLOATING_WINDOWS
            actionKey == "approval_mode" -> APPROVAL_MODE
            actionKey == "day_start" -> DAY_START
            actionKey == "server_change" || actionKey == "sign_out" -> SERVER
            actionKey in setOf("partner_notices_off", "parental_off", "urge_sharing_off", "partner_panel") -> PARTNER_SETTINGS
            actionKey == "launcher_icon" -> LAUNCHER_ICON
            actionKey.startsWith("streak_restore:") -> STREAK
            else -> null
        }
    }
}

/**
 * Lo que se elige para cada forma de tener compañero: «Avisarle lo que pasa» y el modo parental. En cada una:
 * - [noticesOff]: tipos de aviso que el teléfono **no** manda (de serie, ninguno).
 * - [blockedReasonsOff] y [blockedProfilesOff]: de los bloqueos, qué clases de cosa y qué perfiles **no** se avisan (de serie, ninguno).
 * - [protections]: protecciones del modo estricto que valen siempre que haya compañero, sin necesitar el modo estricto. Con la de editar,
 *   todos los cambios que aflojan algo pasan por la aprobación del compañero en vez de rechazarse.
 * - [areas]: funciones cuyo cambio aprueba siempre el compañero (de serie, ninguna).
 */
data class PartnerChoices(
    val noticesOff: Set<PartnerEventType> = emptySet(),
    val protections: Set<StrictProtection> = emptySet(),
    val areas: Set<PartnerArea> = emptySet(),
    val blockedReasonsOff: Set<BlockReason> = emptySet(),
    val blockedProfilesOff: Set<Long> = emptySet(),
) {
    fun allows(type: PartnerEventType) = type !in noticesOff

    /** ¿Se avisa de este bloqueo? Hace falta que los bloqueos estén encendidos y que ni su clase ni su perfil estén apagados. */
    fun allowsBlock(reason: BlockReason, profileId: Long) =
        allows(PartnerEventType.BLOCKED) && reason !in blockedReasonsOff && profileId !in blockedProfilesOff
}

data class PartnerPanel(
    val watch: PartnerChoices = PartnerChoices(),
    val parental: PartnerChoices = PartnerChoices(),
) {
    /** Lo que vale ahora: el modo parental si está encendido, y si no, «Avisarle lo que pasa». */
    fun active(parentalOn: Boolean) = if (parentalOn) parental else watch

    /** ¿Pasar a [next] debilita algo? Apagar un aviso, quitar una protección o un área, en cualquiera de las dos. Añadir es libre. */
    fun weakens(next: PartnerPanel): Boolean = weakens(watch, next.watch) || weakens(parental, next.parental)

    private fun weakens(old: PartnerChoices, new: PartnerChoices) =
        (new.noticesOff - old.noticesOff).isNotEmpty() ||
            (new.blockedReasonsOff - old.blockedReasonsOff).isNotEmpty() ||
            (new.blockedProfilesOff - old.blockedProfilesOff).isNotEmpty() ||
            (old.protections - new.protections).isNotEmpty() ||
            (old.areas - new.areas).isNotEmpty()

    fun encode(): String = listOf(watch, parental).joinToString("|") { c ->
        listOf(
            c.noticesOff.map { it.name }.sorted(),
            c.protections.map { it.name }.sorted(),
            c.areas.map { it.name }.sorted(),
            c.blockedReasonsOff.map { it.name }.sorted(),
            c.blockedProfilesOff.sorted().map { it.toString() },
        ).joinToString(";") { it.joinToString(",") }
    }

    companion object {
        /** Los avisos que el usuario puede elegir (los que manda el teléfono). */
        val NOTICE_TYPES: List<PartnerEventType> = PartnerEventType.entries

        /** Las protecciones que se pueden poner sin el modo estricto. */
        val PROTECTIONS: List<StrictProtection> = StrictProtection.entries

        /** Las clases de bloqueo de las que se puede elegir avisar (las que generan el aviso de «intentó abrir algo bloqueado»). */
        val BLOCK_REASONS: List<BlockReason> = listOf(
            BlockReason.PROFILE_APP, BlockReason.PROFILE_WEB, BlockReason.NOT_ALLOWED, BlockReason.LIMIT_REACHED, BlockReason.SUB_APP,
            BlockReason.NEW_APP, BlockReason.EMBEDDED_BROWSER, BlockReason.ADULT_CONTENT, BlockReason.IMAGE_CONTENT,
        )

        /** Acepta el formato de antes (solo avisos y protecciones) y el actual; lo que no se entiende se descarta entero. */
        fun decode(text: String): PartnerPanel? {
            val parts = text.split('|')
            if (parts.size != 2) return null
            val choices = parts.map { part ->
                val halves = part.split(';')
                if (halves.size != 2 && halves.size != 5) return null
                fun <T> names(i: Int, find: (String) -> T?): Set<T>? {
                    val out = mutableSetOf<T>()
                    for (n in halves.getOrElse(i) { "" }.split(',').filter { it.isNotBlank() }) out += find(n) ?: return null
                    return out
                }
                PartnerChoices(
                    noticesOff = names(0) { n -> PartnerEventType.entries.firstOrNull { it.name == n } } ?: return null,
                    protections = names(1) { n -> StrictProtection.entries.firstOrNull { it.name == n } } ?: return null,
                    areas = names(2) { n -> PartnerArea.entries.firstOrNull { it.name == n } } ?: return null,
                    blockedReasonsOff = names(3) { n -> BlockReason.entries.firstOrNull { it.name == n } } ?: return null,
                    blockedProfilesOff = names(4) { n -> n.toLongOrNull() } ?: return null,
                )
            }
            return PartnerPanel(choices[0], choices[1])
        }
    }
}
