package com.focusguard.decision

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaultLogTest {

    @Test
    fun aNewFaultIsAppended() {
        val log = FaultLog.add(emptyList(), 1_000, "imágenes", "captura fallida")
        assertEquals(listOf(Fault(1_000, 1_000, "imágenes", "captura fallida")), log)
    }

    @Test
    fun aRepeatedFaultCountsAndMovesToTheEnd() {
        var log = FaultLog.add(emptyList(), 1_000, "imágenes", "captura fallida")
        log = FaultLog.add(log, 2_000, "dns", "túnel")
        log = FaultLog.add(log, 3_000, "imágenes", "captura fallida")
        assertEquals(listOf("dns", "imágenes"), log.map { it.source })
        assertEquals(Fault(1_000, 3_000, "imágenes", "captura fallida", count = 2), log.last())
    }

    @Test
    fun onlyTheMostRecentAreKept() {
        var log = emptyList<Fault>()
        repeat(FaultLog.MAX_ENTRIES + 5) { log = FaultLog.add(log, it.toLong(), "s", "fallo $it") }
        assertEquals(FaultLog.MAX_ENTRIES, log.size)
        assertEquals("fallo 5", log.first().message)
    }

    @Test
    fun textIsCleanedSoItSurvivesEncoding() {
        val log = FaultLog.add(emptyList(), 5, "a\tb", "línea 1\nlínea 2\r\n" + "x".repeat(1_000))
        val back = FaultLog.decode(FaultLog.encode(log))
        assertEquals(log, back)
        assertEquals("a b", back.single().source)
        assertTrue(back.single().message.startsWith("línea 1 línea 2 x"))
        assertEquals(FaultLog.MAX_TEXT, back.single().message.length)
    }

    @Test
    fun brokenLinesAreSkipped() {
        assertEquals(listOf(Fault(1, 2, "s", "m", 3)), FaultLog.decode("basura\n1\t2\t3\ts\tm\nx\t2\t3\ts\tm"))
    }

    @Test
    fun describeGivesTypeMessageAndWhere() {
        val error = IllegalStateException("roto", RuntimeException("debajo"))
        val text = FaultLog.describe(error)
        assertTrue(text.startsWith("IllegalStateException: roto | en "), text)
        assertTrue(text.contains("FaultLogTest.describeGivesTypeMessageAndWhere:"), text)
        assertTrue(text.endsWith("causa: RuntimeException: debajo"), text)
    }
}
