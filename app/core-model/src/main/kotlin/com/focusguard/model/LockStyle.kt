package com.focusguard.model

/**
 * Cómo se ve la pantalla de bloqueo (M1, M2, M4). [message] sustituye al texto del motivo y [appMessages]
 * lo sustituye para una app concreta (por paquete). Los colores se eligen entre paletas con contraste comprobado.
 */
data class LockStyle(
    val message: String = "",
    val paletteId: String = LockPalettes.DEFAULT_ID,
    val emoji: String = "",
    /** Muestra una frase motivadora distinta cada vez. */
    val rotating: Boolean = false,
    val appMessages: Map<String, String> = emptyMap(),
    /** Frase propia que se suma al motivo cuando el modo estricto cierra los ajustes para que no se apague ni desinstale la app (M2). */
    val guardMessage: String = "",
) {
    /**
     * El mensaje propio que toca mostrar, o null para usar el texto del motivo. El de una app concreta gana al general.
     * Solo sustituyen al texto cuando el bloqueo viene de un perfil: el modo estricto y la protección de la hora
     * siempre explican por qué se cierra la pantalla.
     */
    fun messageFor(reason: BlockReason, target: String): String? {
        if (!reason.fromProfile) return null
        return appMessages[target]?.takeIf { it.isNotBlank() } ?: message.takeIf { it.isNotBlank() }
    }

    /** La frase propia de un intento de apagar o desinstalar la app; se muestra junto al motivo, que nunca se sustituye. */
    fun guardNoteFor(reason: BlockReason, target: String): String? =
        guardMessage.takeIf { it.isNotBlank() && reason == BlockReason.STRICT_MODE && GuardTargets.isGuardAttempt(target) }

    /** Lo escrito de más se recorta y los mensajes vacíos se descartan. */
    fun clamped() = copy(
        message = message.takeChars(MAX_MESSAGE),
        guardMessage = guardMessage.takeChars(MAX_MESSAGE),
        emoji = emoji.takeChars(MAX_EMOJI),
        appMessages = appMessages.filterValues { it.isNotBlank() }.mapValues { it.value.takeChars(MAX_MESSAGE) },
    )

    companion object {
        const val MAX_MESSAGE = 140
        const val MAX_EMOJI = 8

        /** Recorta por caracteres reales: no parte un emoji por la mitad. */
        fun String.takeChars(max: Int): String {
            val count = codePointCount(0, length)
            return if (count <= max) this else substring(0, offsetByCodePoints(0, max))
        }
    }
}

/** Fondo y texto de la pantalla de bloqueo, como colores ARGB. */
data class LockPalette(val id: String, val label: String, val background: Int, val text: Int)

object LockPalettes {
    const val DEFAULT_ID = "guard"

    val all = listOf(
        LockPalette(DEFAULT_ID, "Guardia", 0xFF06383A.toInt(), 0xFFFFFFFF.toInt()),
        LockPalette("night", "Noche", 0xFF14224F.toInt(), 0xFFFFFFFF.toInt()),
        LockPalette("forest", "Bosque", 0xFF12372A.toInt(), 0xFFFFFFFF.toInt()),
        LockPalette("wine", "Vino", 0xFF4A1530.toInt(), 0xFFFFFFFF.toInt()),
        LockPalette("graphite", "Grafito", 0xFF1F2328.toInt(), 0xFFFFFFFF.toInt()),
        LockPalette("sand", "Arena", 0xFFF3E9D2.toInt(), 0xFF2B2418.toInt()),
        LockPalette("sky", "Cielo", 0xFFDCEBFA.toInt(), 0xFF10264A.toInt()),
    )

    fun byId(id: String): LockPalette = all.firstOrNull { it.id == id } ?: all.first()
}

/** Frases para la pantalla de bloqueo (M4). */
object LockMotivation {
    val phrases = listOf(
        "Cada minuto que aguantas es uno que ganas.",
        "Lo que buscas ahora no se va a ir, tu tiempo sí.",
        "Respira. Esto también pasa.",
        "Tú elegiste este bloqueo por una buena razón.",
        "Un ratito sin pantalla y vuelves con más energía.",
        "No hace falta decidir otra vez: ya lo decidiste.",
        "Hoy cuentas contigo.",
        "Lo importante te está esperando.",
        "Mira a tu alrededor un momento.",
        "Pequeños hábitos, grandes cambios.",
        "Menos desplazar, más vivir.",
        "Tu atención vale mucho: no la regales.",
        "Ya has aguantado antes, puedes hacerlo otra vez.",
        "Haz algo que te haga sentir orgulloso esta noche.",
        "Un paso corto hoy, un hábito largo mañana.",
        "No tienes que hacerlo perfecto, solo seguir.",
    )

    fun pick(index: Int): String = phrases[Math.floorMod(index, phrases.size)]
}
