package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GantManOutputTest {

    // Orden de la salida: dibujos, hentai, neutro, porno, sexy.
    @Test fun loExplicitoEsPornoMasHentai() {
        assertEquals(0.7, GantManOutput.explicitProbability(listOf(0.1, 0.2, 0.1, 0.5, 0.1))!!, 1e-9)
    }

    @Test fun neutroDibujosYSexyNoCuentan() {
        assertEquals(0.0, GantManOutput.explicitProbability(listOf(0.3, 0.0, 0.4, 0.0, 0.3))!!, 1e-9)
    }

    @Test fun unaImagenSeguraDaCasiCero() {
        assertEquals(0.02, GantManOutput.explicitProbability(listOf(0.0, 0.01, 0.97, 0.01, 0.01))!!, 1e-9)
    }

    @Test fun unaSalidaConLaFormaEquivocadaNoSePuntua() {
        assertNull(GantManOutput.explicitProbability(listOf(0.5, 0.5)))
        assertNull(GantManOutput.explicitProbability(emptyList()))
        assertNull(GantManOutput.explicitProbability(listOf(0.2, 0.2, 0.2, 0.2, 0.2, 0.2)))
    }

    @Test fun siNoSumaUnoNoEsUnaSalidaDeSoftmax() {
        assertNull(GantManOutput.explicitProbability(listOf(0.9, 0.9, 0.9, 0.9, 0.9)))
        assertNull(GantManOutput.explicitProbability(listOf(0.0, 0.0, 0.0, 0.0, 0.0)))
    }

    @Test fun losValoresNoNumericosONegativosSeRechazan() {
        assertNull(GantManOutput.explicitProbability(listOf(Double.NaN, 0.0, 0.5, 0.5, 0.0)))
        assertNull(GantManOutput.explicitProbability(listOf(-0.5, 0.0, 1.0, 0.5, 0.0)))
        assertNull(GantManOutput.explicitProbability(listOf(0.0, 0.0, Double.POSITIVE_INFINITY, 0.0, 0.0)))
    }

    @Test fun elRedondeoPequenoSeAcepta() {
        assertEquals(0.5, GantManOutput.explicitProbability(listOf(0.0, 0.0, 0.49, 0.5, 0.0))!!, 1e-9)
    }

    @Test fun cadaClaseTieneNombre() {
        assertEquals(listOf("dibujos", "hentai", "neutro", "porno", "sexy"), (0 until 5).map(GantManOutput::label))
    }
}
