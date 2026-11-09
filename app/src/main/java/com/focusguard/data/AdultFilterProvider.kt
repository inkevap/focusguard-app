package com.focusguard.data

import com.focusguard.decision.AdultFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * El filtro de contenido adulto al día: se vuelve a armar cuando llegan listas nuevas del servidor o el usuario cambia las suyas.
 * Armar un filtro con decenas de miles de dominios cuesta un momento, así que se hace fuera del hilo principal y mientras tanto
 * sigue valiendo el anterior.
 */
class AdultFilterProvider(blocklists: BlocklistRepository, settings: SettingsRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch { blocklists.load() }
    }

    val filter: StateFlow<AdultFilter> = combine(blocklists.lists, settings.adultAllowed, settings.adultWords) { lists, allowed, words ->
        fun values(name: String) = lists[name]?.values.orEmpty()
        AdultFilter(
            domains = values(BlocklistRepository.DOMAINS),
            words = BlocklistRepository.WORDS.flatMap(::values),
            exceptions = values(BlocklistRepository.EXCEPTIONS),
            allowed = allowed,
            userWords = words,
        )
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, AdultFilter(emptyList(), emptyList()))
}
