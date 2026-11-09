package com.focusguard.decision

import com.focusguard.model.BlockReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WrongBlockReportsTest {
    @Test fun unaWebBloqueadaComoAdultaSePuedeReportar() {
        assertEquals(WrongBlockReport(WrongBlockReport.Kind.DOMAIN, "ejemplo.org"), WrongBlockReports.of(BlockReason.ADULT_CONTENT, "ejemplo.org", targetIsApp = false))
    }

    @Test fun unaAppBloqueadaPorSuTextoSeReportaComoApp() {
        assertEquals(WrongBlockReport(WrongBlockReport.Kind.APP, "com.ejemplo.app"), WrongBlockReports.of(BlockReason.ADULT_CONTENT, "com.ejemplo.app", targetIsApp = true))
    }

    @Test fun losDemasBloqueosSonDecisionesDelUsuario() {
        BlockReason.entries.filter { it != BlockReason.ADULT_CONTENT }.forEach {
            assertNull(WrongBlockReports.of(it, "ejemplo.org", targetIsApp = false), it.name)
        }
    }

    @Test fun sinObjetivoNoHayNadaQueReportar() {
        assertNull(WrongBlockReports.of(BlockReason.ADULT_CONTENT, " ", targetIsApp = false))
    }

    @Test fun elValorSeRecortaAlTopeDelServidor() {
        assertEquals(200, WrongBlockReports.of(BlockReason.ADULT_CONTENT, "a".repeat(500), targetIsApp = false)!!.value.length)
    }
}
