package com.focusguard.dns

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UdpPacketsTest {
    private val phone = byteArrayOf(10, 111, 222.toByte(), 1)
    private val dns = byteArrayOf(10, 111, 222.toByte(), 2)

    /** Una consulta de A para «a.b» (id 0x1234, con recursión). */
    private val query = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0, 1, 'a'.code.toByte(), 1, 'b'.code.toByte(), 0, 0, 1, 0, 1)

    private fun packet(payload: ByteArray, protocol: Int = 17, flagsFragment: Int = 0, srcPort: Int = 40000, dstPort: Int = 53): ByteArray {
        val total = 20 + 8 + payload.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total ushr 8).toByte(); p[3] = total.toByte()
        p[6] = (flagsFragment ushr 8).toByte(); p[7] = flagsFragment.toByte()
        p[8] = 64; p[9] = protocol.toByte()
        phone.copyInto(p, 12); dns.copyInto(p, 16)
        p[20] = (srcPort ushr 8).toByte(); p[21] = srcPort.toByte()
        p[22] = (dstPort ushr 8).toByte(); p[23] = dstPort.toByte()
        val udp = 8 + payload.size
        p[24] = (udp ushr 8).toByte(); p[25] = udp.toByte()
        payload.copyInto(p, 28)
        return p
    }

    @Test fun leeUnaConsultaDns() {
        val datagram = assertNotNull(UdpPackets.parse(packet(query)))
        assertEquals(53, datagram.destinationPort)
        assertEquals(40000, datagram.sourcePort)
        assertContentEquals(query, datagram.payload)
        assertContentEquals(dns, datagram.destination)
    }

    @Test fun ignoraLoQueNoEsUdpIpv4Completo() {
        assertNull(UdpPackets.parse(packet(query, protocol = 6)))
        assertNull(UdpPackets.parse(packet(query, flagsFragment = 0x2000)))
        assertNull(UdpPackets.parse(packet(query, flagsFragment = 0x0010)))
        assertNull(UdpPackets.parse(ByteArray(10)))
        val v6 = packet(query).also { it[0] = 0x60 }
        assertNull(UdpPackets.parse(v6))
        val truncated = packet(query)
        assertNull(UdpPackets.parse(truncated, length = 25))
    }

    @Test fun laRespuestaVuelveAlRevesYConSumaDeCabeceraValida() {
        val request = assertNotNull(UdpPackets.parse(packet(query)))
        val response = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte())
        val bytes = UdpPackets.reply(request, response).single()
        val back = assertNotNull(UdpPackets.parse(bytes))
        assertContentEquals(phone, back.destination)
        assertContentEquals(dns, back.source)
        assertEquals(40000, back.destinationPort)
        assertEquals(53, back.sourcePort)
        assertContentEquals(response, back.payload)
        // Una cabecera IPv4 correcta suma cero (complemento a uno) incluyendo su propia suma.
        assertEquals(0, UdpPackets.checksum(bytes, 0, 20))
    }

    @Test fun reconoceUnaConsultaYSuRespuesta() {
        assertTrue(DnsMessages.isQuery(query))
        val response = query.copyOf().also { it[2] = (it[2].toInt() or 0x80).toByte() }
        assertFalse(DnsMessages.isQuery(response))
        assertTrue(DnsMessages.answers(query, response))
        val other = response.copyOf().also { it[1] = 0x35 }
        assertFalse(DnsMessages.answers(query, other))
        assertFalse(DnsMessages.isQuery(ByteArray(5)))
    }

    @Test fun elFalloDeServidorConservaLaPreguntaYElIdentificador() {
        val fail = DnsMessages.servfail(query)
        assertEquals(0x1234, DnsMessages.id(fail))
        assertEquals(0x80, fail[2].toInt() and 0x80)
        assertEquals(2, fail[3].toInt() and 0x0F)
        assertEquals(query.size, fail.size)
        assertEquals(1, fail[5].toInt())
        assertEquals(0, fail[7].toInt())
    }

    @Test fun unaRespuestaGrandeSeParteEnFragmentosQueSeRecomponen() {
        val request = assertNotNull(UdpPackets.parse(packet(query)))
        val big = ByteArray(3000) { it.toByte() }
        val packets = UdpPackets.reply(request, big, mtu = 1500, identification = 7)
        assertEquals(3, packets.size)
        assertTrue(packets.all { it.size <= 1500 })
        // Todos comparten el identificador y solo el último no lleva «más fragmentos»; los desplazamientos van en bloques de 8 bytes.
        assertTrue(packets.all { ((it[4].toInt() and 0xFF) shl 8 or (it[5].toInt() and 0xFF)) == 7 })
        assertEquals(listOf(true, true, false), packets.map { it[6].toInt() and 0x20 != 0 })
        val rebuilt = packets.flatMap { it.drop(20) }.toByteArray()
        assertEquals(8 + 3000, rebuilt.size)
        assertContentEquals(big, rebuilt.copyOfRange(8, rebuilt.size))
        packets.forEach { assertEquals(0, UdpPackets.checksum(it, 0, 20)) }
    }

    @Test fun elFalloSinPreguntaLegibleNoDiceTenerla() {
        val broken = ByteArray(14).also { it[5] = 1; it[12] = 0x7F }
        val fail = DnsMessages.servfail(broken)
        assertEquals(0, fail[5].toInt())
        assertEquals(12, fail.size)
    }
}
