package com.focusguard.decision

import com.focusguard.model.BlockMode
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebRule

/**
 * Un perfil de partida con nombre, apps, webs, horario y límite: el usuario lo crea con un toque y después lo edita como cualquier otro.
 * Las hay genéricas (van en el repositorio) y personales (solo en el APK de cada usuario).
 */
data class ProfileTemplate(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
    val mode: BlockMode = BlockMode.BLOCK,
    val categories: Set<String> = emptySet(),
    val apps: Set<String> = emptySet(),
    val webs: List<WebRule> = emptyList(),
    val intervals: List<ScheduleInterval> = emptyList(),
    val limit: UsageLimit? = null,
) {
    /**
     * El perfil que sale de la plantilla en este teléfono: solo las apps que [installed] dice que están (una app que no se tiene no
     * pinta nada en la lista) y, en modo bloquear, las apps instaladas y las webs de sus categorías, como si se hubieran marcado a mano.
     */
    fun toProfile(table: CategoryTable, installed: Set<String>): Profile {
        val own = apps.filterTo(linkedSetOf()) { it in installed }
        val base = Profile(
            id = 0,
            title = title,
            emoji = emoji,
            mode = mode,
            apps = own,
            webs = webs,
            intervals = intervals,
            limit = limit,
            categories = if (mode == BlockMode.BLOCK) categories else emptySet(),
        )
        if (mode != BlockMode.BLOCK || categories.isEmpty()) return base
        val added = CategoryExpansion.expand(categories, table, installed, base.apps, base.webs)
        return base.copy(apps = base.apps + added.apps, webs = base.webs + added.webs)
    }
}
