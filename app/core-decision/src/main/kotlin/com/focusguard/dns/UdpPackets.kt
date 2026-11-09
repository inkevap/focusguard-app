package com.focusguard.dns

/** Un datagrama UDP sobre IPv4 leído de la VPN local. */
class UdpDatagram(
    val source: ByteArray,
    val destination: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val payload: ByteArray,
)

/**
 * Lo mínimo de IPv4 y UDP para una VPN que solo captura consultas DNS: leer una consulta del teléfono y armar la respuesta con las
 * direcciones y puertos cambiados. Todo lo que no sea un datagrama UDP completo (otros protocolos, IPv6, fragmentos) se ignora.
 */
object UdpPackets {
    private const val IPV4 = 4
    private const val UDP = 17
    private const val IP_HEADER_MIN = 20
    private const val UDP_HEADER = 8
    const val DNS_PORT = 53

    fun parse(packet: ByteArray, length: Int = packet.size): UdpDatagram? {
        if (length < IP_HEADER_MIN + UDP_HEADER || length > packet.size) return null
        if (packet[0].toInt() ushr 4 != IPV4) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < IP_HEADER_MIN || length < ihl + UDP_HEADER) return null
        if (packet[9].toInt() and 0xFF != UDP) return null
        // Un fragmento no trae la cabecera UDP completa (o es solo un trozo de datos): no se intenta recomponer.
        val fragment = ((packet[6].toInt() and 0x1F) shl 8) or (packet[7].toInt() and 0xFF)
        val moreFragments = packet[6].toInt() and 0x20 != 0
        if (fragment != 0 || moreFragments) return null
        val totalLength = u16(packet, 2)
        if (totalLength < ihl + UDP_HEADER || totalLength > length) return null
        val udpLength = u16(packet, ihl + 4)
        if (udpLength < UDP_HEADER || ihl + udpLength > totalLength) return null
        return UdpDatagram(
            source = packet.copyOfRange(12, 16),
            destination = packet.copyOfRange(16, 20),
            sourcePort = u16(packet, ihl),
            destinationPort = u16(packet, ihl + 2),
            payload = packet.copyOfRange(ihl + UDP_HEADER, ihl + udpLength),
        )
    }

    /**
     * La respuesta a [request]: mismas direcciones y puertos al revés, con [payload]. Si no cabe en [mtu] se parte en fragmentos IPv4 (el
     * sistema los recompone); una respuesta DNS grande no lleva bit de truncado porque el túnel no lleva TCP para repetirla.
     */
    fun reply(request: UdpDatagram, payload: ByteArray, mtu: Int = 1500, identification: Int = 0): List<ByteArray> {
        val udp = ByteArray(UDP_HEADER + payload.size)
        put16(udp, 0, request.destinationPort)
        put16(udp, 2, request.sourcePort)
        put16(udp, 4, udp.size)
        // La suma de comprobación de UDP es opcional en IPv4 (0 = sin ella).
        payload.copyInto(udp, UDP_HEADER)
        val chunk = (mtu - IP_HEADER_MIN) / 8 * 8
        val packets = ArrayList<ByteArray>(1)
        var offset = 0
        while (offset < udp.size) {
            val length = minOf(chunk, udp.size - offset)
            val more = offset + length < udp.size
            packets += ipv4(request, udp, offset, length, more, identification)
            offset += length
        }
        return packets
    }

    private fun ipv4(request: UdpDatagram, data: ByteArray, offset: Int, length: Int, moreFragments: Boolean, identification: Int): ByteArray {
        val out = ByteArray(IP_HEADER_MIN + length)
        out[0] = 0x45
        put16(out, 2, out.size)
        put16(out, 4, identification)
        put16(out, 6, (if (moreFragments) 0x2000 else 0) or (offset / 8))
        out[8] = 64 // TTL
        out[9] = UDP.toByte()
        request.destination.copyInto(out, 12)
        request.source.copyInto(out, 16)
        put16(out, 10, checksum(out, 0, IP_HEADER_MIN))
        data.copyInto(out, IP_HEADER_MIN, offset, offset + length)
        return out
    }

    fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i + 1 < offset + length) {
            sum += u16(data, i)
            i += 2
        }
        if (length % 2 == 1) sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }


    private fun put16(b: ByteArray, at: Int, value: Int) {
        b[at] = (value ushr 8).toByte()
        b[at + 1] = value.toByte()
    }
}

/** Lo mínimo de un mensaje DNS (RFC 1035): reconocer una consulta y armar un fallo si no hay respuesta. */
object DnsMessages {
    private const val HEADER = 12
    private const val MAX_NAME = 253
    private const val RCODE_REFUSED = 5
    private const val TYPE_A = 1
    private const val TYPE_AAAA = 28

    /** Una consulta normal: cabecera completa, bit de respuesta apagado y al menos una pregunta. */
    fun isQuery(payload: ByteArray): Boolean =
        payload.size >= HEADER && payload[2].toInt() and 0x80 == 0 && u16(payload, 4) >= 1

