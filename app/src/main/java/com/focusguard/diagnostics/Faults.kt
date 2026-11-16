package com.focusguard.diagnostics

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.focusguard.decision.Fault
import com.focusguard.decision.FaultLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * El registro de fallos de toda la app, guardado en el teléfono (sobrevive a reinicios y a días sin mirarlo) y visible en Ajustes. Apunta los fallos que la
 * app supera sin cerrarse y también los que la cierran. No sale del teléfono: solo se comparte si el usuario lo copia. Se instala al arrancar la app
 * ([install]); antes de eso los fallos solo van al registro del sistema.
 */
object Faults {
    private const val TAG = "FGfallo"
    private const val PREFS = "fault_log"
    private const val KEY = "faults"

    @Volatile private var prefs: SharedPreferences? = null
    private val _log = MutableStateFlow<List<Fault>>(emptyList())
    val log: StateFlow<List<Fault>> = _log.asStateFlow()

    fun install(context: Context) {
        val store = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _log.value = FaultLog.decode(store.getString(KEY, "").orEmpty())
        // Un fallo que cierra la app se apunta antes de que se cierre (guardado al momento), y luego sigue su curso normal.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(SOURCE_CRASH, "hilo ${thread.name}: ${FaultLog.describe(error)}", now = true) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Apunta un fallo. [source] es la parte de la app (p. ej. «imágenes»); si hay [error], se añade su tipo y dónde saltó. */
    fun record(source: String, message: String, error: Throwable? = null) {
        val text = if (error == null) message else "$message · ${FaultLog.describe(error)}"
        Log.w(TAG, "[$source] $text")
        runCatching { write(source, text, now = false) }
    }

    @Synchronized
    fun clear() {
        _log.value = emptyList()
        prefs?.edit()?.remove(KEY)?.apply()
    }

    @Synchronized
    private fun write(source: String, text: String, now: Boolean) {
        val next = FaultLog.add(_log.value, System.currentTimeMillis(), source, text)
        _log.value = next
        val edit = prefs?.edit()?.putString(KEY, FaultLog.encode(next)) ?: return
        if (now) edit.commit() else edit.apply()
    }

    private const val SOURCE_CRASH = "cierre de la app"
}
