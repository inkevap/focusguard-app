package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals

class ImageResponderTest {

    private val every = 5 * 60_000L

    private fun explicit(clear: Boolean) = ImageAssessment(
        ImageLevel.EXPLICIT, ImageResult(ImageVerdict.EXPLICIT, if (clear) 0.95 else 0.8, listOf("m"), borderline = !clear), null,
    )
    private val suggestive = ImageAssessment(ImageLevel.SUGGESTIVE, null, ImageResult(ImageVerdict.EXPLICIT, 0.5, listOf("m"), borderline = true))
    private val safe = ImageAssessment(ImageLevel.SAFE, ImageResult(ImageVerdict.SAFE, 0.01, listOf("m")), null)

    @Test fun loExplicitoClaroBloqueaAlMomento() {
        assertEquals(ImageAction.BLOCK, ImageResponder().decide(0, explicit(clear = true), every))
    }

    @Test fun loExplicitoDudosoNecesitaQueLoConfirmenDosCapturas() {
        val r = ImageResponder()
        assertEquals(ImageAction.NONE, r.decide(0, explicit(clear = false), every))
        assertEquals(ImageAction.BLOCK, r.decide(7_000, explicit(clear = false), every))
    }

    @Test fun unaSeguraEntreMedias_noImpideLaConfirmacion() {
        val r = ImageResponder()
        assertEquals(ImageAction.NONE, r.decide(0, explicit(clear = false), every))
        assertEquals(ImageAction.NONE, r.decide(7_000, safe, every))
        assertEquals(ImageAction.BLOCK, r.decide(14_000, explicit(clear = false), every))
    }

    @Test fun unaMarcaDudosaAislada_noBloquea() {
        val r = ImageResponder()
        assertEquals(ImageAction.NONE, r.decide(0, explicit(clear = false), every))
        assertEquals(ImageAction.NONE, r.decide(7_000, safe, every))
        assertEquals(ImageAction.NONE, r.decide(14_000, safe, every))
        assertEquals(ImageAction.NONE, r.decide(21_000, explicit(clear = false), every))
    }

    @Test fun unaMarcaMuyVieja_noSumaALaDeAhora() {
        val r = ImageResponder()
        assertEquals(ImageAction.NONE, r.decide(0, explicit(clear = false), every))
        assertEquals(ImageAction.NONE, r.decide(6 * 60_000L, explicit(clear = false), every))
    }

    @Test fun trasBloquearSeEmpiezaDeCero() {
        val r = ImageResponder()
        r.decide(0, explicit(clear = false), every)
        assertEquals(ImageAction.BLOCK, r.decide(7_000, explicit(clear = false), every))
        // La cuenta de marcas se vació: otra dudosa suelta no vuelve a bloquear.
        assertEquals(ImageAction.NONE, r.decide(14_000, explicit(clear = false), every))
    }

    @Test fun loSugerenteOfreceBloquearPeroNoBloquea() {
        assertEquals(ImageAction.OFFER, ImageResponder().decide(0, suggestive, every))
    }

    @Test fun dentroDelPlazoNoSeOfreceOtraVez() {
        val r = ImageResponder()
        assertEquals(ImageAction.OFFER, r.decide(0, suggestive, every))
        assertEquals(ImageAction.NONE, r.decide(60_000, suggestive, every))
        assertEquals(ImageAction.NONE, r.decide(every - 1, suggestive, every))
    }

    @Test fun pasadoElPlazoSeOfreceDeNuevo() {
        val r = ImageResponder()
        assertEquals(ImageAction.OFFER, r.decide(0, suggestive, every))
        assertEquals(ImageAction.OFFER, r.decide(every, suggestive, every))
        // El plazo cuenta desde el último ofrecimiento, no desde el primero.
        assertEquals(ImageAction.NONE, r.decide(every + 60_000, suggestive, every))
    }

    @Test fun elPlazoSeCambiaEnAjustes() {
        val r = ImageResponder()
        assertEquals(ImageAction.OFFER, r.decide(0, suggestive, ImageAlerts.toMs(1)))
        assertEquals(ImageAction.NONE, r.decide(30_000, suggestive, ImageAlerts.toMs(1)))
        assertEquals(ImageAction.OFFER, r.decide(60_000, suggestive, ImageAlerts.toMs(1)))
        val r30 = ImageResponder()
        r30.decide(0, suggestive, ImageAlerts.toMs(30))
        assertEquals(ImageAction.NONE, r30.decide(29 * 60_000L, suggestive, ImageAlerts.toMs(30)))
    }

    @Test fun loSeguroNoHaceNada() {
        assertEquals(ImageAction.NONE, ImageResponder().decide(0, safe, every))
    }

    @Test fun unaCapturaSinPuntuarNoCambiaNada() {
        val r = ImageResponder()
        assertEquals(ImageAction.OFFER, r.decide(0, suggestive, every))
        assertEquals(ImageAction.NONE, r.decide(1_000, null, every))
        assertEquals(ImageAction.NONE, r.decide(2_000, suggestive, every))
    }

    @Test fun elOfrecimientoNoDependeDeLoExplicito() {
        val r = ImageResponder()
        assertEquals(ImageAction.BLOCK, r.decide(0, explicit(clear = true), every))
        // Bloquear no gasta el plazo de las notificaciones.
        assertEquals(ImageAction.OFFER, r.decide(1_000, suggestive, every))
    }

    @Test fun lasOpcionesDeMinutosSonLasOfrecidasYLasDemasSeAjustan() {
        assertEquals(listOf(1, 2, 5, 10, 15, 30), ImageAlerts.OPTIONS)
        assertEquals(5, ImageAlerts.DEFAULT_MINUTES)
        assertEquals(5, ImageAlerts.clamp(5))
        assertEquals(5, ImageAlerts.clamp(7))
        assertEquals(10, ImageAlerts.clamp(8))
        assertEquals(1, ImageAlerts.clamp(0))
        assertEquals(30, ImageAlerts.clamp(100))
        assertEquals(300_000L, ImageAlerts.toMs(5))
    }
}
