package com.focusguard.decision

/**
 * Lo que el asistente «Preparar navegadores» lee en la página de experimentos de un navegador para activar «Incognito Screenshot»
 * (`#incognito-screenshot`), que deja capturar las pestañas privadas. El servicio de accesibilidad aplana el árbol de la pantalla en
 * [UiText] en orden de lectura y estas reglas dicen dónde tocar; así se prueban sin teléfono.
 */
object BrowserPrep {

    /** El identificador de la opción en la página de experimentos. */
    const val FLAG = "incognito-screenshot"

    /** La dirección que abre la página ya en la opción. Brave y Edge también la aceptan con `chrome://`. */
    const val FLAGS_URL = "chrome://flags/#$FLAG"

    /**
     * Navegadores basados en Chromium que tienen esa opción, en el orden en que se preparan. Samsung Internet no la tiene (`internet://flags` no la
     * muestra) y Firefox no tiene esa página: sus pestañas privadas solo las cubre el bloqueo por captura negra.
     */
    val SUPPORTED: List<String> = listOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
        "com.microsoft.emmx", "com.vivaldi.browser", "com.kiwibrowser.browser", "com.ecosia.android",
    )

    /** Un texto en pantalla: el de la vista o, si no tiene, su descripción. [clickable] dice si ella o algún antecesor se puede tocar. */
    data class UiText(val text: String, val clickable: Boolean)

    enum class Choice { DEFAULT, ENABLED, DISABLED }

    /** Los nombres de cada opción del desplegable: la página sale en inglés, y algunos navegadores la traducen. */
    private val labels: Map<Choice, Set<String>> = mapOf(
        Choice.DEFAULT to setOf("default", "predeterminado", "predeterminada", "por defecto"),
        Choice.ENABLED to setOf("enabled", "habilitado", "habilitada", "activado", "activada"),
        Choice.DISABLED to setOf("disabled", "inhabilitado", "inhabilitada", "deshabilitado", "deshabilitada", "desactivado", "desactivada"),
    )

    private val relaunchLabels = setOf("relaunch", "reiniciar", "volver a iniciar", "reiniciar ahora")

    fun choiceOf(text: String): Choice? {
        val key = text.trim().lowercase()
        return labels.entries.firstOrNull { key in it.value }?.key
    }

    /**
     * El desplegable de la opción: el primer texto con el nombre de una opción que viene **después** del identificador `#incognito-screenshot`.
     * Devuelve su posición en [nodes], o null si la opción aún no está en pantalla.
     */
    fun findSelect(nodes: List<UiText>): Int? {
        val anchor = nodes.indexOfFirst { it.text.contains("#$FLAG", ignoreCase = true) }
        if (anchor < 0) return null
        for (i in anchor + 1 until nodes.size) {
            // Si aparece el identificador de otra opción antes del desplegable, el de esta no se ve. Chrome repite el de esta partido
            // en «#» e «incognito-screenshot»: eso no es otra opción.
            val id = nodes[i].text.trim().removePrefix("#")
            if (nodes[i].text.trim().startsWith("#") && id.isNotEmpty() && !id.equals(FLAG, ignoreCase = true)) return null
            if (choiceOf(nodes[i].text) != null) return i
        }
        return null
    }

    /** La fila «Enabled» de la lista que se abre al tocar el desplegable. */
    fun findOption(nodes: List<UiText>, choice: Choice): Int? = nodes.indexOfFirst { choiceOf(it.text) == choice }.takeIf { it >= 0 }

    /** El botón para reiniciar el navegador que aparece abajo tras cambiar una opción. */
    fun findRelaunch(nodes: List<UiText>): Int? = nodes.indexOfFirst { it.clickable && it.text.trim().lowercase() in relaunchLabels }.takeIf { it >= 0 }

    /** Cómo quedó cada navegador. */
    enum class Result {
        /** Se activó la opción y se reinició el navegador. */
        DONE,

        /** Ya estaba activada. */
        ALREADY,

        /** No se encontró la opción o no respondió a tiempo: hay que hacerlo a mano. */
        FAILED,
    }

    /** Los resultados se guardan como `paquete=RESULTADO;…`. Lo que no se entiende se descarta. */
    fun encode(results: Map<String, Result>): String = results.entries.joinToString(";") { "${it.key}=${it.value.name}" }

    fun decode(text: String?): Map<String, Result> {
        if (text.isNullOrBlank()) return emptyMap()
        return text.split(';').mapNotNull { part ->
            val pkg = part.substringBefore('=', "").trim()
            val result = Result.entries.firstOrNull { it.name == part.substringAfter('=', "").trim() }
            if (pkg.isEmpty() || result == null) null else pkg to result
        }.toMap()
    }
}
