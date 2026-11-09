package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.Profile

/** Qué perfiles bloquean lo que se ve en la pantalla de bloqueo. Lógica pura, sin textos: los pone la app. */
object LockBlockers {

    /** Los perfiles de [decision] (el principal primero) que siguen existiendo en [profiles]; sin repetidos. */
    fun of(decision: BlockDecision.Block, profiles: List<Profile>): List<Profile> =
        (listOf(decision.profileId) + decision.otherProfileIds).distinct().mapNotNull { id -> profiles.firstOrNull { it.id == id } }

    /** Los primeros [max] y cuántos quedan fuera, para escribir «A, B y 2 más». */
    fun <T> limit(items: List<T>, max: Int = MAX_NAMED): Pair<List<T>, Int> {
        require(max > 0) { "max debe ser positivo" }
        return items.take(max) to (items.size - max).coerceAtLeast(0)
    }

    const val MAX_NAMED = 3
}
