package com.focusguard.decision

import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockReason
import com.focusguard.model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LockBlockersTest {
    private val profiles = listOf(Profile(1, "Trabajo", "📵"), Profile(2, "Noche", "🌙"), Profile(3, "Juegos"))

    @Test fun elPrincipalVaPrimeroYLosDemasLeSiguen() {
        val d = BlockDecision.Block(BlockReason.PROFILE_APP, 2, "com.x", otherProfileIds = listOf(1, 3))
        assertEquals(listOf("Noche", "Trabajo", "Juegos"), LockBlockers.of(d, profiles).map { it.title })
    }

    @Test fun unPerfilBorradoOUnIdRepetidoNoSalen() {
        val d = BlockDecision.Block(BlockReason.PROFILE_APP, 1, "com.x", otherProfileIds = listOf(1, 99, 2))
        assertEquals(listOf("Trabajo", "Noche"), LockBlockers.of(d, profiles).map { it.title })
    }

    @Test fun sinPerfilesConocidosNoHayNadaQueNombrar() {
        assertEquals(emptyList(), LockBlockers.of(BlockDecision.Block(BlockReason.EMBEDDED_BROWSER, 0, "com.x"), profiles))
    }

    @Test fun limitaYCuentaLosQueSobran() {
        assertEquals(listOf(1, 2, 3) to 2, LockBlockers.limit(listOf(1, 2, 3, 4, 5)))
        assertEquals(listOf(1, 2) to 0, LockBlockers.limit(listOf(1, 2)))
        assertEquals(emptyList<Int>() to 0, LockBlockers.limit(emptyList<Int>()))
        assertFailsWith<IllegalArgumentException> { LockBlockers.limit(listOf(1), 0) }
    }
}
