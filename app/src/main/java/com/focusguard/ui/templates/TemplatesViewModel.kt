package com.focusguard.ui.templates

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ProfileRepository
import com.focusguard.data.Templates
import com.focusguard.decision.CategoryTable
import com.focusguard.decision.ProfileTemplate
import com.focusguard.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Una plantilla con el perfil que saldría en este teléfono, para enseñar qué incluye antes de crearlo. */
data class TemplateItem(val template: ProfileTemplate, val preview: Profile)

data class TemplateLists(val personal: List<TemplateItem> = emptyList(), val generic: List<TemplateItem> = emptyList(), val loaded: Boolean = false)

class TemplatesViewModel(
    private val repository: ProfileRepository,
    private val templates: Templates,
    private val categories: CategoryTable,
    /** Los paquetes instalados con icono; consulta al sistema, así que se llama fuera del hilo principal. */
    private val installedPackages: () -> Set<String>,
) : ViewModel() {

    var lists by mutableStateOf(TemplateLists())
        private set

    init {
        viewModelScope.launch {
            val installed = withContext(Dispatchers.Default) { runCatching(installedPackages).getOrDefault(emptySet()) }
            fun items(list: List<ProfileTemplate>) = list.map { TemplateItem(it, it.toProfile(categories, installed)) }
            lists = TemplateLists(items(templates.personal), items(templates.generic), loaded = true)
        }
    }

    /** Crea el perfil de la plantilla. Crear nunca afloja nada, así que no pasa por el modo estricto ni por las aprobaciones. */
    fun create(item: TemplateItem, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repository.save(item.preview)) }
    }
}
