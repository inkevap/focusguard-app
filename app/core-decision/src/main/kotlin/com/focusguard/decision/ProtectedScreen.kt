package com.focusguard.decision

/**
 * Una ventana con FLAG_SECURE (las pestañas privadas de Brave o el incógnito de Chrome) sale **totalmente negra** en una captura de pantalla: no hay nada que puntuar.
 * Esta regla dice si una captura reducida es esa pantalla protegida. La barra de estado y la de navegación sí se ven (son ventanas aparte), así que solo se mira la
 * zona central; y se deja un pequeño margen ([MIN_DARK_SHARE]) por el suavizado al reducir la imagen.
 */
object ProtectedScreen {

    /** Cuánto de la zona central tiene que ser casi negro para darla por protegida. */
    const val MIN_DARK_SHARE = 0.97

    /** Un píxel es «casi negro» si ninguno de sus canales (rojo, verde, azul) pasa de este valor (de 0 a 255). */
    const val DARK_CHANNEL_MAX = 10

    /** La zona que se mide, como parte de la altura: se dejan fuera la barra de estado (arriba) y la de navegación (abajo). */
    private const val BAND_TOP = 0.10
    private const val BAND_BOTTOM = 0.90

    /** [pixels] son colores ARGB de Android, fila a fila, de una imagen de [width] × [height]. Una imagen vacía o con datos de menos nunca es protegida. */
    fun isProtected(pixels: IntArray, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return false
        val from = (height * BAND_TOP).toInt()
        val to = ((height * BAND_BOTTOM).toInt()).coerceAtLeast(from + 1).coerceAtMost(height)
        var dark = 0
        for (y in from until to) {
            val row = y * width
            for (x in 0 until width) {
                val p = pixels[row + x]
                if (((p shr 16) and 0xFF) <= DARK_CHANNEL_MAX && ((p shr 8) and 0xFF) <= DARK_CHANNEL_MAX && (p and 0xFF) <= DARK_CHANNEL_MAX) dark++
            }
        }
        return dark >= (to - from) * width * MIN_DARK_SHARE
    }
}

/**
 * Para no bloquear por una página negra de verdad ni por un fotograma suelto de una transición, solo se avisa tras [needed] capturas protegidas seguidas
 * del **mismo** paquete. Cualquier captura que no lo sea (o de otro paquete) empieza la cuenta de nuevo; al avisar también se vuelve a empezar.
 */
class ProtectedStreak(private val needed: Int = 2) {

    private var packageName: String? = null
    private var count = 0

    init {
        require(needed >= 1) { "hace falta al menos una captura" }
    }

    /** Apunta una captura de [pkg] y dice si ya hay que avisar. [pkg] null (no se sabe qué app es) nunca suma. */
    fun record(pkg: String?, isProtected: Boolean): Boolean {
        if (!isProtected || pkg == null) {
            clear()
            return false
        }
        count = if (pkg == packageName) count + 1 else 1
        packageName = pkg
        if (count < needed) return false
        clear()
        return true
    }

    fun clear() {
        packageName = null
        count = 0
    }
}
