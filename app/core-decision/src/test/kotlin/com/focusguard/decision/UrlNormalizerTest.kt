package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlNormalizerTest {

    @Test
    fun quitaProtocoloWwwYFragmento() {
        assertEquals("example.com/a", UrlNormalizer.normalize("HTTPS://www.Example.com/a/#top"))
    }

    @Test
    fun quitaParametrosDeRastreoPeroConservaLosDemas() {
        assertEquals(
            "example.com/p?id=7",
            UrlNormalizer.normalize("https://example.com/p?utm_source=x&id=7&fbclid=abc"),
        )
        assertEquals("example.com/p", UrlNormalizer.normalize("example.com/p?gclid=1"))
    }

    @Test
    fun aceptaDireccionesSinProtocolo() {
        assertEquals("example.com", UrlNormalizer.normalize("  example.com/  "))
    }

    @Test
    fun hostSinPuertoNiRuta() {
        assertEquals("example.com", UrlNormalizer.host("https://www.example.com:8080/a?b=1"))
    }

    @Test
    fun `quita el aviso de conexion que Chrome pega a la direccion`() {
        assertEquals("chrome://flags", UrlNormalizer.addressText("chrome://flags. La conexión no es segura"))
        assertEquals("sitio.com/ruta", UrlNormalizer.addressText("sitio.com/ruta. La conexión no es segura"))
        assertEquals("sitio.com", UrlNormalizer.addressText("sitio.com"))
        assertEquals("hola. que tal", UrlNormalizer.addressText("hola. que tal"))
        assertEquals("busca esto", UrlNormalizer.addressText("busca esto"))
    }
}
