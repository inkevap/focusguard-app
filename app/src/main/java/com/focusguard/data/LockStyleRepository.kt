package com.focusguard.data

import android.content.Context
import com.focusguard.model.LockStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Estilo de la pantalla de bloqueo, guardado en preferencias. Cambiarlo no debilita ningún bloqueo, y el texto de motivos
 * protegidos no se puede sustituir.
 */
class LockStyleRepository(context: Context) {

    private val prefs = context.getSharedPreferences("lock_style", Context.MODE_PRIVATE)

    private val _style = MutableStateFlow(read())
    val style: StateFlow<LockStyle> = _style.asStateFlow()

    fun save(style: LockStyle) {
        val clean = style.clamped()
        prefs.edit().putString(KEY, LockStyleJson.toJson(clean).toString()).apply()
        _style.value = clean
    }

    private fun read(): LockStyle =
        prefs.getString(KEY, null)?.let { runCatching { LockStyleJson.fromJson(JSONObject(it)).clamped() }.getOrNull() } ?: LockStyle()

    private companion object {
        const val KEY = "style"
    }
}
