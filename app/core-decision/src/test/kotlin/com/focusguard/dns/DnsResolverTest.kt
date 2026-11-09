package com.focusguard.dns

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class DnsResolverTest {
    private val query = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0, 1, 'a'.code.toByte(), 0, 0, 1, 0, 1)
    private val answer = query.copyOf().also { it[2] = 0x81.toByte() }
    private val other = query.copyOf().also { it[2] = 0x81.toByte(); it[17] = 9 }

    private class Fake(var reply: ByteArray?) : UpstreamResolver {
        var calls = 0

        override suspend fun resolve(query: ByteArray): ByteArray? {
            calls++
            return reply
        }
    }

    private var now = 0L

    @Test fun conElResolvedorSeResponde() = runBlocking {
        val primary = Fake(answer)
        val result = DnsResolver(primary, Fake(other), { now }).answer(query)
        assertEquals(DnsAnswer.Source.FILTERED, result.source)
        assertContentEquals(answer, result.bytes)
    }

    @Test fun siElResolvedorCaeYSePermiteSeUsaElSistema() = runBlocking {
        val result = DnsResolver(Fake(null), Fake(other), { now }).answer(query)
        assertEquals(DnsAnswer.Source.SYSTEM, result.source)
        assertContentEquals(other, result.bytes)
    }

    @Test fun fallandoCerradoNoSeContestaConElSistema() = runBlocking {
        val system = Fake(other)
        val result = DnsResolver(Fake(null), null, { now }).answer(query)
        assertEquals(DnsAnswer.Source.FAILED, result.source)
        assertEquals(0, system.calls)
        assertEquals(2, result.bytes[3].toInt() and 0x0F)
    }

    @Test fun siTampocoContestaElSistemaHayUnFallo() = runBlocking {
        val result = DnsResolver(Fake(null), Fake(null), { now }).answer(query)
        assertEquals(DnsAnswer.Source.FAILED, result.source)
        assertEquals(0x1234, DnsMessages.id(result.bytes))
    }

    @Test fun unFalloSueltoNoDejaDeFiltrar() = runBlocking {
        val primary = Fake(null)
        val resolver = DnsResolver(primary, Fake(other), { now })
        resolver.answer(query)
        primary.reply = answer
        assertEquals(DnsAnswer.Source.FILTERED, resolver.answer(query).source)
        assertEquals(2, primary.calls)
    }

    @Test fun trasVariosFallosSeguidosNoSeEsperaAlResolvedorEnCadaConsulta() = runBlocking {
        val primary = Fake(null)
        val resolver = DnsResolver(primary, Fake(other), { now })
        repeat(3) { resolver.answer(query) }
        now = 5_000
        resolver.answer(query)
        resolver.answer(query)
        assertEquals(3, primary.calls)
    }

    @Test fun pasadoElTiempoSeVuelveAProbarElResolvedor() = runBlocking {
        val primary = Fake(null)
        val resolver = DnsResolver(primary, Fake(other), { now })
        repeat(3) { resolver.answer(query) }
        now = 11_000
        primary.reply = answer
        val result = resolver.answer(query)
        assertEquals(DnsAnswer.Source.FILTERED, result.source)
        assertEquals(4, primary.calls)
        // Y con él otra vez al día, ya no se salta.
        resolver.answer(query)
        assertEquals(5, primary.calls)
    }

    @Test fun fallandoCerradoSiempreSePruebaElResolvedor() = runBlocking {
        val primary = Fake(null)
        val resolver = DnsResolver(primary, null, { now })
        resolver.answer(query)
        resolver.answer(query)
        assertEquals(2, primary.calls)
    }

    @Test fun elProxyContestaSoloConsultasDnsPorUdp() = runBlocking {
        val proxy = DnsProxy(DnsResolver(Fake(answer), null, { now }))
        val phone = byteArrayOf(10, 111, 222.toByte(), 1)
        val dns = byteArrayOf(10, 111, 222.toByte(), 2)
        fun packet(dstPort: Int): ByteArray {
            val total = 28 + query.size
            val p = ByteArray(total)
            p[0] = 0x45; p[2] = (total ushr 8).toByte(); p[3] = total.toByte(); p[8] = 64; p[9] = 17
            phone.copyInto(p, 12); dns.copyInto(p, 16)
            p[20] = 0x9C.toByte(); p[21] = 0x40
            p[22] = (dstPort ushr 8).toByte(); p[23] = dstPort.toByte()
            val udp = 8 + query.size
            p[24] = (udp ushr 8).toByte(); p[25] = udp.toByte()
            query.copyInto(p, 28)
            return p
        }
        assertNull(proxy.accept(packet(443), packet(443).size))
        val request = assertNotNull(proxy.accept(packet(53), packet(53).size))
        val reply = assertNotNull(UdpPackets.parse(proxy.respond(request).single()))
        assertContentEquals(answer, reply.payload)
        assertEquals(53, reply.sourcePort)
        assertEquals(0x9C40, reply.destinationPort)
    }
}
