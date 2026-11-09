package com.focusguard.ui.adult

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.AdultFilterProvider
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.BlocklistRepository
import com.focusguard.data.ChangeGuard
import com.focusguard.data.SettingsRepository
import com.focusguard.decision.AdultContent
import com.focusguard.decision.AllowPhrase
import com.focusguard.decision.Approvals
import com.focusguard.decision.UserWebValidator
import com.focusguard.server.ApiResult
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

/** Lo que enseña la pantalla de contenido adulto. */
data class AdultUiState(
    val domains: Int = 0,
    val words: Int = 0,
    val version: Long = 0,
    val offUntilMs: Long? = null,
    val allowed: List<String> = emptyList(),
    val userWords: List<String> = emptyList(),
    /** Las apps que el usuario eligió para no leer su texto (las de serie no salen aquí). */
    val exemptApps: List<String> = emptyList(),
)

class AdultViewModel(
    private val settings: SettingsRepository,
    private val blocklists: BlocklistRepository,
    private val filter: AdultFilterProvider,
    private val guard: ChangeGuard,
) : ViewModel() {

    val state: StateFlow<AdultUiState> = combine(
        blocklists.lists, settings.adultOffUntilMs, settings.adultAllowed, settings.adultWords, settings.adultTextExemptApps,
    ) { lists, offUntil, allowed, words, exempt ->
        val domains = lists[BlocklistRepository.DOMAINS]
        AdultUiState(
            domains = domains?.values?.size ?: 0,
            words = BlocklistRepository.WORDS.sumOf { lists[it]?.values?.size ?: 0 },
            version = domains?.version ?: 0,
            offUntilMs = offUntil,
            allowed = allowed.sorted(),
            userWords = words.sorted(),
            exemptApps = exempt.sorted(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdultUiState())

    var message by mutableStateOf<String?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set

    fun clearMessage() {
        message = null
    }

    fun refreshLists() {
        if (syncing) return
        syncing = true
        viewModelScope.launch {
            try {
                message = when (val result = blocklists.sync()) {
                    is ApiResult.Ok -> "Listas al día."
                    is ApiResult.Failure -> result.error.message
                }
            } finally {
                syncing = false
            }
        }
    }

    /** Añadir una web o palabra propia es libre (endurece el filtro). */
    fun addWord(raw: String): Boolean {
        val entry = raw.clean()
        val result = if ('.' in entry) UserWebValidator.validate(entry, Locale.getDefault().language, filter.filter.value) else null
        var added = false
        message = when {
            entry.isEmpty() -> "Escribe una palabra o una web."
            result is UserWebValidator.Result.NoDot -> "Una web lleva al menos un punto, por ejemplo ejemplo.com."
            result is UserWebValidator.Result.TooShort -> "Esa web es demasiado corta."
            result is UserWebValidator.Result.AlreadyBlocked -> "Ya se bloquea sola: no hace falta añadirla."
            result is UserWebValidator.Result.Valid -> {
                settings.addAdultWord(result.web)
                added = true
                "Añadida."
            }
            AdultContent.normalize(entry).joined.length < AdultContent.MIN_WORD_LENGTH -> "La palabra necesita al menos ${AdultContent.MIN_WORD_LENGTH} letras."
            else -> {
                settings.addAdultWord(entry)
                added = true
                "Añadida."
            }
        }
        return added
    }

    /** Quitar una palabra propia debilita el filtro: pasa por el modo estricto y las aprobaciones. */
    fun removeWord(entry: String) {
        val blocked = guard.blockedMessage(ApprovalExecutor.KEY_ADULT_WORD_REMOVE, Approvals.ACTION_ADULT_WORD_REMOVE, entry)
        if (blocked == null) settings.removeAdultWord(entry) else message = blocked
    }

    /** Dejar pasar algo exige teclear la frase a mano (D7) y que el modo estricto no esté bloqueando cambios. */
    fun allow(raw: String, phrase: String): Boolean {
        val entry = raw.clean()
        var allowed = false
        message = when {
            AdultContent.normalize(entry).joined.length < AdultContent.MIN_WORD_LENGTH -> "Escribe qué dejar pasar (al menos ${AdultContent.MIN_WORD_LENGTH} letras)."
            guard.isLocked() -> ChangeGuard.LOCKED
            !AllowPhrase.matches(phrase) -> "Escribe la frase tal como aparece para confirmar."
            settings.adultAllowed.value.size >= MAX_ALLOWED -> "Hay demasiadas entradas permitidas: quita alguna antes."
            else -> {
                // Dejar pasar algo debilita el filtro: además de la frase, pasa por las aprobaciones.
                val blocked = guard.blockedMessage(ApprovalExecutor.KEY_ADULT_ALLOW, Approvals.ACTION_ADULT_ALLOW, entry)
                if (blocked == null) settings.addAdultAllowed(entry)
                allowed = blocked == null
                blocked ?: "Permitido."
            }
        }
        return allowed
    }

    /** Dejar de leer el texto de una app debilita el filtro: pasa por las aprobaciones, de una app en una. Los navegadores no se pueden elegir (la web se mira siempre). */
    fun exemptApps(packages: Set<String>, isBrowser: (String) -> Boolean) {
        val added = (packages - settings.adultTextExemptApps.value).filterNot(isBrowser)
        if (added.isEmpty()) {
            if (packages.any(isBrowser)) message = "Un navegador no se puede dejar sin leer: sus webs se miran siempre."
            return
        }
        val first = added.min()
        val blocked = guard.blockedMessage(ApprovalExecutor.KEY_ADULT_TEXT_EXEMPT, Approvals.ACTION_ADULT_TEXT_EXEMPT, first)
        if (blocked != null) {
            message = if (added.size > 1) "$blocked Solo se pide una app cada vez: elige las demás cuando esté aprobada." else blocked
            return
        }
        added.forEach(settings::addAdultTextExemptApp)
    }

    /** Volver a leer el texto de una app endurece el filtro: es libre. */
    fun readAgain(app: String) = settings.removeAdultTextExemptApp(app)

    /** Quitar algo de los permitidos endurece el filtro: es libre. */
    fun disallow(entry: String) = settings.removeAdultAllowed(entry)

    private companion object {
        const val MAX_ALLOWED = 50
    }

    private fun String.clean() = trim().lowercase(Locale.ROOT)
}
