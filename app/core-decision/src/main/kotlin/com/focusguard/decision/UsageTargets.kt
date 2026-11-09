package com.focusguard.decision

import com.focusguard.model.BlockMode
import com.focusguard.model.DeviceContext
import com.focusguard.model.ForegroundState
import com.focusguard.model.Profile
import java.time.Instant
import java.time.LocalDateTime

/** Decide qué cuenta como "uso" de un perfil para sus límites. */
object UsageTargets {

    /**
     * En modo bloquear cuenta lo que el perfil lista (apps y webs). En modo permitir solo esto
     * cuenta todo lo que queda fuera de la lista y de las excepciones, que es lo que acabará bloqueando.
     */
    fun isUsing(profile: Profile, state: ForegroundState, neverBlock: Set<String>, categories: CategoryTable = CategoryTable.EMPTY): Boolean {
        if (state.packageName in neverBlock) return false
        val inList = ProfileTargets.listed(profile, state, categories) != null
        return when (profile.mode) {
            BlockMode.BLOCK -> inList
            BlockMode.ALLOW_ONLY -> !inList
        }
    }

    /**
     * ¿Suma esto al límite del perfil? Solo si el perfil estaría activo sin el límite (horario, condiciones, pausa, sesión):
     * es la misma regla con la que el decisor bloquea, así que el uso fuera de horario no gasta el límite.
     */
    fun counts(
        profile: Profile,
        state: ForegroundState,
        neverBlock: Set<String>,
        categories: CategoryTable,
        localNow: LocalDateTime,
        now: Instant,
        context: () -> DeviceContext,
    ): Boolean = isUsing(profile, state, neverBlock, categories) && ProfileActivation.isActive(profile, localNow, now, context())
}
