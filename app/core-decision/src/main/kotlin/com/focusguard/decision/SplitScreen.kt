package com.focusguard.decision

/** Qué clase de ventana es, según lo que informa el sistema de accesibilidad. */
enum class WindowKind {
    /** Ventana de una app (`TYPE_APPLICATION`). */
    APP,

    /** El divisor que arrastra el usuario entre las dos apps (`TYPE_SPLIT_SCREEN_DIVIDER`). */
    SPLIT_DIVIDER,

    /** Teclado, barras del sistema, diálogos y cualquier otra cosa. */
    OTHER,
}

/** Lo que el servicio recoge de cada ventana: el tipo, su rectángulo en pantalla y si está en imagen en imagen. */
data class WindowInfoLite(
    val kind: WindowKind,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val pictureInPicture: Boolean = false,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = width.toLong() * height
}

/**
 * Decide si hay una pantalla dividida de verdad (catálogo A16 y la protección del modo estricto).
 *
 * Contar «dos ventanas de app» no sirve: ocurre en las transiciones, con el teclado de algunos teléfonos y con visores como el de
 * estados de WhatsApp. Se exige una señal que solo existe con la división: el divisor del sistema o, si el teléfono no lo informa, dos
 * ventanas que reparten la pantalla sin taparse y ninguna a pantalla completa. Quien llama debe además esperar a que la situación dure
 * ([SplitScreenTracker]), para no actuar en mitad de una animación.
 */
object SplitScreen {
    /** Una ventana que ocupa más de esto de la pantalla es «pantalla completa» (hay barras y recortes que quitan algo). */
    const val FULL_SCREEN_RATIO = 0.90

    /** Cada mitad ha de tener al menos esto de la pantalla (un paño pequeño es una ventana flotante, no una división). */
    const val MIN_PANE_RATIO = 0.20

    /** Las dos juntas han de cubrir al menos esto de la pantalla. */
    const val MIN_COVERAGE_RATIO = 0.80

    /** Lo que pueden taparse entre sí, respecto de la menor: más es una ventana flotante encima de otra. */
    const val MAX_OVERLAP_RATIO = 0.05

    fun isSplit(windows: List<WindowInfoLite>, screenWidth: Int, screenHeight: Int): Boolean {
        if (windows.any { it.kind == WindowKind.SPLIT_DIVIDER && it.area > 0 }) return true
        val screenArea = screenWidth.toLong() * screenHeight
        if (screenArea <= 0) return false
        val apps = windows.filter { it.kind == WindowKind.APP && !it.pictureInPicture && it.area > 0 }
        // Con tres o más ventanas (flotantes, escritorio) no es la división de dos: queda para el divisor.
        if (apps.size != 2) return false
        val (a, b) = apps
        if (apps.any { it.area >= screenArea * FULL_SCREEN_RATIO }) return false
        if (apps.any { it.area < screenArea * MIN_PANE_RATIO }) return false
        if (overlap(a, b) > minOf(a.area, b.area) * MAX_OVERLAP_RATIO) return false
        return a.area + b.area >= screenArea * MIN_COVERAGE_RATIO
    }

    private fun overlap(a: WindowInfoLite, b: WindowInfoLite): Long {
        val w = minOf(a.right, b.right) - maxOf(a.left, b.left)
        val h = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return if (w > 0 && h > 0) w.toLong() * h else 0L
    }
}

/**
 * Exige que la división dure [holdMs] antes de darla por real. Cualquier lectura sin división reinicia la cuenta. Es puro: el
 * servicio le pasa la hora en cada consulta y programa una nueva ([remainingMs]) mientras la espera sigue en marcha.
 */
class SplitScreenTracker(private val holdMs: Long = HOLD_MS) {
    private var sinceMs: Long? = null

    /** Devuelve true cuando la división lleva al menos [holdMs] vista sin interrupción. */
    fun update(nowMs: Long, split: Boolean): Boolean {
        if (!split) {
            sinceMs = null
            return false
        }
        val since = sinceMs ?: nowMs.also { sinceMs = it }
        return nowMs - since >= holdMs
    }

    /** Lo que falta para confirmar, o null si no hay una división pendiente. */
    fun remainingMs(nowMs: Long): Long? = sinceMs?.let { (holdMs - (nowMs - it)).coerceAtLeast(0) }

    fun reset() {
        sinceMs = null
    }

    companion object {
        const val HOLD_MS = 400L
    }
}
