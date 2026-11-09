package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdultContentTest {
    private val filter = AdultFilter(
        domains = listOf("xvideos.com", "bad.example.org"),
        words = listOf("porno", "hentai", "sexo"),
        exceptions = listOf("wikipedia.org", "ejemplo.es"),
    )

    @Test fun elDominioCoincideConYSinWww() {
        assertEquals(AdultMatch.Kind.DOMAIN, filter.checkUrl("https://www.xvideos.com/video123")?.kind)
        assertEquals(AdultMatch.Kind.DOMAIN, filter.checkUrl("xvideos.com")?.kind)
    }

    @Test fun unSubdominioCoincideConSuDominioPrincipal() {
        assertEquals(AdultMatch.Kind.DOMAIN, filter.checkUrl("https://es.m.xvideos.com/x")?.kind)
    }

    @Test fun elDominioPrincipalNoSeBloqueaPorUnSubdominioDeLaLista() {
        assertNull(filter.checkUrl("https://example.org/inicio"))
        assertNull(filter.checkUrl("https://good.example.org/inicio"))
        assertEquals(AdultMatch.Kind.DOMAIN, filter.checkUrl("https://www.bad.example.org/")?.kind)
    }

    @Test fun lasPestanasNuevasSeIgnoran() {
        assertNull(filter.checkUrl("about:blank"))
        assertNull(filter.checkUrl("chrome://newtab"))
        assertNull(filter.checkUrl(""))
    }

    @Test fun unaPaginaDeDatosNoEsUnaPestanaNueva() {
        assertEquals(AdultMatch.Kind.WORD, filter.checkUrl("data:text/html,hentai gratis")?.kind)
    }

    @Test fun unDominioPermitidoNoDejaPasarCualquierTextoQueLoNombre() {
        val allowing = AdultFilter(listOf("xvideos.com"), listOf("sexo"), allowed = listOf("wikipedia.org"))
        assertNull(allowing.checkUrl("https://es.wikipedia.org/wiki/Sexo"))
        assertEquals(AdultMatch.Kind.DOMAIN, allowing.checkUrl("https://xvideos.com/?ref=wikipedia.org")?.kind)
        assertEquals(AdultMatch.Kind.WORD, allowing.checkUrl("https://otro.net/sexo?ref=wikipedia.org")?.kind)
    }

    @Test fun lasEntradasPermitidasMuyCortasNoCuentan() {
        val allowing = AdultFilter(listOf("xvideos.com"), listOf("sexo"), allowed = listOf("a.b", "é"))
        assertEquals(AdultMatch.Kind.DOMAIN, allowing.checkUrl("https://xvideos.com/a.b")?.kind)
    }

    @Test fun unaWebConExcepcionNoSeDiceQueYaSeBloquea() {
        val withException = AdultFilter(listOf("example.com"), emptyList(), exceptions = listOf("news.example.com"))
        assertEquals(UserWebValidator.Result.Valid("news.example.com"), UserWebValidator.validate("news.example.com", "es", withException))
    }

    @Test fun laReglaFijaDePornPillaLasVariantes() {
        assertEquals(AdultMatch.Kind.PORN_RULE, filter.checkUrl("https://sitio-p.o.r.n-gratis.net/")?.kind)
        assertEquals(AdultMatch.Kind.PORN_RULE, filter.checkUrl("buscar?q=PORN+videos")?.kind)
    }

    @Test fun laPalabraDeLaListaBloqueaEnLaBarra() {
        val match = filter.checkUrl("https://buscador.net/?q=hentai%20gratis")
        assertEquals(AdultMatch.Kind.WORD, match?.kind)
        assertTrue("hentai" in match!!.matched)
    }

    @Test fun losAcentosYMayusculasNoEvitanLaPalabra() {
        assertEquals(AdultMatch.Kind.WORD, filter.checkUrl("https://x.net/?q=SEXO")?.kind)
    }

    @Test fun unaExcepcionNuncaSeBloqueaPorPalabras() {
        assertNull(filter.checkUrl("https://es.wikipedia.org/wiki/Pornografia"))
        assertNull(filter.checkUrl("https://ejemplo.es/hentai"))
    }

    @Test fun loPermitidoPorElUsuarioPasa() {
        val allowing = AdultFilter(listOf("xvideos.com"), listOf("sexo"), allowed = listOf("educacion sexo seguro"))
        assertNull(allowing.checkScreenText(listOf("Educación sexo seguro en clase", "sexo y mas sexo")))
        assertNull(allowing.checkUrl("https://escuela.net/educacion-sexo-seguro"))
    }

    @Test fun elTextoDePantallaPideDosPalabrasDistintas() {
        assertNull(filter.checkScreenText(listOf("un articulo sobre el sexo en la historia")))
        assertNull(filter.checkScreenText(listOf("sexo", "sexo otra vez sexo")))
        val match = filter.checkScreenText(listOf("hentai", "mas contenido de sexo"))
        assertIs<AdultMatch>(match)
        assertEquals(listOf("hentai", "sexo"), match.matched)
    }

    @Test fun unaAppSinLecturaDeTextoNoSeBloqueaPorLoQueDice_peroSuDireccionSiSeMira() {
        val gate = AdultGate()
        // Una pantalla con dos palabras distintas de la lista bloquea...
        assertIs<AdultMatch>(gate.check("com.app", null, true, false, 0, true, filter) { screen })
        // ...salvo en una app cuyo texto no se lee.
        assertNull(AdultGate().check("md.obsidian", null, true, false, 0, true, filter, skipText = true) { screen })
        // La dirección de una web se mira aunque el texto no.
        assertIs<AdultMatch>(AdultGate().check("com.app", "https://xvideos.com/x", true, false, 0, true, filter, skipText = true) { screen })
    }

    @Test fun unaPalabraDentroDeOtraDeLaListaNoCuentaPorDos() {
        val nested = AdultFilter(emptyList(), listOf("porn", "porno", "hentai"))
        // «porno» contiene «porn»: es una sola mención, y una sola no bloquea.
        assertNull(nested.checkScreenText(listOf("una nota que dice porno una vez")))
        // Dos palabras que no se contienen siguen bloqueando.
        val match = nested.checkScreenText(listOf("una nota con porno", "y con hentai"))
        assertIs<AdultMatch>(match)
        assertEquals(listOf("porno", "hentai"), match.matched)
    }

    @Test fun lasPalabrasCortasDeLaListaNoCuentan() {
        val short = AdultFilter(emptyList(), listOf("sex", "ass"))
        assertNull(short.checkUrl("https://passenger.net/class"))
    }

    @Test fun laListaDelUsuarioTambienBuscaSinEspacios() {
        val user = AdultFilter(emptyList(), emptyList(), userWords = listOf("mala palabra"))
        assertEquals(AdultMatch.Kind.WORD, user.checkUrl("https://x.net/?q=malapalabra")?.kind)
    }

    @Test fun normalizarConservaElMasDe18() {
        assertEquals("solo 18+ aqui", AdultContent.normalize("Solo 18+ aquí!!").spaced)
        assertEquals("solo18+aqui", AdultContent.normalize("Solo 18+ aquí!!").joined)
    }

    @Test fun laFraseDeConfirmacionSeCompruebaSinAcentosNiMayusculas() {
        assertTrue(AllowPhrase.matches("Confirmo que este elemento no es pornografico"))
        assertTrue(AllowPhrase.matches("  confirmo   que este elemento no es pornográfico "))
        assertFalse(AllowPhrase.matches("confirmo que este elemento no es porno"))
    }

    @Test fun validarUnaWebDelUsuario() {
        assertEquals(UserWebValidator.Result.Valid("ejemplo.com"), UserWebValidator.validate(" https://www.Ejemplo..com ", "es", filter))
        assertEquals(UserWebValidator.Result.NoDot, UserWebValidator.validate("ejemplo", "es", filter))
        assertEquals(UserWebValidator.Result.TooShort, UserWebValidator.validate("a.", "es", filter))
        assertEquals(UserWebValidator.Result.AlreadyBlocked, UserWebValidator.validate("xvideos.com", "es", filter))
        assertEquals(UserWebValidator.Result.AlreadyBlocked, UserWebValidator.validate("mi-hentai.net", "es", filter))
    }

    @Test fun enCoreanoLaLongitudMinimaEsDos() {
        assertEquals(UserWebValidator.Result.TooShort, UserWebValidator.validate("a.", "es", filter))
        assertEquals(UserWebValidator.Result.Valid("a.b"), UserWebValidator.validate("a.b", "ko", filter))
    }

    @Test fun laReactivacionAutomatica() {
        val off = 1_000_000L
        val at = AutoReactivation.reactivateAt(off)
        assertEquals(off + 24 * 3_600_000L, at)
        assertFalse(AutoReactivation.due(at, at - 1))
        assertTrue(AutoReactivation.due(at, at))
        assertFalse(AutoReactivation.due(null, at))
    }

    private val screen = listOf("hentai", "mas contenido de sexo")

    private fun gate(scan: Boolean, nowMs: Long, gate: AdultGate, texts: () -> Collection<String>? = { screen }, url: String? = null, enabled: Boolean = true) =
        gate.check("com.app", url, enabled, exempt = false, nowMs = nowMs, scan = scan, filter = filter, screenTexts = texts)

    @Test fun laCompuertaMiraLaPantallaSoloAlPedirlo() {
        val gate = AdultGate()
        assertNull(gate(scan = false, nowMs = 0, gate = gate))
        assertEquals(AdultMatch.Kind.WORD, gate(scan = true, nowMs = 0, gate = gate)?.kind)
    }

    @Test fun laCompuertaNoRecorreLaPantallaDosVecesSeguidas() {
        val gate = AdultGate()
        var reads = 0
        val texts = { reads++; listOf("una pagina normal") }
        gate(scan = true, nowMs = 0, gate = gate, texts = texts)
        gate(scan = true, nowMs = 1_000, gate = gate, texts = texts)
        assertEquals(1, reads)
        gate(scan = true, nowMs = 3_000, gate = gate, texts = texts)
        assertEquals(2, reads)
    }

    @Test fun unBloqueoNoSeRecuerdaParaLaPaginaSiguiente() {
        val gate = AdultGate()
        assertEquals(AdultMatch.Kind.WORD, gate(scan = true, nowMs = 0, gate = gate)?.kind)
        assertNull(gate(scan = false, nowMs = 1_000, gate = gate))
        assertNull(gate(scan = true, nowMs = 1_000, gate = gate, texts = { listOf("ya es otra pagina") }))
    }

    @Test fun unaVentanaQueNoSePudoLeerNoSeRecuerdaComoLimpia() {
        val gate = AdultGate()
        assertNull(gate(scan = true, nowMs = 0, gate = gate, texts = { null }))
        assertEquals(AdultMatch.Kind.WORD, gate(scan = true, nowMs = 100, gate = gate)?.kind)
    }

    @Test fun laCompuertaApagadaOExentaNoMiraNada() {
        val gate = AdultGate()
        assertNull(gate(scan = true, nowMs = 0, gate = gate, enabled = false))
        assertNull(gate.check("com.app", "xvideos.com", true, exempt = true, nowMs = 0, scan = true, filter = filter) { screen })
    }

    @Test fun laDireccionSeMiraSiempreSinLeerLaPantalla() {
        val gate = AdultGate()
        var reads = 0
        val match = gate(scan = false, nowMs = 0, gate = gate, texts = { reads++; null }, url = "https://xvideos.com")
        assertEquals(AdultMatch.Kind.DOMAIN, match?.kind)
        assertEquals(0, reads)
    }

    @Test fun unaPalabraPermitidaNoLibraUnDominioDeLaLista() {
        val allowing = AdultFilter(listOf("pornhub.com"), listOf("sexo"), allowed = listOf("pornhub", "sexo seguro"))
        assertEquals(AdultMatch.Kind.DOMAIN, allowing.checkUrl("https://pornhub.com/")?.kind)
    }

    @Test fun lasPalabrasPermitidasCuentanEnterasYNoAtravesDeEspacios() {
        val allowing = AdultFilter(emptyList(), listOf("sexo"), allowed = listOf("sexo seguro"))
        assertNull(allowing.checkUrl("https://escuela.net/sexo-seguro"))
        assertEquals(AdultMatch.Kind.WORD, allowing.checkUrl("https://escuela.net/sexo-inseguro")?.kind)
    }
}
