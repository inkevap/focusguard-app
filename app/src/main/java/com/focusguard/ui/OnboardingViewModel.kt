package com.focusguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ProfileRepository
import com.focusguard.data.SettingsRepository
import com.focusguard.decision.CategoryExpansion
import com.focusguard.decision.CategoryTable
import com.focusguard.decision.Onboarding
import com.focusguard.decision.OnboardingWhen
import com.focusguard.model.ProfileKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bienvenida de la primera vez (O3): crea un primer perfil con dos respuestas y no vuelve a salir. */
class OnboardingViewModel(
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
    private val categoryTable: CategoryTable,
    private val installedPackages: () -> Set<String>,
) : ViewModel() {

    private val _needed = MutableStateFlow<Boolean?>(if (settings.onboardingDone.value) false else null)

    /** ¿Hay que enseñar la bienvenida? Null mientras se comprueba, para no enseñarla un instante a quien ya usaba la app. */
    val needed: StateFlow<Boolean?> = _needed.asStateFlow()

    init {
        // «Repetir el asistente» en Ajustes lo vuelve a poner: cuando ya se había pasado y de pronto deja de estarlo, se enseña de nuevo.
        viewModelScope.launch {
            settings.onboardingDone.collect { done -> if (!done && _needed.value == false) _needed.value = true }
        }
        if (_needed.value == null) {
            viewModelScope.launch {
                // Quien ya tenía perfiles (una versión anterior) no necesita la bienvenida.
                // Si no se puede leer, mejor no estorbar: se entra a la app directamente.
                val hadProfiles = runCatching { profiles.profiles.first().any { it.kind == ProfileKind.NORMAL } }.getOrDefault(true)
                if (hadProfiles) settings.setOnboardingDone()
                _needed.value = !hadProfiles
            }
        }
    }

    /** Termina la bienvenida; si se eligió algo que cuidar, deja creado el primer perfil. */
    fun finish(categories: Set<String>, whenToBlock: OnboardingWhen) {
        // Un segundo toque mientras se guarda no crea otro perfil: la pantalla ya no está.
        if (_needed.value != true) return
        skip()
        if (categories.isNotEmpty()) {
            viewModelScope.launch {
                // Igual que al marcar una categoría en un perfil: sus apps instaladas y sus webs entran en las listas, a la vista.
                val first = Onboarding.firstProfile(categories, whenToBlock)
                val installed = withContext(Dispatchers.Default) { runCatching(installedPackages).getOrDefault(emptySet()) }
                val added = CategoryExpansion.expand(categories, categoryTable, installed, first.apps, first.webs)
                profiles.save(first.copy(apps = first.apps + added.apps, webs = first.webs + added.webs))
            }
        }
    }

    fun skip() {
        settings.setOnboardingDone()
        _needed.value = false
    }
}
