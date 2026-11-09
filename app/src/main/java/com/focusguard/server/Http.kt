package com.focusguard.server

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    /** Tiempo máximo de conexión y de lectura; sin valor, los de siempre (15 y 30 s). */
    val timeoutMs: Int? = null,
    /** Tope de la respuesta para esta petición; sin valor, el del transporte. */
    val maxBodyBytes: Int? = null,
)

class HttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray) {
    val text: String get() = body.toString(Charsets.UTF_8)

    /** El cuerpo como objeto JSON, o `null` si no lo es (una página de error de un proxy, por ejemplo). */
    fun json(): JSONObject? = runCatching { JSONObject(text) }.getOrNull()
}

/** No hubo respuesta (sin conexión, tiempo agotado, certificado no válido…). */
class NetworkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Lo único que la app necesita de la red; así la lógica del cliente se prueba sin red. */
interface HttpTransport {
    suspend fun send(request: HttpRequest): HttpResponse
}

/**
 * Transporte con `HttpURLConnection`: sin dependencias nuevas. No sigue redirecciones (una redirección a `http` quitaría el cifrado)
 * y no lee más de [maxBody] bytes.
 */
class UrlConnectionTransport(private val maxBody: Int = 8 * 1024 * 1024) : HttpTransport {
    override suspend fun send(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = try {
            URL(request.url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw NetworkException("Dirección no válida", e)
        }
        var completed = false
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = request.timeoutMs ?: 15_000
            connection.readTimeout = request.timeoutMs ?: 30_000
            connection.instanceFollowRedirects = false
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            request.body?.let {
                connection.doOutput = true
                connection.outputStream.use { out -> out.write(it) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { readLimited(it, request.maxBodyBytes ?: maxBody) } ?: ByteArray(0)
            val headers = connection.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.firstOrNull().orEmpty() }
            completed = true
            HttpResponse(status, headers, body)
        } catch (e: IOException) {
            throw NetworkException(e.message ?: "Sin conexión", e)
        } catch (e: IllegalArgumentException) {
            // Una cabecera o una dirección que `HttpURLConnection` rechaza: es un fallo de la petición, no de la app.
            throw NetworkException("Petición no válida", e)
        } catch (e: SecurityException) {
            throw NetworkException("Sin permiso de red", e)
        } finally {
            // Con la respuesta leída hasta el final y cerrada, la conexión vuelve al grupo y la siguiente petición al mismo servidor se ahorra el TLS
            // (el filtro DNS hace muchas). Solo se corta si algo salió mal.
            if (!completed) connection.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (out.size() + n > max) throw IOException("La respuesta es demasiado grande")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
