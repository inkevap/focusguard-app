package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageAdaptationTest {

    private val hour = 3_600_000L
    private val settings = AdaptiveSettings()

    private fun level(incidents: List<Long>, now: Long, s: AdaptiveSettings = settings) = ImageAdaptation.level(s, incidents, now)

    @Test fun deSerieEstaEncendidoCuentaSeisHorasYSubeHastaElNivelDos() {
        assertTrue(settings.enabled)
        assertEquals(6, settings.windowHours)
        assertEquals(2, settings.maxLevel)
        assertEquals(6 * hour, settings.windowMs)
    }

    @Test fun sinIncidentesElNivelEsCero() {
        assertEquals(0, level(emptyList(), 100 * hour))
    }

    @Test fun losNivelesSuben_conLasVecesQueSeRepite() {
        val all = AdaptiveSettings(maxLevel = 3)
        val now = 10 * hour
        fun n(count: Int) = List(count) { now - it * 1000L }
        assertEquals(0, level(n(0), now, all))
        assertEquals(1, level(n(1), now, all))
        assertEquals(1, level(n(2), now, all))
        assertEquals(2, level(n(3), now, all))
        assertEquals(2, level(n(5), now, all))
        assertEquals(3, level(n(6), now, all))
        assertEquals(3, level(n(50), now, all))
    }

    @Test fun elNivelNoPasaDelMaximoElegido() {
        val now = 10 * hour
        val many = List(10) { now - it * 1000L }
        assertEquals(1, level(many, now, AdaptiveSettings(maxLevel = 1)))
        assertEquals(2, level(many, now, AdaptiveSettings(maxLevel = 2)))
        assertEquals(3, level(many, now, AdaptiveSettings(maxLevel = 3)))
    }

    @Test fun conElTiempoLosIncidentesViejosDejanDeContarYElNivelBaja() {
        val incidents = listOf(0L, 1 * hour, 2 * hour) // tres incidentes seguidos
        val oneDay = AdaptiveSettings(windowHours = 24)
        assertEquals(2, level(incidents, 3 * hour, oneDay))
        // A las 24 h y 30 min el primero ya salió del periodo: quedan dos, nivel 1.
        assertEquals(1, level(incidents, 24 * hour + 30 * 60_000L, oneDay))
        // A las 26 h y 1 min ya salieron los tres.
        assertEquals(0, level(incidents, 26 * hour + 60_000L, oneDay))
    }

    @Test fun elPeriodoElegidoCambiaQueCuenta() {
        val incidents = listOf(0L, 10 * hour, 20 * hour)
        val now = 30 * hour
        assertEquals(0, level(incidents, now, AdaptiveSettings(windowHours = 6)))
        assertEquals(1, level(incidents, now, AdaptiveSettings(windowHours = 24)))
        assertEquals(2, level(incidents, now, AdaptiveSettings(windowHours = 72)))
    }

    @Test fun unIncidenteJustoEnElLimiteDelPeriodoTodaviaCuenta() {
        val oneDay = AdaptiveSettings(windowHours = 24)
        assertEquals(1, level(listOf(0L), 24 * hour, oneDay))
        assertEquals(0, level(listOf(0L), 24 * hour + 1, oneDay))
    }

    @Test fun conElModoApagadoNoHayNivelAunqueHayaIncidentes() {
        assertEquals(0, level(List(10) { 10 * hour - it }, 10 * hour, AdaptiveSettings(enabled = false)))
    }

    @Test fun unIncidenteDeUnaHoraFuturaNoCuenta() {
        assertEquals(0, level(listOf(100 * hour), 10 * hour))
    }

    @Test fun cadaNivelAcortaElTiempoUnVeintePorCiento() {
        assertEquals(1.0, ImageAdaptation.intervalFactor(0), 1e-9)
        assertEquals(0.8, ImageAdaptation.intervalFactor(1), 1e-9)
        assertEquals(0.64, ImageAdaptation.intervalFactor(2), 1e-9)
        assertEquals(0.512, ImageAdaptation.intervalFactor(3), 1e-9)
        assertEquals(0.512, ImageAdaptation.intervalFactor(9), 1e-9)
        assertEquals(1.0, ImageAdaptation.intervalFactor(-1), 1e-9)
    }

    @Test fun laSensibilidadSubeUnPasoPorNivelHastaLaMaxima() {
        assertEquals(3, ImageAdaptation.sensitivity(3, 0))
        assertEquals(4, ImageAdaptation.sensitivity(3, 1))
        assertEquals(5, ImageAdaptation.sensitivity(3, 2))
        assertEquals(5, ImageAdaptation.sensitivity(3, 3))
        assertEquals(5, ImageAdaptation.sensitivity(5, 2))
    }

    @Test fun laSensibilidadNuncaBajaDeLaElegida() {
        for (base in 1..5) for (level in 0..3) assertTrue(ImageAdaptation.sensitivity(base, level) >= base)
        assertEquals(1, ImageAdaptation.sensitivity(1, -3))
    }

    @Test fun laVigilanciaDuraMasConElNivelPeroConTope() {
        assertEquals(15, ImageAdaptation.vigilanceMinutes(15, 0))
        assertEquals(22, ImageAdaptation.vigilanceMinutes(15, 1))
        assertEquals(30, ImageAdaptation.vigilanceMinutes(15, 2))
        assertEquals(37, ImageAdaptation.vigilanceMinutes(15, 3))
        assertEquals(120, ImageAdaptation.vigilanceMinutes(60, 3))
    }

    @Test fun elAjusteSeGuardaYSeLeeComoTexto() {
        val custom = AdaptiveSettings(enabled = true, windowHours = 168, maxLevel = 3)
        assertEquals("true,168,3", custom.encode())
        assertEquals(custom, AdaptiveSettings.decode(custom.encode()))
        assertEquals(settings, AdaptiveSettings.decode(settings.encode()))
    }

    @Test fun unTextoInvalidoNoSeLee() {
        assertNull(AdaptiveSettings.decode(""))
        assertNull(AdaptiveSettings.decode("true,24"))
        assertNull(AdaptiveSettings.decode("true,7,2"))
        assertNull(AdaptiveSettings.decode("true,24,9"))
        assertNull(AdaptiveSettings.decode("si,24,2"))
    }

    @Test fun losAjustesFueraDeRangoSeRechazan() {
        assertFailsWith<IllegalArgumentException> { AdaptiveSettings(windowHours = 5) }
        assertFailsWith<IllegalArgumentException> { AdaptiveSettings(maxLevel = 0) }
        assertFailsWith<IllegalArgumentException> { AdaptiveSettings(maxLevel = 4) }
    }

    @Test fun soloDebilitaLoQueAdaptaMenos() {
        val base = AdaptiveSettings(windowHours = 72, maxLevel = 3)
        assertTrue(base.weakens(base.copy(enabled = false)))
        assertTrue(base.weakens(base.copy(windowHours = 24)))
        assertTrue(base.weakens(base.copy(maxLevel = 2)))
        assertFalse(base.weakens(base.copy(windowHours = 168)))
        assertFalse(AdaptiveSettings(windowHours = 24, maxLevel = 2).weakens(AdaptiveSettings(windowHours = 72, maxLevel = 3)))
        assertFalse(base.weakens(base))
    }

    @Test fun conElModoYaApagadoNoHayNadaQueDebilitar() {
        val off = AdaptiveSettings(enabled = false, windowHours = 168)
        assertFalse(off.weakens(off.copy(windowHours = 6, maxLevel = 1)))
    }

    @Test fun elHistorialAnadeElIncidenteYLoGuardaComoTexto() {
        val once = ImageIncidents.add(emptyList(), 1_000L)
        assertEquals(listOf(1_000L), once)
        val twice = ImageIncidents.add(once, 2_000L)
        assertEquals(listOf(1_000L, 2_000L), twice)
        assertEquals("1000,2000", ImageIncidents.encode(twice))
        assertEquals(twice, ImageIncidents.decode("1000,2000"))
    }

    @Test fun elHistorialDescartaLoQueNingunPeriodoPuedeContar() {
        val old = 0L
        val week = 168 * hour
        val list = ImageIncidents.add(listOf(old), week + 1)
        assertEquals(listOf(week + 1), list)
        // Justo en el límite de la semana todavía se guarda.
        assertEquals(listOf(old, week), ImageIncidents.add(listOf(old), week))
    }

    @Test fun elHistorialTieneUnTope() {
        var list = emptyList<Long>()
        repeat(300) { list = ImageIncidents.add(list, it * 1000L) }
        assertEquals(200, list.size)
        assertEquals(299_000L, list.last())
    }

    @Test fun unTextoDeHistorialConBasuraSeLimpia() {
        assertEquals(listOf(1L, 3L), ImageIncidents.decode("1,hola,,3"))
        assertEquals(emptyList(), ImageIncidents.decode(""))
    }

    @Test fun laVigilanciaPuedeDurarLoQueElNivelPida() {
        val v = ImageVigilance()
        v.trigger(0, VigilanceSettings(), minutes = 22)
        assertTrue(v.active(21 * 60_000L))
        assertFalse(v.active(22 * 60_000L))
    }

    @Test fun laVigilanciaSePuedeRestaurarTrasUnReinicio() {
        val v = ImageVigilance()
        v.restore(untilMs = 5 * 60_000L)
        assertTrue(v.active(4 * 60_000L))
        assertFalse(v.active(5 * 60_000L))
        assertEquals(5 * 60_000L, v.untilMs())
    }

    @Test fun elAlcanceSeConsultaSinNecesitarVigilanciaActiva() {
        val risky = VigilanceSettings()
        val all = VigilanceSettings(scope = VigilanceScope.ALL)
        val social = CaptureTarget("a", CaptureRisk.HIGH)
        val notes = CaptureTarget("b", CaptureRisk.NORMAL)
        val web = CaptureTarget("c", CaptureRisk.LOW, isBrowser = true)
        assertTrue(ImageVigilance.appliesTo(risky, social))
        assertTrue(ImageVigilance.appliesTo(risky, web))
        assertFalse(ImageVigilance.appliesTo(risky, notes))
        assertTrue(ImageVigilance.appliesTo(all, notes))
    }
}
