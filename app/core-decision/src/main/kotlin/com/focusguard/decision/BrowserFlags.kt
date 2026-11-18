package com.focusguard.decision

/**
 * Las páginas de experimentos de los navegadores (`chrome://flags`, `brave://flags`, `internet://flags`…). Desde ahí se puede volver a impedir
 * las capturas en las pestañas privadas («Incognito Screenshot»), y con eso la detección de imágenes se queda ciega; por eso se cierran
 * mientras el bloqueo de pestañas privadas esté encendido.
 */
object BrowserFlags {

    /** Esquemas de páginas internas de los navegadores basados en Chromium (y `about:`, que Chrome redirige a `chrome://`). */
    private val schemes = setOf("chrome", "brave", "edge", "opera", "vivaldi", "internet", "kiwi", "about")

    /** [url] es el texto de la barra de direcciones, tal cual. */
    fun isFlagsPage(url: String?): Boolean {
        val text = url?.trim()?.lowercase() ?: return false
        val colon = text.indexOf(':')
        if (colon <= 0 || text.substring(0, colon) !in schemes) return false
        val rest = text.substring(colon + 1).trimStart('/')
        if (!rest.startsWith(PAGE)) return false
        // «flags», «flags/», «flags#incognito-screenshot»; Chrome añade texto para lectores de pantalla («chrome://flags. La conexión no es segura»).
        // Basta que «flags» sea una palabra entera: «flagship» no cuenta.
        if (rest.length == PAGE.length) return true
        val next = rest[PAGE.length]
        return !next.isLetterOrDigit() && next != '-' && next != '_'
    }

    private const val PAGE = "flags"
}
