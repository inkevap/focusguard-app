package com.focusguard.dns

/** Alguien que contesta consultas DNS: el resolvedor del servidor, el DNS del sistema... `null` si no contestó. */
fun interface UpstreamResolver {
    suspend fun resolve(query: ByteArray): ByteArray?
}

class DnsAnswer(val bytes: ByteArray, val source: Source) {
    enum class Source {
        /** La respuesta del resolvedor del servidor, que filtra. */
        FILTERED,

        /** El resolvedor no contestó y respondió el DNS del sistema, sin filtrar. */
        SYSTEM,

        /** Nadie contestó: se devuelve un fallo para que la app pruebe otra cosa. */
        FAILED,
    }
}

/**
 * Qué hacer con cada consulta (D5): preguntar al resolvedor del servidor y, si no contesta y se permite fallar abierto, al DNS del
 * sistema; si tampoco, un fallo de servidor. Si el resolvedor falla [FAILURES_BEFORE_SKIP] veces seguidas no se espera su tiempo máximo en cada
 * consulta: durante [retryAfterMs] se va directo al sistema y pasado ese tiempo se vuelve a probar. Un fallo suelto (un cambio de red)
 * no deja el teléfono sin filtrar.
 */
class DnsResolver(
    private val primary: UpstreamResolver,
    /** `null` si el servidor pidió fallar cerrado: sin resolvedor propio no se contesta con otro. */
    private val fallback: UpstreamResolver?,
    private val nowMs: () -> Long,
    private val retryAfterMs: Long = 10_000,
) {
    @Volatile
    private var primaryDownAt: Long? = null

    @Volatile
    private var failures = 0

    suspend fun answer(query: ByteArray): DnsAnswer {
        val downAt = primaryDownAt
        val skipPrimary = fallback != null && downAt != null && nowMs() - downAt < retryAfterMs
        if (!skipPrimary) {
            primary.resolve(query)?.let {
                primaryDownAt = null
                failures = 0
                return DnsAnswer(it, DnsAnswer.Source.FILTERED)
            }
            if (fallback != null && ++failures >= FAILURES_BEFORE_SKIP) primaryDownAt = nowMs()
        }
        fallback?.resolve(query)?.let { return DnsAnswer(it, DnsAnswer.Source.SYSTEM) }
        return DnsAnswer(DnsMessages.servfail(query), DnsAnswer.Source.FAILED)
    }
}

private const val FAILURES_BEFORE_SKIP = 3

/** De un paquete leído del túnel a la respuesta que se escribe de vuelta: solo se contestan las consultas DNS por UDP. */
class DnsProxy(
    private val resolver: DnsResolver,
    private val onAnswer: (DnsAnswer.Source) -> Unit = {},
    /** Cada consulta con su respuesta, para llevar el historial (D8). */
    private val onQuery: (query: ByteArray, answer: DnsAnswer) -> Unit = { _, _ -> },
) {

    /** La consulta DNS que lleva el paquete, o `null` si es otra cosa (no se contesta). */
    fun accept(packet: ByteArray, length: Int): UdpDatagram? =
        UdpPackets.parse(packet, length)?.takeIf { it.destinationPort == UdpPackets.DNS_PORT && DnsMessages.isQuery(it.payload) }

    /** Los paquetes de la respuesta (más de uno si es grande y hay que fragmentarla). */
    suspend fun respond(request: UdpDatagram): List<ByteArray> {
        val answer = resolver.answer(request.payload)
        onAnswer(answer.source)
        onQuery(request.payload, answer)
        return UdpPackets.reply(request, answer.bytes, identification = DnsMessages.id(answer.bytes))
    }
}
