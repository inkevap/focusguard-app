package com.focusguard.dns

/** Cuántas veces se consultó [host] y si se bloqueó, con la última vez (ms). */
data class DnsCount(val host: String, val blocked: Boolean, val count: Int, val lastMs: Long)

/** Lo que dura el historial de consultas en el teléfono (D8). */
object DnsLogPolicy {
    const val RETENTION_DAYS = 30L

    /** El primer día que se conserva si hoy es [today] (días desde 1970): los anteriores se borran. */
    fun firstDayToKeep(today: Long): Long = today - RETENTION_DAYS + 1
}

/**
 * Cuenta las consultas DNS en memoria hasta que alguien las vuelque al disco: en un teléfono pasan cientos por minuto y escribir cada una
 * gastaría batería. Agrupa por dominio y por bloqueada o permitida. Con demasiados dominios distintos sin volcar, los nuevos se cuentan
 * juntos como [OTHERS]: se pierde el nombre pero no la cuenta, que es lo que ve el compañero.
 */
class DnsTally(private val maxKeys: Int = 5_000) {
    private data class Key(val host: String, val blocked: Boolean)

    private class Entry(var count: Int, var lastMs: Long)

    private val entries = HashMap<Key, Entry>()

    @Synchronized
    fun add(host: String, blocked: Boolean, atMs: Long) {
        record(Key(host, blocked), 1, atMs)
    }

    private fun record(key: Key, count: Int, atMs: Long) {
        val known = entries[key] ?: if (entries.size < maxKeys) null else entries.getOrPut(Key(OTHERS, key.blocked)) { Entry(0, atMs) }
        if (known != null) {
            known.count += count
            known.lastMs = maxOf(known.lastMs, atMs)
        } else {
            entries[key] = Entry(count, atMs)
        }
    }

    /** Devuelve lo que no se pudo guardar para que se cuente en el siguiente volcado. */
    @Synchronized
    fun restore(counts: List<DnsCount>) {
        counts.forEach { record(Key(it.host, it.blocked), it.count, it.lastMs) }
    }

    companion object {
        /** Los dominios que no cupieron, juntos. */
        const val OTHERS = "(otros dominios)"
    }

    val isEmpty: Boolean @Synchronized get() = entries.isEmpty()

    /** Lo contado hasta ahora; vuelve a empezar de cero. */
    @Synchronized
    fun drain(): List<DnsCount> = entries.map { (key, e) -> DnsCount(key.host, key.blocked, e.count, e.lastMs) }.also { entries.clear() }
}

/** Lo que el compañero ve del DNS (D8): solo cuántas consultas se bloquearon y se permitieron un día, nunca qué dominios. */
object DnsSummary {
    /** La etiqueta `bloqueadas,permitidas` del día [day], o `null` si ese día ya se contó (hasta [lastReportedDay]) o no hubo consultas. */
    fun due(day: Long, lastReportedDay: Long?): Boolean = lastReportedDay == null || day > lastReportedDay

    fun label(day: Long, lastReportedDay: Long?, blocked: Int, allowed: Int): String? =
        if (!due(day, lastReportedDay) || blocked + allowed == 0) null else "$blocked,$allowed"
}
