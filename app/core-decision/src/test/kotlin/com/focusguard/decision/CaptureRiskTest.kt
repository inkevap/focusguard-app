package com.focusguard.decision

import com.focusguard.model.ForegroundState
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureRiskTest {

    private val table = CategoryTable(
        packages = mapOf(
            "com.instagram.android" to "social",
            "com.google.android.youtube" to "video",
            "com.example.fotos" to "photo",
            "com.example.citas" to "dating",
            "com.example.noticias" to "news",
        ),
        domains = mapOf(
            "instagram.com" to "social",
            "reddit.com" to "social",
            "youtube.com" to "video",
            "elpais.com" to "news",
        ),
    )

    private fun app(pkg: String) = ForegroundState(pkg)
    private fun browser(url: String?, pkg: String = "com.android.chrome") = ForegroundState(pkg, url = url, isBrowser = true)

    @Test fun unaAppDeRedesOVideoSeMiraMasSeguido() {
        assertEquals(CaptureTarget("com.instagram.android", CaptureRisk.HIGH), CaptureRiskClassifier.classify(app("com.instagram.android"), table))
        assertEquals(CaptureRisk.HIGH, CaptureRiskClassifier.classify(app("com.google.android.youtube"), table).risk)
        assertEquals(CaptureRisk.HIGH, CaptureRiskClassifier.classify(app("com.example.fotos"), table).risk)
        assertEquals(CaptureRisk.HIGH, CaptureRiskClassifier.classify(app("com.example.citas"), table).risk)
    }

    @Test fun otraAppOUnaDesconocidaEsNormal() {
        assertEquals(CaptureRisk.NORMAL, CaptureRiskClassifier.classify(app("com.example.noticias"), table).risk)
        assertEquals(CaptureRisk.NORMAL, CaptureRiskClassifier.classify(app("com.desconocida"), table).risk)
    }

    @Test fun enUnNavegadorManda_laWebNoElNavegador() {
        val social = CaptureRiskClassifier.classify(browser("https://m.instagram.com/reels"), table)
        assertEquals(CaptureTarget("m.instagram.com", CaptureRisk.HIGH, isBrowser = true), social)
        val video = CaptureRiskClassifier.classify(browser("https://www.youtube.com/watch?v=1"), table)
        assertEquals(CaptureRisk.HIGH, video.risk)
        assertEquals(CaptureRisk.HIGH, CaptureRiskClassifier.classify(browser("old.reddit.com/r/algo"), table).risk)
    }

    @Test fun cualquierOtraWebEnUnNavegadorSeEspacia() {
        val news = CaptureRiskClassifier.classify(browser("https://elpais.com/internacional"), table)
        assertEquals(CaptureRisk.LOW, news.risk)
        assertEquals("elpais.com", news.key)
        assertEquals(CaptureRisk.LOW, CaptureRiskClassifier.classify(browser("https://sitio-desconocido.org"), table).risk)
    }

    @Test fun sinDireccionLegibleElNavegadorSeEspaciaYLaClaveEsElPaquete() {
        assertEquals(CaptureTarget("com.android.chrome", CaptureRisk.LOW, isBrowser = true), CaptureRiskClassifier.classify(browser(null), table))
        // Una búsqueda a medio escribir no es una dirección.
        assertEquals(CaptureTarget("com.android.chrome", CaptureRisk.LOW, isBrowser = true), CaptureRiskClassifier.classify(browser("gatos graciosos"), table))
    }

    @Test fun cambiarDeWebCambiaLaClaveYAsiLaCuentaDeSegurasEmpiezaDeNuevo() {
        val a = CaptureRiskClassifier.classify(browser("https://elpais.com/a"), table)
        val b = CaptureRiskClassifier.classify(browser("https://otro.org/b"), table)
        assertEquals(false, a.key == b.key)
        // Dos páginas de la misma web comparten clave.
        assertEquals(a.key, CaptureRiskClassifier.classify(browser("https://elpais.com/c"), table).key)
    }

    @Test fun lasCategoriasDeRiesgoSePuedenCambiar() {
        val onlyNews = CaptureRiskClassifier.classify(app("com.example.noticias"), table, highRisk = setOf("news"))
        assertEquals(CaptureRisk.HIGH, onlyNews.risk)
        assertEquals(CaptureRisk.NORMAL, CaptureRiskClassifier.classify(app("com.instagram.android"), table, highRisk = setOf("news")).risk)
    }
}
