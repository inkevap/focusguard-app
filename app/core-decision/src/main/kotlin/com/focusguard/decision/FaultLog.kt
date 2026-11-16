package com.focusguard.decision

/**
 * Un fallo apuntado en el registro de fallos: dónde pasó ([source]), qué pasó ([message]), cuántas veces se repitió ([count]) y cuándo fue la primera y la
 * última vez (hora del reloj, en milisegundos).
 */
data class Fault(val firstMs: Long, val lastMs: Long, val source: String, val message: String, val count: Int = 1)

/**
 * El registro de fallos de la app: se guarda en el teléfono para poder saber, días después, qué falló. Un fallo que se repite no ocupa una línea por vez:
 * suma a la suya y pasa al final. Se quedan los [MAX_ENTRIES] más recientes.
 */
object FaultLog {
    const val MAX_ENTRIES = 60
    const val MAX_TEXT = 400
    /** Cuántas líneas de la traza se guardan: las de la app primero, que son las que dicen dónde. */
    private const val TRACE_LINES = 3
    private const val APP_PACKAGE = "com.focusguard"

    /** Sin saltos ni tabuladores (separan los campos al guardar) y con un tope de largo. */
    fun clean(text: String): String = text.replace(WHITESPACE, " ").trim().take(MAX_TEXT)

    fun add(log: List<Fault>, nowMs: Long, source: String, message: String): List<Fault> {
        val s = clean(source)
        val m = clean(message)
        val previous = log.firstOrNull { it.source == s && it.message == m }
        val entry = previous?.copy(lastMs = nowMs, count = previous.count + 1) ?: Fault(nowMs, nowMs, s, m)
        return (log.filterNot { it === previous } + entry).takeLast(MAX_ENTRIES)
    }

    /**
     * El tipo de error, su mensaje y dónde saltó: las primeras líneas de la traza que son de la app (si no hay, las primeras de todas). Si tiene causa, se
     * añade también.
     */
    fun describe(error: Throwable): String = buildString {
        append(summary(error))
        val frames = error.stackTrace.filter { it.className.startsWith(APP_PACKAGE) }.ifEmpty { error.stackTrace.toList() }
        frames.take(TRACE_LINES).forEach { append(" | en ${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}") }
        error.cause?.takeIf { it !== error }?.let { append(" | causa: ${summary(it)}") }
    }

    private fun summary(error: Throwable): String = error::class.java.simpleName + (error.message?.let { ": $it" } ?: "")

    fun encode(log: List<Fault>): String = log.joinToString("\n") { "${it.firstMs}\t${it.lastMs}\t${it.count}\t${it.source}\t${it.message}" }

    fun decode(text: String): List<Fault> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('\t', limit = 5)
        if (parts.size < 5) return@mapNotNull null
        val first = parts[0].toLongOrNull() ?: return@mapNotNull null
        val last = parts[1].toLongOrNull() ?: return@mapNotNull null
        val count = parts[2].toIntOrNull() ?: return@mapNotNull null
        Fault(first, last, parts[3], parts[4], count)
    }.toList()

    private val WHITESPACE = Regex("[\\t\\r\\n]+")
}
