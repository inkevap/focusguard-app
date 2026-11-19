package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SplitScreenTest {
    private val w = 1080
    private val h = 2400

    private fun app(l: Int, t: Int, r: Int, b: Int, pip: Boolean = false) = WindowInfoLite(WindowKind.APP, l, t, r, b, pip)
    private val full = app(0, 0, w, h)
    private val top = app(0, 0, w, 1180)
    private val bottom = app(0, 1220, w, h)
    private val divider = WindowInfoLite(WindowKind.SPLIT_DIVIDER, 0, 1180, w, 1220)

    @Test
    fun unaAppAPantallaCompletaNoEsDivision() = assertFalse(SplitScreen.isSplit(listOf(full), w, h))

    @Test
    fun elDivisorBastaPorSiSolo() = assertTrue(SplitScreen.isSplit(listOf(top, divider, bottom), w, h))

    @Test
    fun elDivisorCuentaAunqueSoloSeVeaUnaApp() = assertTrue(SplitScreen.isSplit(listOf(top, divider), w, h))

    @Test
    fun sinDivisorDosMitadesQueLlenanLaPantallaSonDivision() = assertTrue(SplitScreen.isSplit(listOf(top, bottom), w, h))

    @Test
    fun tambienEnVertical() {
        val left = app(0, 0, 530, h)
        val right = app(550, 0, w, h)
        assertTrue(SplitScreen.isSplit(listOf(left, right), w, h))
    }

    @Test
    fun unaAppCompletaMasUnaVentanaDeTransicionNoEsDivision() {
        // El visor de estados o el cambio de app: la app nueva a pantalla completa con la anterior detrás.
        assertFalse(SplitScreen.isSplit(listOf(full, app(0, 0, w, h)), w, h))
        assertFalse(SplitScreen.isSplit(listOf(full, bottom), w, h))
    }

    @Test
    fun unaVentanaFlotantePequenaNoEsDivision() {
        val floating = app(200, 600, 900, 1400)
        assertFalse(SplitScreen.isSplit(listOf(full, floating), w, h))
    }

    @Test
    fun dosVentanasFlotantesQueSeTapanNoSonDivision() {
        val a = app(0, 200, 900, 1500)
        val b = app(100, 400, w, 1700)
        assertFalse(SplitScreen.isSplit(listOf(a, b), w, h))
    }

    @Test
    fun dosVentanasQueNoLlenanLaPantallaNoSonDivision() {
        val a = app(0, 0, 500, 900)
        val b = app(500, 0, w, 900)
        assertFalse(SplitScreen.isSplit(listOf(a, b), w, h))
    }

    @Test
    fun laImagenEnImagenNoCuenta() {
        val pip = app(600, 1600, w, 2000, pip = true)
        assertFalse(SplitScreen.isSplit(listOf(full, pip), w, h))
        assertFalse(SplitScreen.isSplit(listOf(top, pip), w, h))
    }

    @Test
    fun elTecladoNoEsUnaVentanaDeApp() {
        val ime = WindowInfoLite(WindowKind.OTHER, 0, 1500, w, h)
        assertFalse(SplitScreen.isSplit(listOf(app(0, 0, w, 1500), ime), w, h))
    }

    @Test
    fun conTresVentanasDeAppSinDivisorNoSeDecide() = assertFalse(SplitScreen.isSplit(listOf(top, bottom, app(0, 0, 400, 400)), w, h))

    @Test
    fun ventanasVaciasOPantallaSinTamanoNoSonDivision() {
        assertFalse(SplitScreen.isSplit(emptyList(), w, h))
        assertFalse(SplitScreen.isSplit(listOf(top, bottom), 0, 0))
        assertFalse(SplitScreen.isSplit(listOf(app(0, 0, 0, 0), app(0, 0, 0, 0)), w, h))
    }

    @Test
    fun elSeguimientoEsperaAntesDeConfirmar() {
        val t = SplitScreenTracker(400)
        assertFalse(t.update(1_000, true))
        assertEquals(400, t.remainingMs(1_000))
        assertFalse(t.update(1_300, true))
        assertEquals(100, t.remainingMs(1_300))
        assertTrue(t.update(1_400, true))
        assertTrue(t.update(5_000, true))
    }

    @Test
    fun unaLecturaSinDivisionReiniciaLaCuenta() {
        val t = SplitScreenTracker(400)
        t.update(1_000, true)
        assertFalse(t.update(1_200, false))
        assertNull(t.remainingMs(1_200))
        assertFalse(t.update(1_300, true))
        assertFalse(t.update(1_600, true))
        assertTrue(t.update(1_700, true))
    }
}
