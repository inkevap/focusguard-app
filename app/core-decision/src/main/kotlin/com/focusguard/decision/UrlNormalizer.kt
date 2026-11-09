package com.focusguard.decision

/** Normaliza direcciones leídas de la barra del navegador y reglas escritas por el usuario. */
object UrlNormalizer {

    private val trackingParams = Regex("^(utm_.*|fbclid|gclid|msclkid|mc_eid)$")
    private val scheme = Regex("^[a-z][a-z0-9+.-]*://")

    /** Minúsculas, sin protocolo, sin `www.`, sin fragmento ni parámetros de rastreo. */
    fun normalize(raw: String): String {
        var url = raw.trim().lowercase()
        url = url.substringBefore('#')
        url = url.replace(scheme, "")
        url = url.removePrefix("www.")

        val path = url.substringBefore('?')
        val query = url.substringAfter('?', "")
        val kept = query.split('&').filter { it.isNotEmpty() && !trackingParams.matches(it.substringBefore('=')) }
        val result = if (kept.isEmpty()) path else "$path?${kept.joinToString("&")}"
        return result.trimEnd('/')
    }

    /** Como [host], pero solo si lo escrito parece una dirección: la barra también muestra búsquedas a medio escribir. */
    fun hostOrNull(raw: String?): String? =
        raw?.let(::host)?.takeIf { '.' in it && it.none(Char::isWhitespace) && !it.startsWith('.') && !it.endsWith('.') }

    /** Parte del dominio de una URL ya normalizada o sin normalizar. Sin puerto. */
    fun host(raw: String): String =
        normalize(raw).substringBefore('/').substringBefore('?').substringBefore(':')
}
