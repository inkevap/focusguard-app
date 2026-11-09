package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowEventsTest {
    private val google = "com.google.android.googlequicksearchbox"
    private val launcher = "com.motorola.launcher3"

    @Test fun unEventoDeGoogleSinVentanaMientrasLaActivaEsElLanzadorEsFantasma() {
        assertTrue(WindowEvents.isGhost(google, launcher) { listOf("com.android.systemui", launcher) })
    }

    @Test fun elEventoDeLaVentanaActivaNoEsFantasmaNiLeeLasVentanas() {
        assertFalse(WindowEvents.isGhost(launcher, launcher) { error("no debe leer las ventanas") })
    }

    @Test fun siLaAppTieneUnaVentanaEnPantallaNoEsFantasma() {
        // La app de Google abierta de verdad (o en pantalla dividida): hay una ventana suya, así que se sigue decidiendo sobre ella.
        assertFalse(WindowEvents.isGhost(google, launcher) { listOf(launcher, google) })
    }

    @Test fun sinVentanasLeidasSeDescartaPorqueNoHayNingunaVisible() {
        assertTrue(WindowEvents.isGhost(google, launcher) { emptyList() })
    }
}
