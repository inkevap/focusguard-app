package com.focusguard.model

import java.time.Instant

/** Lo que el servicio de accesibilidad ve en este momento. */
data class ForegroundState(
    val packageName: String,
    /** Dirección leída de la barra de un navegador; null si no hay o no es un navegador. */
    val url: String? = null,
    val isBrowser: Boolean = false,
)

enum class BlockReason {
    /** La app está en la lista de un perfil activo en modo bloquear. */
    PROFILE_APP,

    /** La web coincide con la lista de un perfil activo en modo bloquear. */
    PROFILE_WEB,

    /** Perfil activo en modo permitir solo esto, y lo abierto no está en la lista. */
    NOT_ALLOWED,

    /** El límite de uso del perfil se agotó. */
    LIMIT_REACHED,

    /** Pantalla del sistema que el modo estricto protege. */
    STRICT_MODE,

    /** Fecha y hora: moverlas acorta esperas, así que se protegen con aprobaciones activadas o con el modo estricto. */
    CLOCK_PROTECTED,

    /** Sección de una app (Shorts, Reels…) bloqueada por un perfil; se sale con «atrás» y un aviso, sin pantalla de bloqueo. */
    SUB_APP,

    /** App instalada hace poco, bloqueada por el ajuste global de apps nuevas (A11). */
    NEW_APP,

    /** App que enseña una vista web propia (navegador integrado), bloqueada por el ajuste de navegadores dentro de apps (B8). */
    EMBEDDED_BROWSER,

    /** Contenido adulto: un dominio o palabras de las listas del servidor o del usuario (D2, D3). */
    ADULT_CONTENT,

    /** Contenido explícito visto en una captura de pantalla por la detección de imágenes. */
    IMAGE_CONTENT,

    /** Los ajustes del administrador del dispositivo: desactivarlo permitiría desinstalar la app, así que pide permiso aunque no haya modo estricto. */
    ADMIN_PROTECTED,
    ;

    /** El bloqueo viene de un perfil del usuario (no de una protección del sistema): admite mensaje propio y desbloqueo de emergencia. */
    val fromProfile: Boolean get() = this == PROFILE_APP || this == PROFILE_WEB || this == NOT_ALLOWED || this == LIMIT_REACHED
}

sealed interface BlockDecision {
    data object Allow : BlockDecision

    data class Block(
        val reason: BlockReason,
        val profileId: Long,
        /** Paquete o dominio que provocó el bloqueo, para mostrarlo en la pantalla de bloqueo. */
        val target: String,
        /** Cuándo se reinicia el límite agotado (G6); null si no se sabe. */
        val availableAt: Instant? = null,
        /** [target] es un paquete (no un dominio): lo sabe quien decide, y la pantalla de bloqueo lo usa para reportar un bloqueo erróneo. */
        val targetIsApp: Boolean = false,
        /** Los demás perfiles que bloquean lo mismo a la vez, en orden de id; [profileId] es el primero. La pantalla de bloqueo los nombra. */
        val otherProfileIds: List<Long> = emptyList(),
        /** Con qué coincidió lo que se bloqueó (por ahora, las palabras o el dominio del filtro adulto): la pantalla de bloqueo lo enseña para que no parezca arbitrario. Solo se queda en el teléfono. */
        val detail: String? = null,
    ) : BlockDecision
}
