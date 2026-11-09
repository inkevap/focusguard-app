package com.focusguard.decision

/** Qué se hace con las notificaciones (J1, J2). Cada opción se activa por separado y está apagada por defecto. */
data class NotificationSettings(
    /** Quitar las notificaciones de apps bloqueadas ahora y guardarlas en el teléfono para verlas después. */
    val captureBlocked: Boolean = false,
    /** Esconder 24 h el aviso del sistema de que FocusGuard sigue en segundo plano. */
    val hideBackgroundNotice: Boolean = false,
)

object NotificationRules {

    /** Lo guardado de cada notificación se recorta: sirve para recordar de qué iba, no para archivar conversaciones. */
    const val MAX_TEXT = 200

    /** Cuántas notificaciones se guardan como máximo y cuántos días. La retención la aplica quien las guarda. */
    const val MAX_KEPT = 200
    const val RETENTION_DAYS = 7L

    /** Tiempo que se esconde el aviso del sistema. */
    const val HIDE_HOURS = 24L

    private val ignoredCategories = setOf("call", "alarm")

    /**
     * ¿Se quita y se guarda esta notificación? Solo si el usuario lo activó, la app está bloqueada ahora y no es una
     * notificación que el usuario necesita en el momento (llamada, alarma, servicio en curso, resumen de un grupo) ni una de la propia app.
     * [blocked] se pregunta al final, cuando lo demás ya lo permite, porque es lo más caro.
     */
    fun shouldCapture(settings: NotificationSettings, own: Boolean, ongoing: Boolean, groupSummary: Boolean, category: String?, blocked: () -> Boolean): Boolean =
        settings.captureBlocked && !own && !ongoing && !groupSummary && category !in ignoredCategories && blocked()

    /** ¿Es el aviso del sistema de que [appLabel] está en segundo plano o sobre otras apps? Lo publica el propio sistema. */
    fun isBackgroundNotice(settings: NotificationSettings, packageName: String, texts: () -> List<String>, appLabel: String): Boolean =
        settings.hideBackgroundNotice && packageName == "android" && texts().any { it.contains(appLabel, ignoreCase = true) }

    fun clip(text: CharSequence?): String = text?.toString()?.trim().orEmpty().take(MAX_TEXT)
}
