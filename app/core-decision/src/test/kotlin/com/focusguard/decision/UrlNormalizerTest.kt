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
}
