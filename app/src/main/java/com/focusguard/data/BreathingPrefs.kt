package com.focusguard.data

import android.content.Context
import com.focusguard.decision.BreathingPatterns

/** Lo que se recuerda del ejercicio de respiración: el último ritmo y si marca las fases con vibración o con un sonido suave. */
class BreathingPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("breathing", Context.MODE_PRIVATE)

    /** El último ritmo elegido; si el guardado ya no existe, el de siempre. */
    var patternId: String
        get() = BreathingPatterns.byId(prefs.getString(KEY_PATTERN, null) ?: BreathingPatterns.CALM.id).id
        set(value) = prefs.edit().putString(KEY_PATTERN, value).apply()

    /** Vibración suave al cambiar de fase: encendida de serie. */
    var vibrate: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    /** Un sonido suave al cambiar de fase: apagado de serie (suena aunque el teléfono esté en silencio, así que se pide a propósito). */
    var sound: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    private companion object {
        const val KEY_PATTERN = "pattern"
        const val KEY_VIBRATE = "vibrate"
        const val KEY_SOUND = "sound"
    }
}
