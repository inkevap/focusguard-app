package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageCapturePlanTest {

    private val trusted = setOf("com.bank", "com.focusguard")

    @Test fun conLaPantallaApagadaNoSeCaptura() {
        assertEquals(SkipReason.SCREEN_OFF, CaptureGate.skipReason(screenOn = false, packageName = "com.x", trusted = trusted, unchanged = false))
    }

    @Test fun lasAppsDeConfianzaNoSeMiran() {
        assertEquals(SkipReason.TRUSTED_APP, CaptureGate.skipReason(true, "com.bank", trusted, unchanged = false))
    }

    @Test fun unaPantallaIgualSeReutiliza() {
        assertEquals(SkipReason.UNCHANGED, CaptureGate.skipReason(true, "com.x", trusted, unchanged = true))
    }

    @Test fun sinSaberLaAppNoSeDaPorDeConfianza() {
        assertNull(CaptureGate.skipReason(true, null, trusted, unchanged = false))
    }

    @Test fun primeroPantallaApagadaLuegoConfianzaLuegoIgual() {
        assertEquals(SkipReason.SCREEN_OFF, CaptureGate.skipReason(false, "com.bank", trusted, unchanged = true))
        assertEquals(SkipReason.TRUSTED_APP, CaptureGate.skipReason(true, "com.bank", trusted, unchanged = true))
    }

    @Test fun unaSolaMarcaNoBasta() {
        val w = ConfirmationWindow()
        assertFalse(w.record(0, true))
        assertTrue(w.record(10_000, true))
    }

    @Test fun soloCuentanLasUltimasCapturas() {
        val w = ConfirmationWindow(needed = 2, of = 3)
        assertFalse(w.record(0, true))
        assertFalse(w.record(1_000, false))
        assertTrue(w.record(2_000, true)) // dos marcas entre las últimas tres
        w.clear()
        assertFalse(w.record(3_000, false))
        assertFalse(w.record(4_000, false))
        assertFalse(w.record(5_000, true))
        assertFalse(w.record(6_000, false)) // la marca sigue sola
    }

    @Test fun unaMarcaMuyViejaNoSumaALaDeAhora() {
        val w = ConfirmationWindow(needed = 2, of = 3, maxAgeMs = 5 * 60_000L)
        assertFalse(w.record(0, true))
        assertFalse(w.record(6 * 60_000L, true))
        assertTrue(w.record(6 * 60_000L + 40_000L, true))
    }

    @Test fun trasActuarSeEmpiezaDeCero() {
        val w = ConfirmationWindow()
        w.record(0, true)
        assertTrue(w.record(1_000, true))
        w.clear()
        assertFalse(w.record(2_000, true))
    }

    @Test fun laVentanaNoAceptaPedirMasDeLoQueCabe() {
        assertFailsWith<IllegalArgumentException> { ConfirmationWindow(needed = 4, of = 3) }
        assertFailsWith<IllegalArgumentException> { ConfirmationWindow(needed = 0, of = 3) }
    }

    private fun assess(result: ImageResult) =
        ImageAssessment(if (result.verdict == ImageVerdict.EXPLICIT) ImageLevel.EXPLICIT else ImageLevel.SAFE, result, null)

    private val safe = assess(ImageResult(ImageVerdict.SAFE, 0.02, listOf("a")))
    private val suspect = assess(ImageResult(ImageVerdict.SAFE, 0.3, listOf("a", "b"), borderline = true))
    private val explicit = assess(ImageResult(ImageVerdict.EXPLICIT, 0.95, listOf("a")))

    private val minute = 60_000L

    /** Hace [times] capturas con ese resultado empezando en [from], una cada vez que toca, y devuelve el momento de la siguiente. */
    private fun CaptureScheduler.run(key: String, risk: CaptureRisk, result: ImageAssessment?, from: Long, times: Int): Long {
        var t = from
        repeat(times) {
            t += intervalMs(key, risk, t)
            record(key, result, t)
        }
        return t
    }

    @Test fun alEntrarSeCapturaAlMinimoPosibleDuranteDosMinutos() {
        val s = CaptureScheduler()
        for (risk in CaptureRisk.entries) assertEquals(1_000L, s.intervalMs("app-$risk", risk, 0))
        // Justo antes de los dos minutos sigue al mínimo, también si todo sale seguro.
        val t = s.run("app", CaptureRisk.NORMAL, safe, 0, 119)
        assertEquals(119_000L, t)
        assertEquals(1_000L, s.intervalMs("app", CaptureRisk.NORMAL, t))
        assertTrue(s.intervalMs("app", CaptureRisk.NORMAL, 2 * minute) > 1_000L)
    }

    @Test fun trasLosDosMinutosElTiempoCreceDespacio() {
        val s = CaptureScheduler()
        var t = s.run("app", CaptureRisk.NORMAL, safe, 0, 120)
        val seen = mutableListOf<Long>()
        repeat(8) {
            val step = s.intervalMs("app", CaptureRisk.NORMAL, t)
            seen += step
            t += step
            s.record("app", safe, t)
        }
        assertEquals(seen.sorted(), seen, "solo crece")
        assertTrue(seen.first() in 1_000L..2_000L, "empieza cerca del mínimo: ${seen.first()}")
        assertTrue(seen.zipWithNext().all { (a, b) -> b <= a * 1.4 + 1 }, "cada paso es un 30 % más largo, no un salto: $seen")
    }

    @Test fun elTiempoLlegaAlTopeSegunElRiesgoYNoLoPasa() {
        val s = CaptureScheduler()
        var t = s.run("a", CaptureRisk.NORMAL, safe, 0, 120)
        t = s.run("a", CaptureRisk.NORMAL, safe, t, 80)
        assertEquals(102_000L, s.intervalMs("a", CaptureRisk.NORMAL, t))
        assertEquals(51_000L, s.intervalMs("a", CaptureRisk.HIGH, t))
        assertEquals(204_000L, s.intervalMs("a", CaptureRisk.LOW, t))
    }

    @Test fun unaSolaCapturaDudosaVuelveAlMinimoOtrosDosMinutos() {
        val s = CaptureScheduler()
        var t = s.run("app", CaptureRisk.NORMAL, safe, 0, 120)
        t = s.run("app", CaptureRisk.NORMAL, safe, t, 80)
        assertEquals(102_000L, s.intervalMs("app", CaptureRisk.NORMAL, t))
        s.record("app", suspect, t)
        assertEquals(1_000L, s.intervalMs("app", CaptureRisk.NORMAL, t))
        assertEquals(1_000L, s.intervalMs("app", CaptureRisk.NORMAL, t + 2 * minute - 1))
        assertTrue(s.intervalMs("app", CaptureRisk.NORMAL, t + 2 * minute) > 1_000L)
    }

    @Test fun unResultadoReutilizadoNoRenuevaElTiempoAlMinimo() {
        val s = CaptureScheduler()
        s.record("app", suspect, 0)
        // Pantalla quieta: se reutiliza lo dudoso cada segundo. A los dos minutos el tiempo vuelve a crecer en lugar de quedarse al mínimo.
        var t = 0L
        while (t < 2 * minute) {
            t += 1_000L
            s.record("app", suspect, t, fresh = false)
        }
        assertTrue(s.intervalMs("app", CaptureRisk.NORMAL, t) > 1_000L)
    }

    @Test fun cadaAppOWebLlevaSuPropioReloj() {
        val s = CaptureScheduler()
        val t = s.run("a", CaptureRisk.NORMAL, safe, 0, 200)
        assertTrue(s.intervalMs("a", CaptureRisk.NORMAL, t) > 1_000L)
        // Otra app a la que se entra ahora empieza sus dos minutos al mínimo.
        assertEquals(1_000L, s.intervalMs("b", CaptureRisk.NORMAL, t))
    }

    @Test fun volverTrasUnRatoEmpiezaDeNuevo() {
        val s = CaptureScheduler()
        var t = s.run("a", CaptureRisk.NORMAL, safe, 0, 200)
        assertTrue(s.intervalMs("a", CaptureRisk.NORMAL, t) > 1_000L)
        // Se estuvo fuera más de dos minutos: al volver son otros dos minutos al mínimo.
        t += 3 * minute
        assertEquals(1_000L, s.intervalMs("a", CaptureRisk.NORMAL, t))
        // Un salto corto a otra app no lo reinicia.
        val u = s.run("c", CaptureRisk.NORMAL, safe, t, 200)
        s.intervalMs("d", CaptureRisk.NORMAL, u)
        assertTrue(s.intervalMs("c", CaptureRisk.NORMAL, u + 30_000L) > 1_000L)
    }

    @Test fun lasCapturasQueNoSePudieronPuntuarNoCambianNada() {
        val s = CaptureScheduler()
        val t = s.run("a", CaptureRisk.NORMAL, safe, 0, 200)
        val before = s.intervalMs("a", CaptureRisk.NORMAL, t)
        s.record("a", null, t)
        assertEquals(before, s.intervalMs("a", CaptureRisk.NORMAL, t))
        s.record("a", suspect, t)
        s.record("a", null, t)
        assertEquals(1_000L, s.intervalMs("a", CaptureRisk.NORMAL, t))
    }

    @Test fun laVigilanciaAcortaPeroNuncaPorDebajoDelMinimo() {
        val s = CaptureScheduler()
        assertEquals(1_000L, s.intervalMs("a", CaptureRisk.NORMAL, 0, boost = 0.33))
        val t = s.run("a", CaptureRisk.NORMAL, safe, 0, 200)
        val normal = s.intervalMs("a", CaptureRisk.NORMAL, t)
        assertTrue(kotlin.math.abs(normal / 2 - s.intervalMs("a", CaptureRisk.NORMAL, t, boost = 0.5)) <= 1L)
        // Un factor extremo se queda en un décimo del tiempo (102 s pasan a 10,2 s).
        assertEquals(10_200L, s.intervalMs("a", CaptureRisk.NORMAL, t, boost = 0.0001))
    }

    @Test fun recorrerMuchasWebsNoHaceCrecerLaMemoriaSinLimite() {
        val s = CaptureScheduler()
        repeat(500) { s.intervalMs("web$it", CaptureRisk.NORMAL, it.toLong()) }
        // Pasado el tope, la cuenta se reinicia en lugar de crecer: una web antigua vuelve a empezar al mínimo.
        assertEquals(1_000L, s.intervalMs("web0", CaptureRisk.NORMAL, 600))
    }

    @Test fun losTiemposFueraDeOrdenSeRechazan() {
        assertFailsWith<IllegalArgumentException> { CaptureTiming(minMs = 0L) }
        assertFailsWith<IllegalArgumentException> { CaptureTiming(minMs = 200_000L) }
        assertFailsWith<IllegalArgumentException> { CaptureTiming(growth = 1.0) }
        assertFailsWith<IllegalArgumentException> { CaptureTiming(holdMs = -1) }
    }
}
