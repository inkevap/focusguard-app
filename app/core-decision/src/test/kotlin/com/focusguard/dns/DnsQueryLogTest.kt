package com.focusguard.dns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DnsQueryLogTest {
    private fun name(host: String): ByteArray = host.split('.').flatMap { listOf(it.length.toByte()) + it.toByteArray().toList() }.toByteArray() + 0

    private fun query(host: String): ByteArray =
        byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0) + name(host) + byteArrayOf(0, 1, 0, 1)

    /** Una respuesta con [answers] registros (tipo, datos); el nombre de cada uno es un puntero a la pregunta. */
    private fun response(host: String, rcode: Int = 0, vararg answers: Pair<Int, ByteArray>): ByteArray {
        val header = byteArrayOf(0x12, 0x34, 0x81.toByte(), (0x80 or rcode).toByte(), 0, 1, 0, answers.size.toByte(), 0, 0, 0, 0)
        val records = answers.fold(ByteArray(0)) { acc, (type, data) ->
            acc + byteArrayOf(0xC0.toByte(), 12, 0, type.toByte(), 0, 1, 0, 0, 0, 10, 0, data.size.toByte()) + data
        }
        return header + name(host) + byteArrayOf(0, 1, 0, 1) + records
    }

    private val zero4 = ByteArray(4)
    private val zero16 = ByteArray(16)
    private val real4 = byteArrayOf(93.toByte(), 184.toByte(), 216.toByte(), 34)

    @Test fun elNombreDeLaPreguntaVaEnMinusculas() {
        assertEquals("www.ejemplo.com", DnsMessages.questionName(query("WWW.Ejemplo.com")))
        assertEquals("_dmarc.ejemplo.es", DnsMessages.questionName(query("_dmarc.ejemplo.es")))
    }

    @Test fun unNombreRotoOQueNoEsUnaConsultaNoDaNombre() {
        assertNull(DnsMessages.questionName(ByteArray(5)))
        assertNull(DnsMessages.questionName(response("a.com", 0, 1 to zero4)))
        // Una etiqueta que se sale del mensaje.
        assertNull(DnsMessages.questionName(byteArrayOf(0, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 20, 'a'.code.toByte())))
        // Caracteres que no son de un nombre de máquina.
        assertNull(DnsMessages.questionName(query("ej emplo.com")))
        assertNull(DnsMessages.questionName(query("ejemplo.com").also { it[12] = 0xC0.toByte() }))
    }

    @Test fun laDireccionVaciaEsUnBloqueo() {
        assertTrue(DnsMessages.isBlocked(response("anuncios.com", 0, 1 to zero4)))
        assertTrue(DnsMessages.isBlocked(response("anuncios.com", 0, 28 to zero16)))
        assertTrue(DnsMessages.isBlocked(response("anuncios.com", 0, 1 to zero4, 28 to zero16)))
    }

    @Test fun unaDireccionRealNoEsUnBloqueo() {
        assertFalse(DnsMessages.isBlocked(response("ejemplo.com", 0, 1 to real4)))
        // Si alguna dirección es real, no se bloqueó.
        assertFalse(DnsMessages.isBlocked(response("ejemplo.com", 0, 1 to zero4, 28 to ByteArray(16).also { it[15] = 1 })))
    }

    @Test fun unRechazoDelResolvedorEsUnBloqueo() {
        // Con las reglas de lista de bloqueo, AdGuard Home contesta REFUSED (código 5).
        assertTrue(DnsMessages.isBlocked(response("anuncios.com", 5)))
    }

    @Test fun unErrorOUnaRespuestaSinDireccionesNoEsUnBloqueo() {
        assertFalse(DnsMessages.isBlocked(response("no-existe.com", 3)))
        assertFalse(DnsMessages.isBlocked(response("roto.com", 2)))
        assertFalse(DnsMessages.isBlocked(response("ejemplo.com", 0)))
        // Solo un CNAME (tipo 5) no tiene direcciones.
        assertFalse(DnsMessages.isBlocked(response("ejemplo.com", 0, 5 to name("otro.com"))))
    }

    @Test fun unRegistroDeAliasSeguidoDeLaDireccionVaciaEsUnBloqueo() {
        assertTrue(DnsMessages.isBlocked(response("www.anuncios.com", 0, 5 to name("anuncios.com"), 1 to zero4)))
    }

    @Test fun unaRespuestaCortadaONoRespuestaNoCuentaComoBloqueo() {
        val whole = response("anuncios.com", 0, 1 to zero4)
        for (cut in 0 until whole.size - 1) assertFalse(DnsMessages.isBlocked(whole.copyOf(cut)), "cortada en $cut")
        assertFalse(DnsMessages.isBlocked(query("anuncios.com")))
    }

    @Test fun elContadorAgrupaPorDominioYPorBloqueo() {
        val tally = DnsTally()
        tally.add("a.com", blocked = false, atMs = 1)
        tally.add("a.com", blocked = false, atMs = 5)
        tally.add("a.com", blocked = true, atMs = 3)
        tally.add("b.com", blocked = true, atMs = 2)
        val counts = tally.drain().sortedWith(compareBy({ it.host }, { it.blocked }))
        assertEquals(
            listOf(DnsCount("a.com", false, 2, 5), DnsCount("a.com", true, 1, 3), DnsCount("b.com", true, 1, 2)),
            counts,
        )
    }

    @Test fun alVolcarSeEmpiezaDeCero() {
        val tally = DnsTally()
        tally.add("a.com", false, 1)
        assertFalse(tally.isEmpty)
        tally.drain()
        assertTrue(tally.isEmpty)
        assertEquals(emptyList(), tally.drain())
    }

    @Test fun conDemasiadosDominiosLosNuevosSeCuentanJuntosYNoSePierdeNingunaConsulta() {
        val tally = DnsTally(maxKeys = 2)
        tally.add("a.com", false, 1)
        tally.add("b.com", false, 1)
        tally.add("c.com", false, 1)
        tally.add("d.com", false, 2)
        tally.add("a.com", false, 3)
        val counts = tally.drain().associate { it.host to it.count }
        assertEquals(mapOf("a.com" to 2, "b.com" to 1, DnsTally.OTHERS to 2), counts)
        assertEquals(5, counts.values.sum())
    }

    @Test fun loQueNoSePudoGuardarSeDevuelveYSeSumaAlSiguienteVolcado() {
        val tally = DnsTally()
        tally.add("a.com", false, 1)
        val failed = tally.drain()
        tally.add("a.com", false, 5)
        tally.restore(failed)
        assertEquals(listOf(DnsCount("a.com", false, 2, 5)), tally.drain())
    }

    @Test fun elHistorialDuraTreintaDias() {
        assertEquals(1000L - 29, DnsLogPolicy.firstDayToKeep(1000L))
    }

    @Test fun alCompanieroSoloSeLeCuentaCuantasConsultasHuboUnDia() {
        assertEquals("12,340", DnsSummary.label(day = 100, lastReportedDay = 99, blocked = 12, allowed = 340))
        assertEquals("0,5", DnsSummary.label(100, null, 0, 5))
    }

    @Test fun unDiaYaContadoOSinConsultasNoSeVuelveAContar() {
        assertNull(DnsSummary.label(100, 100, 12, 340))
        assertNull(DnsSummary.label(99, 100, 12, 340))
        assertNull(DnsSummary.label(100, null, 0, 0))
    }
}
