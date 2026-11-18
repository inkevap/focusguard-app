package com.focusguard.decision

/** Normaliza direcciones leídas de la barra del navegador y reglas escritas por el usuario. */
object UrlNormalizer {

    private val trackingParams = Regex("^(utm_.*|fbclid|gclid|msclkid|mc_eid)$")
    private val scheme = Regex("^[a-z][a-z0-9+.-]*://")

    /** Una dirección sin espacios, un punto y un espacio, y luego texto: el aviso que Chrome añade para los lectores de pantalla. */
    private val trailingNote = Regex("""^(\S+)\.\s+\S.*$""", RegexOption.DOT_MATCHES_ALL)

    /**
     * El texto de la barra de direcciones sin el aviso que algunos navegadores le pegan: Chrome lee «chrome://flags. La conexión no es segura»
     * o «sitio.com. La conexión no es segura» en las páginas sin cifrar. Una búsqueda a medio escribir («hola. que tal») se queda igual si su primera
     * palabra no parece una dirección (con «:» o con un punto en medio).
     */
    fun addressText(raw: String): String {
        val match = trailingNote.matchEntire(raw.trim()) ?: return raw
        val address = match.groupValues[1]
        return if (':' in address || '.' in address) address else raw
    }

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
