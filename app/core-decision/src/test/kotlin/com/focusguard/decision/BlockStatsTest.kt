package com.focusguard.decision

import com.focusguard.model.BlockEvent
import com.focusguard.model.BlockReason
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockStatsTest {

    private fun event(at: String, target: String) = BlockEvent(Instant.parse(at), 1, BlockReason.PROFILE_APP, target)
    private val today = LocalDate.of(2026, 10, 26)

    @Test
    fun cuentaPorDiaIncluyendoDiasSinBloqueos() {
        val events = listOf(
            event("2026-10-26T08:00:00Z", "a"),
            event("2026-10-26T23:59:00Z", "b"),
            event("2026-10-24T12:00:00Z", "a"),
            event("2026-10-10T12:00:00Z", "a"), // fuera de la ventana
        )
        val result = BlockStats.perDay(events, today, days = 4, zone = ZoneOffset.UTC)
        assertEquals(
            listOf(
                LocalDate.of(2026, 10, 23) to 0,
                LocalDate.of(2026, 10, 24) to 1,
                LocalDate.of(2026, 10, 25) to 0,
                LocalDate.of(2026, 10, 26) to 2,
            ),
            result,
        )
    }

    @Test
    fun elDiaDependeDeLaZonaHoraria() {
        val e = listOf(event("2026-10-26T23:30:00Z", "a"))
        val plus2 = ZoneOffset.ofHours(2) // ya es 27
        assertEquals(1, BlockStats.perDay(e, LocalDate.of(2026, 10, 27), 1, plus2).single().second)
        assertEquals(0, BlockStats.perDay(e, LocalDate.of(2026, 10, 26), 1, plus2).single().second)
    }

    @Test
    fun masBloqueadosOrdenaPorCuentaYDespuesPorNombre() {
        val events = listOf("b", "a", "b", "c", "a", "b").map { event("2026-10-26T08:00:00Z", it) }
        assertEquals(listOf("b" to 3, "a" to 2), BlockStats.topTargets(events, 2))
        assertEquals(listOf("b" to 3, "a" to 2, "c" to 1), BlockStats.topTargets(events, 10))
        assertEquals(emptyList(), BlockStats.topTargets(emptyList(), 5))
    }
}