    fun id(payload: ByteArray): Int = u16(payload, 0)

    /** ¿Es [response] una respuesta con el mismo identificador que [query]? Sirve para no aceptar la respuesta de otra consulta. */
    fun answers(query: ByteArray, response: ByteArray): Boolean =
        response.size >= HEADER && response[2].toInt() and 0x80 != 0 && id(query) == id(response)

    /** Respuesta de error de servidor (SERVFAIL) a [query], con la misma pregunta: la app que preguntó prueba otra cosa en vez de esperar. */
    fun servfail(query: ByteArray): ByteArray {
        val question = questionLength(query)
        val out = query.copyOf(HEADER + question)
        out[2] = (0x80 or (query[2].toInt() and 0x01)).toByte() // QR=1, se conserva RD
        out[3] = 0x82.toByte() // RA=1, RCODE=2
        out[6] = 0; out[7] = 0; out[8] = 0; out[9] = 0; out[10] = 0; out[11] = 0 // sin respuestas ni más secciones
        out[4] = 0; out[5] = if (question > 0) 1 else 0 // la pregunta, si se pudo copiar
        return out
    }

    /** El nombre de la primera pregunta en minúsculas y sin el punto final («www.ejemplo.com»); `null` si está roto, comprimido o no es un nombre de máquina. */
    fun questionName(query: ByteArray): String? {
        if (!isQuery(query)) return null
        val labels = mutableListOf<String>()
        var i = HEADER
        var length = 0
        while (i < query.size) {
            val size = query[i].toInt() and 0xFF
            if (size == 0) return labels.takeIf { it.isNotEmpty() }?.joinToString(".")
            if (size and 0xC0 != 0 || i + 1 + size > query.size) return null
            length += size + 1
            if (length > MAX_NAME) return null
            val label = String(query, i + 1, size, Charsets.ISO_8859_1).lowercase()
            if (!label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) return null
            labels += label
            i += size + 1
        }
        return null
    }

    /**
     * ¿Es [response] una respuesta bloqueada por el filtro? El resolvedor (AdGuard Home con `blocking_mode: default`) bloquea de dos maneras:
     * con las reglas de lista de bloqueo contesta REFUSED, y con las de tipo «hosts» contesta la dirección «vacía» (0.0.0.0 o ::), sin error y
     * con todas las direcciones vacías. Cualquier respuesta que no se pueda leer cuenta como no bloqueada (no se inventa un bloqueo). Solo se
     * llama con respuestas del resolvedor propio: un REFUSED del DNS del sistema no es un bloqueo nuestro.
     */
    fun isBlocked(response: ByteArray): Boolean {
        if (response.size < HEADER || response[2].toInt() and 0x80 == 0) return false
        val rcode = response[3].toInt() and 0x0F
        if (rcode == RCODE_REFUSED) return true
        if (rcode != 0) return false
        val questions = u16(response, 4)
        val answers = u16(response, 6)
        if (answers == 0) return false
        var i = HEADER
        repeat(questions) {
            i = skipName(response, i) ?: return false
            i += 4
        }
        var addresses = 0
        repeat(answers) {
            i = skipName(response, i) ?: return false
            if (i + 10 > response.size) return false
            val type = u16(response, i)
            val rdLength = u16(response, i + 8)
            i += 10
            if (i + rdLength > response.size) return false
            if ((type == TYPE_A && rdLength == 4) || (type == TYPE_AAAA && rdLength == 16)) {
                addresses++
                if ((i until i + rdLength).any { response[it].toInt() != 0 }) return false
            }
            i += rdLength
        }
        return addresses > 0
    }

    /** Dónde acaba el nombre que empieza en [start] (con etiquetas o con un puntero de compresión); `null` si se sale del mensaje. */
    private fun skipName(bytes: ByteArray, start: Int): Int? {
        var i = start
        while (i < bytes.size) {
            val size = bytes[i].toInt() and 0xFF
            when {
                size == 0 -> return i + 1
                size and 0xC0 == 0xC0 -> return if (i + 2 <= bytes.size) i + 2 else null
                size and 0xC0 != 0 -> return null
                else -> i += size + 1
            }
        }
        return null
    }

    /** Longitud de la primera pregunta (nombre + tipo + clase); si el nombre está roto, solo la cabecera. */
    private fun questionLength(query: ByteArray): Int {
        var i = HEADER
        while (i < query.size) {
            val label = query[i].toInt() and 0xFF
            if (label == 0) return if (i + 5 <= query.size) i + 5 - HEADER else 0
            if (label and 0xC0 != 0) return 0 // un nombre comprimido no aparece en la pregunta de una consulta
            i += label + 1
        }
        return 0
    }
}

/** Dos bytes en orden de red como un número sin signo. */
private fun u16(bytes: ByteArray, at: Int) = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
