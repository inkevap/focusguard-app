package com.focusguard.server

/** Transporte de pruebas: responde con lo que diga el guion y recuerda las peticiones. */
class FakeTransport(private val script: (HttpRequest) -> HttpResponse?) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()

    override suspend fun send(request: HttpRequest): HttpResponse {
        requests += request
        return script(request) ?: throw NetworkException("sin conexión")
    }

    fun paths() = requests.map { it.url.substringAfter("//").substringAfter('/').let { p -> "/$p" } }
}

fun json(status: Int, body: String) = HttpResponse(status, emptyMap(), body.toByteArray())

fun HttpRequest.bodyText() = body?.toString(Charsets.UTF_8).orEmpty()

fun HttpRequest.bearer(): String? = headers["authorization"]
