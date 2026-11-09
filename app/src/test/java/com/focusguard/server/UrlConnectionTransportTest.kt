package com.focusguard.server

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** El transporte real contra un servidor mínimo hecho con sockets (la JVM de pruebas de Android no trae `HttpServer`). */
class UrlConnectionTransportTest {
    private class Received(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    private val base get() = "http://127.0.0.1:${socket.localPort}"

    private val worker = thread(isDaemon = true) {
        while (!socket.isClosed) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            runCatching { handle(client) }
            runCatching { client.close() }
        }
    }

    private fun handle(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val (method, path) = reader.readLine().split(" ")
        val headers = generateSequence { reader.readLine() }.takeWhile { it.isNotEmpty() }.associate { line -> line.substringBefore(':').lowercase() to line.substringAfter(':').trim() }
        val length = headers["content-length"]?.toInt() ?: 0
        val body = CharArray(length).also { reader.read(it, 0, length) }.concatToString()
        val request = Received(method, path, headers, body)
        val (status, extraHeaders, payload) = when (path) {
            "/eco" -> Triple(200, "X-Prueba: valor\r\n", """{"method":"${request.method}","auth":"${request.headers["authorization"]}","body":"${request.body}"}""")
            "/error" -> Triple(409, "", """{"error":"mal"}""")
            "/redirige" -> Triple(302, "Location: http://127.0.0.1:1/otra\r\n", "")
            "/grande" -> Triple(200, "", "A".repeat(2000))
            else -> Triple(404, "", "")
        }
        val bytes = payload.toByteArray()
        client.getOutputStream().apply {
            write("HTTP/1.1 $status X\r\nContent-Length: ${bytes.size}\r\n${extraHeaders}Connection: close\r\n\r\n".toByteArray())
            write(bytes)
            flush()
        }
    }

    @AfterTest fun stop() {
        socket.close()
        worker.join(1000)
    }

    @Test fun mandaMetodoCabecerasYCuerpoYLeeLaRespuesta() = runBlocking {
        val response = UrlConnectionTransport().send(HttpRequest("PUT", "$base/eco", mapOf("authorization" to "Bearer x"), "hola".toByteArray()))
        assertEquals(200, response.status)
        assertTrue(response.text.contains(""""method":"PUT"""") && response.text.contains(""""auth":"Bearer x"""") && response.text.contains(""""body":"hola""""), response.text)
        assertEquals("valor", response.headers["x-prueba"])
    }

    @Test fun leeElCuerpoDeLosErrores() = runBlocking {
        val response = UrlConnectionTransport().send(HttpRequest("GET", "$base/error"))
        assertEquals(409, response.status)
        assertEquals("""{"error":"mal"}""", response.text)
    }

    @Test fun noSigueRedirecciones() = runBlocking {
        assertEquals(302, UrlConnectionTransport().send(HttpRequest("GET", "$base/redirige")).status)
    }

    @Test fun cortaRespuestasMasGrandesQueElLimite() {
        assertFailsWith<NetworkException> { runBlocking { UrlConnectionTransport(maxBody = 1000).send(HttpRequest("GET", "$base/grande")) } }
    }

    @Test fun sinServidorLanzaNetworkException() {
        assertFailsWith<NetworkException> { runBlocking { UrlConnectionTransport().send(HttpRequest("GET", "http://127.0.0.1:1/x")) } }
    }
}
