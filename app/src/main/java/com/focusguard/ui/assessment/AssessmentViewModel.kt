package com.focusguard.ui.assessment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.AccountRepository
import com.focusguard.data.AssessmentResultEntity
import com.focusguard.data.AssessmentStore
import com.focusguard.data.SavedResult
import com.focusguard.decision.Assessment
import com.focusguard.server.ApiResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AssessmentViewModel(private val store: AssessmentStore, private val account: AccountRepository) : ViewModel() {
    /** Las respuestas de ahora, una por pregunta; no se guardan en ningún sitio. */
    val answers = mutableStateListOf<Int?>().apply { repeat(Assessment.QUESTIONS.size) { add(null) } }

    /** El resultado del test que se acaba de hacer; `null` mientras se contesta. */
    var result by mutableStateOf<SavedResult?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    val history: StateFlow<List<AssessmentResultEntity>> = store.results.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val signedIn: StateFlow<Boolean> = account.state.map { it.signedIn }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), account.state.value.signedIn)

    fun clearMessage() {
        message = null
    }

    fun answer(question: Int, value: Int) {
        answers[question] = value
    }

    fun finish() {
        val score = Assessment.score(answers.toList()) ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                result = store.save(score)
            } finally {
                busy = false
            }
        }
    }

    /** Guarda en la cuenta el resultado que se acaba de dar (solo la puntuación). */
    fun upload() {
        val saved = result ?: return
        val row = history.value.firstOrNull { it.id == saved.id } ?: return
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                message = when (val r = store.upload(row)) {
                    is ApiResult.Ok -> "Guardado en tu cuenta. Solo la puntuación; las respuestas no salen del teléfono."
                    is ApiResult.Failure -> r.error.message
                }
            } finally {
                busy = false
            }
        }
    }

    fun again() {
        for (i in answers.indices) answers[i] = null
        result = null
    }

    /** Borra los resultados de este teléfono y, con sesión, los de la cuenta. */
    fun clearHistory() {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                message = when (val remote = store.clear()) {
                    null -> "Resultados borrados de este teléfono. Si guardaste alguno en tu cuenta, entra en ella para borrarlo también."
                    is ApiResult.Ok -> "Resultados borrados de este teléfono y de tu cuenta."
                    is ApiResult.Failure -> "Borrados de este teléfono. En tu cuenta no se pudo: ${remote.error.message}"
                }
                // Lo que se enseñaba ya no existe: se vuelve a las preguntas.
                again()
            } finally {
                busy = false
            }
        }
    }
}
