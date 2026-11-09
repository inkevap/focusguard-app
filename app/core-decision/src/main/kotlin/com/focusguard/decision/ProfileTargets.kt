package com.focusguard.decision

import com.focusguard.model.BlockReason
import com.focusguard.model.ForegroundState
import com.focusguard.model.Profile
import com.focusguard.model.BlockMode

/** Qué lista un perfil del estado que hay en pantalla: sus apps, sus webs y, en modo bloquear, sus categorías. */
object ProfileTargets {

    /** [reason] distingue app y web; [target] es el paquete, la regla o el dominio que coincidió. */
    data class Listed(val reason: BlockReason, val target: String)

    fun listed(profile: Profile, state: ForegroundState, categories: CategoryTable = CategoryTable.EMPTY): Listed? {
        if (state.packageName in profile.apps) return Listed(BlockReason.PROFILE_APP, state.packageName)
        val url = state.url
        if (url != null) WebMatcher.firstMatch(profile.webs, url)?.let { return Listed(BlockReason.PROFILE_WEB, it.value) }
        // En «permitir solo esto» las listas son de lo permitido: las categorías no pintan nada.
        if (profile.mode != BlockMode.BLOCK || profile.categories.isEmpty()) return null
        categories.ofPackage(state.packageName)?.takeIf { it in profile.categories }?.let { return Listed(BlockReason.PROFILE_APP, state.packageName) }
        if (url != null && categories.ofUrl(url)?.let { it in profile.categories } == true) return Listed(BlockReason.PROFILE_WEB, UrlNormalizer.host(url))
        return null
    }

    /**
     * El contador que le toca a [state] en un límite por app o web: el mismo [Listed.target] con el que el decisor bloquea.
     * Null si el perfil no lo toca (en «permitir solo esto», si está permitido).
     */
    fun counterOf(profile: Profile, state: ForegroundState, categories: CategoryTable = CategoryTable.EMPTY): String? = when (profile.mode) {
        BlockMode.BLOCK -> listed(profile, state, categories)?.target
        BlockMode.ALLOW_ONLY -> when {
            state.packageName in profile.apps -> null
            state.isBrowser && profile.webs.isNotEmpty() -> state.url?.takeIf { WebMatcher.firstMatch(profile.webs, it) == null }?.let(UrlNormalizer::host)
            else -> state.packageName
        }
    }
}
