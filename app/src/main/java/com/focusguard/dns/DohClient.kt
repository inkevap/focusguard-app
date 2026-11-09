package com.focusguard.dns

import com.focusguard.server.HttpRequest
import com.focusguard.server.HttpTransport
import com.focusguard.server.NetworkException

/** DNS sobre HTTPS (RFC 8484): la consulta tal cual va en el cuerpo de un POST y la respuesta vuelve igual. */
class DohClient(private val transport: HttpTransport, private val timeoutMs: Int = 4_000) {

    /** La respuesta del resolvedor, o `null` si no contestó bien (red, estado distinto de 200, o una respuesta que no es de esta consulta). */
    suspend fun resolve(url: String, query: ByteArray): ByteArray? {
        val response = try {
            transport.send(
                HttpRequest(
                    "POST", url,
                    headers = mapOf("content-type" to "application/dns-message", "accept" to "application/dns-message"),
                    body = query, timeoutMs = timeoutMs, maxBodyBytes = MAX_DNS_MESSAGE,
                ),
            )
        } catch (e: NetworkException) {
            return null
        }
        if (response.status != 200 || !DnsMessages.answers(query, response.body)) return null
        return response.body
    }

    private companion object {
        /** Una respuesta DNS no pasa de 64 KB; más es un servidor roto. */
        const val MAX_DNS_MESSAGE = 65_535
    }
}
