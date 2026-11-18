package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtectedScreenTest {

    private val w = 100
    private val h = 100
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val gray = 0xFF202124.toInt()

    private fun image(fill: Int) = IntArray(w * h) { fill }

    @Test fun unaCapturaTotalmenteNegraEsProtegida() {
        assertTrue(ProtectedScreen.isProtected(image(black), w, h))
    }

    @Test fun laBarraDeEstadoVisibleNoImpide() {
        val px = image(black)
        // Las primeras 5 filas (la barra de estado) y las últimas 5 (la de navegación) se ven con normalidad.
        for (y in 0 until 5) for (x in 0 until w) { px[y * w + x] = white; px[(h - 1 - y) * w + x] = white }
        assertTrue(ProtectedScreen.isProtected(px, w, h))
    }

    @Test fun unaPaginaConContenidoNoEsProtegida() {
        assertFalse(ProtectedScreen.isProtected(image(white), w, h))
    }

    @Test fun unModoOscuroGrisNoEsNegroDelTodo() {
        assertFalse(ProtectedScreen.isProtected(image(gray), w, h))
    }

    @Test fun unNegroConPocoTextoSigueSiendoProtegido() {
        val px = image(black)
        // Un 2 % de la zona central con algo de contenido: por debajo del margen.
        for (i in 0 until 160) px[30 * w + (i % w)] = white
        assertTrue(ProtectedScreen.isProtected(px, w, h))
    }

    @Test fun conMasDelMargenDeContenidoYaNoEsProtegida() {
        val px = image(black)
        // Cuatro filas enteras de la zona central (un 5 % de ella) con contenido.
        for (y in 30 until 34) for (x in 0 until w) px[y * w + x] = white
        assertFalse(ProtectedScreen.isProtected(px, w, h))
    }

    @Test fun elNegroDeVerdadSeAdmiteConUnPocoDeRuidoDeCanal() {
        assertTrue(ProtectedScreen.isProtected(image(0xFF0A0A0A.toInt()), w, h))
        assertFalse(ProtectedScreen.isProtected(image(0xFF0B0B0B.toInt()), w, h))
    }

    @Test fun losDatosDeMenosOVaciosNuncaSonProtegidos() {
        assertFalse(ProtectedScreen.isProtected(IntArray(0), 0, 0))
        assertFalse(ProtectedScreen.isProtected(IntArray(10), w, h))
    }

    @Test fun hastaUnaImagenMuyPequenaSeMide() {
        assertTrue(ProtectedScreen.isProtected(IntArray(1) { black }, 1, 1))
    }

    @Test fun unaSolaCapturaProtegidaNoBasta() {
        val streak = ProtectedStreak()
        assertFalse(streak.record("com.brave.browser", true))
    }

    @Test fun dosProtegidasSeguidasDelMismoNavegadorAvisan() {
        val streak = ProtectedStreak()
        assertFalse(streak.record("com.brave.browser", true))
        assertTrue(streak.record("com.brave.browser", true))
    }

    @Test fun trasAvisarHacenFaltaOtrasDosParaVolverAAvisar() {
        val streak = ProtectedStreak()
        streak.record("com.brave.browser", true)
        assertTrue(streak.record("com.brave.browser", true))
        assertFalse(streak.record("com.brave.browser", true))
        assertTrue(streak.record("com.brave.browser", true))
    }

    @Test fun unaCapturaNormalEntreMedias() {
        val streak = ProtectedStreak()
        streak.record("com.brave.browser", true)
        assertFalse(streak.record("com.brave.browser", false))
        assertFalse(streak.record("com.brave.browser", true))
        assertTrue(streak.record("com.brave.browser", true))
    }

    @Test fun cambiarDeNavegadorEmpiezaLaCuenta() {
        val streak = ProtectedStreak()
        streak.record("com.brave.browser", true)
        assertFalse(streak.record("com.android.chrome", true))
        assertTrue(streak.record("com.android.chrome", true))
    }

    @Test fun sinSaberElPaqueteNuncaSuma() {
        val streak = ProtectedStreak()
        assertFalse(streak.record(null, true))
        assertFalse(streak.record(null, true))
    }

    @Test fun conUnaSolaCapturaNecesariaAvisaALaPrimera() {
        assertTrue(ProtectedStreak(needed = 1).record("com.brave.browser", true))
        assertFailsWith<IllegalArgumentException> { ProtectedStreak(needed = 0) }
    }
}
