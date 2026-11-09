package com.focusguard.decision

import com.focusguard.model.ForegroundState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageVigilanceTest {

    private val minute = 60_000L
    private val settings = VigilanceSettings()
    private val social = CaptureTarget("com.red", CaptureRisk.HIGH)
    private val normalApp = CaptureTarget("com.notas", CaptureRisk.NORMAL)
    private val webPage = CaptureTarget("elpais.com", CaptureRisk.LOW, isBrowser = true)

    @Test fun deSerieEstaEncendidaDuraQuinceMinutosEsMediaYSoloDeLoDeRiesgo() {
        assertTrue(settings.enabled)
        assertEquals(15, settings.minutes)
        assertEquals(2, settings.intensity)
        assertEquals(VigilanceScope.RISKY, settings.scope)
        assertFalse(settings.afterSuggestive)
        assertEquals(0.5, settings.intervalFactor, 1e-9)
    }

    @Test fun sinNingunSucesoNoHayVigilancia() {
        val v = ImageVigilance()
        assertFalse(v.active(0))
        assertEquals(1.0, v.factor(0, settings, social), 1e-9)
    }

    @Test fun unSucesoLaActivaDuranteElTiempoElegido() {
        val v = ImageVigilance()
        v.trigger(0, settings)
        assertTrue(v.active(14 * minute))
        assertEquals(1 * minute, v.remainingMs(14 * minute))
        assertFalse(v.active(15 * minute))
        assertEquals(0, v.remainingMs(20 * minute))
    }

    @Test fun otroSucesoRenuevaElEnfriamientoDesdeEseMomento() {
        val v = ImageVigilance()
        v.trigger(0, settings)
        v.trigger(10 * minute, settings)
        assertTrue(v.active(24 * minute))
        assertFalse(v.active(25 * minute))
    }

    @Test fun duranteLaVigilanciaLoDeRiesgoSeCapturaMasSeguido() {
        val v = ImageVigilance()
        v.trigger(0, settings)
        assertEquals(0.5, v.factor(minute, settings, social), 1e-9)
        assertEquals(0.5, v.factor(minute, settings, webPage), 1e-9)
    }

    @Test fun conElAlcanceDeRiesgoUnaAppNormalNoCambia() {
        val v = ImageVigilance()
        v.trigger(0, settings)
        assertEquals(1.0, v.factor(minute, settings, normalApp), 1e-9)
    }

    @Test fun conElAlcanceDeTodoCambiaTodo() {
        val all = settings.copy(scope = VigilanceScope.ALL)
        val v = ImageVigilance()
        v.trigger(0, all)
        assertEquals(0.5, v.factor(minute, all, normalApp), 1e-9)
    }

    @Test fun cadaIntensidadAcortaUnaCantidadDistinta() {
        assertEquals(listOf(0.7, 0.5, 0.33), (1..3).map { settings.copy(intensity = it).intervalFactor })
    }

    @Test fun conLaVigilanciaApagadaNoActuaNiAunqueHayaUnSuceso() {
        val off = settings.copy(enabled = false)
        val v = ImageVigilance()
        v.trigger(0, off)
        assertFalse(v.active(minute))
        // Y si se apaga mientras corría, el factor vuelve a 1 al instante.
        v.trigger(0, settings)
        assertEquals(1.0, v.factor(minute, off, social), 1e-9)
    }

    @Test fun clearLaApaga() {
        val v = ImageVigilance()
        v.trigger(0, settings)
        v.clear()
        assertFalse(v.active(minute))
    }

    @Test fun elTiempoElegidoSeAplicaAlTriggerEnElMomento() {
        val short = settings.copy(minutes = 5)
        val v = ImageVigilance()
        v.trigger(0, short)
        assertTrue(v.active(4 * minute))
        assertFalse(v.active(5 * minute))
    }

    @Test fun elAjusteSeGuardaYSeLeeComoTexto() {
        val custom = VigilanceSettings(enabled = true, minutes = 30, intensity = 3, scope = VigilanceScope.ALL, afterSuggestive = true)
        assertEquals("true,30,3,ALL,true", custom.encode())
        assertEquals(custom, VigilanceSettings.decode(custom.encode()))
        assertEquals(settings, VigilanceSettings.decode(settings.encode()))
    }

    @Test fun unTextoInvalidoNoSeLee() {
        assertNull(VigilanceSettings.decode(""))
        assertNull(VigilanceSettings.decode("true,15,2,RISKY"))
        assertNull(VigilanceSettings.decode("true,7,2,RISKY,false"))
        assertNull(VigilanceSettings.decode("true,15,9,RISKY,false"))
        assertNull(VigilanceSettings.decode("true,15,2,NADA,false"))
        assertNull(VigilanceSettings.decode("si,15,2,RISKY,false"))
    }

    @Test fun losAjustesFueraDeRangoSeRechazan() {
        assertFailsWith<IllegalArgumentException> { VigilanceSettings(minutes = 7) }
        assertFailsWith<IllegalArgumentException> { VigilanceSettings(intensity = 0) }
    }

    @Test fun soloDebilitaLoQueVigilaMenos() {
        val base = VigilanceSettings(minutes = 15, intensity = 2, scope = VigilanceScope.ALL, afterSuggestive = true)
        assertTrue(base.weakens(base.copy(enabled = false)))
        assertTrue(base.weakens(base.copy(minutes = 10)))
        assertTrue(base.weakens(base.copy(intensity = 1)))
        assertTrue(base.weakens(base.copy(scope = VigilanceScope.RISKY)))
        assertTrue(base.weakens(base.copy(afterSuggestive = false)))
    }

    @Test fun vigilarMasNoDebilita() {
        val base = VigilanceSettings(minutes = 15, intensity = 2, scope = VigilanceScope.RISKY, afterSuggestive = false)
        assertFalse(base.weakens(base.copy(minutes = 60)))
        assertFalse(base.weakens(base.copy(intensity = 3)))
        assertFalse(base.weakens(base.copy(scope = VigilanceScope.ALL)))
        assertFalse(base.weakens(base.copy(afterSuggestive = true)))
        assertFalse(base.weakens(base))
    }

    @Test fun conLaVigilanciaYaApagadaNoHayNadaQueDebilitar() {
        val off = VigilanceSettings(enabled = false, minutes = 30)
        assertFalse(off.weakens(off.copy(minutes = 5, intensity = 1)))
        assertFalse(off.weakens(settings))
    }

    @Test fun lasDuracionesOfrecidasSonLasEsperadas() {
        assertEquals(listOf(5, 10, 15, 30, 60), VigilanceSettings.OPTIONS_MINUTES)
    }

    @Test fun elProgramadorAcortaElTiempoSinBajarDelMinimo() {
        val s = CaptureScheduler()
        val safe = ImageAssessment(ImageLevel.SAFE, null, null)
        // Pasados los dos minutos al mínimo y con todo seguro, el tiempo llega a su tope: 102 s en una app normal y 51 s en una de redes.
        var t = 0L
        repeat(200) {
            t += s.intervalMs("a", CaptureRisk.NORMAL, t)
            s.record("a", safe, t)
            s.record("r", safe, t)
        }
        assertEquals(102_000L, s.intervalMs("a", CaptureRisk.NORMAL, t))
        assertEquals(51_000L, s.intervalMs("a", CaptureRisk.NORMAL, t, boost = 0.5))
        // Con la vigilancia alta (×0,33) una app de redes baja de 51 s a unos 16,8 s.
        assertEquals(16_830L, s.intervalMs("r", CaptureRisk.HIGH, t, boost = 0.33))
        // Un factor extremo no acorta más de diez veces el tiempo (51 s pasan a 5,1 s).
        assertEquals(5_100L, s.intervalMs("r", CaptureRisk.HIGH, t, boost = 0.0001))
        // Un factor mayor que 1 no alarga el tiempo.
        assertEquals(102_000L, s.intervalMs("a", CaptureRisk.NORMAL, t, boost = 3.0))
    }

    @Test fun elClasificadorMarcaLosNavegadores() {
        val table = CategoryTable(emptyMap(), emptyMap())
        val page = CaptureRiskClassifier.classify(ForegroundState("com.android.chrome", url = "https://sitio.org", isBrowser = true), table)
        assertTrue(page.isBrowser)
        assertFalse(CaptureRiskClassifier.classify(ForegroundState("com.notas"), table).isBrowser)
    }
}
