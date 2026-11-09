package com.focusguard.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerUrlTest {
    @Test fun aceptaHttpsYQuitaLaBarraFinal() {
        assertEquals("https://fg.example.com", ServerUrl.normalize("https://fg.example.com/"))
        assertEquals("https://fg.example.com:8443", ServerUrl.normalize("  HTTPS://FG.example.com:8443  "))
    }

    @Test fun rechazaHttpSinCifrar() {
        assertNull(ServerUrl.normalize("http://fg.example.com"))
        assertNull(ServerUrl.normalize("http://10.0.2.2:3000"))
    }

    @Test fun enDepuracionAceptaHttpSoloLocal() {
        assertEquals("http://10.0.2.2:3000", ServerUrl.normalize("http://10.0.2.2:3000", allowLocalCleartext = true))
        assertEquals("http://localhost:3000", ServerUrl.normalize("http://localhost:3000", allowLocalCleartext = true))
        assertNull(ServerUrl.normalize("http://fg.example.com", allowLocalCleartext = true))
    }

    @Test fun rechazaRutaUsuarioConsultaYBasura() {
        for (bad in listOf("", "   ", "fg.example.com", "https://", "https://user:pw@fg.example.com", "https://fg.example.com/api", "https://fg.example.com/?x=1", "https://fg.example.com/#a", "ftp://fg.example.com", "https://fg example.com")) {
            assertNull(ServerUrl.normalize(bad), bad)
        }
    }
}
