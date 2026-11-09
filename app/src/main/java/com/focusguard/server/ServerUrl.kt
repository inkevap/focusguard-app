package com.focusguard.server

import java.net.URI

object ServerUrl {
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /**
     * Dirección base del servidor, sin barra final: solo `https` (y `http` a la propia máquina o al emulador cuando
     * [allowLocalCleartext], para desarrollar), sin usuario, ruta, consulta ni fragmento. `null` si no sirve.
     */
    fun normalize(raw: String, allowLocalCleartext: Boolean = false): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val secure = uri.scheme.equals("https", ignoreCase = true)
        val localHttp = allowLocalCleartext && uri.scheme.equals("http", ignoreCase = true) && host in LOCAL_HOSTS
        if (!secure && !localHttp) return null
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        if (uri.path.orEmpty().isNotEmpty() && uri.path != "/") return null
        val port = if (uri.port >= 0) ":${uri.port}" else ""
        return "${uri.scheme.lowercase()}://$host$port"
    }
}
