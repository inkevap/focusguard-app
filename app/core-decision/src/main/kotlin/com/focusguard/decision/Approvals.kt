package com.focusguard.decision

import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalProgress
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.ApprovalStatus
import com.focusguard.model.RemoteStatus
import java.time.Duration
import java.time.Instant

object Approvals {

    const val DEFAULT_STEPS = 250

    /** Códigos de acción compartidos con el servidor (TABLAS_Y_ALGORITMO sección 7). */
    const val ACTION_ADULT_BLOCK = 1
    const val ACTION_REMOVE_BLOCKED_APP = 1001
    const val ACTION_REMOVE_BLOCKED_WEB = 1002
    const val ACTION_REMOVE_ALLOWED_APP = 1003
    const val ACTION_REMOVE_ALLOWED_WEB = 1004

    /** Códigos locales sin equivalente en la tabla del servidor todavía. */
    const val ACTION_PROFILE_OFF = 2001
    const val ACTION_PROFILE_DELETE = 2002
    const val ACTION_APPROVAL_MODE = 2006
    const val ACTION_SESSION_END = 2007
    const val ACTION_DAY_START = 2008

    /** Cerrar la sesión de la cuenta con un compañero activo: callaría sus avisos. */
    const val ACTION_SIGN_OUT = 2016
    const val ACTION_PARTNER_NOTICES_OFF = 2017
    const val ACTION_ADULT_WORD_REMOVE = 2018
    const val ACTION_ADULT_ALLOW = 2019
    const val ACTION_SERVER_CHANGE = 2020
    const val ACTION_DNS_OFF = 2021
    const val ACTION_DNS_EXCLUDE = 2022
    const val ACTION_STREAK_RESTORE = 2023
    const val ACTION_LAUNCHER_ICON = 2024
    const val ACTION_PARENTAL_OFF = 2025
    const val ACTION_URGE_SHARING_OFF = 2026

    /** Salir del modo estricto (pausarlo o desactivarlo) con el compañero como método de salida. */
    const val ACTION_STRICT_EXIT = 2027

    /** Desactivar el administrador del dispositivo: abre una ventana en la que se puede, y con él desactivado se puede desinstalar la app. */
    const val ACTION_ADMIN_OFF = 2028
    const val ACTION_IMAGE_SENSITIVITY = 2029
    const val ACTION_IMAGE_PROBE_OFF = 2030
    const val ACTION_IMAGE_VIGILANCE = 2031
    const val ACTION_IMAGE_ADAPTIVE = 2032
    const val ACTION_ADULT_TEXT_EXEMPT = 2033

    /** Quitar el candado de un perfil con el compañero como método de salida. */
    const val ACTION_PROFILE_UNLOCK = 2034

    /** Quitar un aviso o una protección del panel del compañero. */
    const val ACTION_PARTNER_PANEL = 2035

    /** Textos largos de literatura clásica en español (dominio público), para teclear a mano. */
    val PHRASES = listOf(
        "En un lugar de la Mancha, de cuyo nombre no quiero acordarme, no ha mucho tiempo que vivía un hidalgo de los de lanza en astillero, adarga antigua, rocín flaco y galgo corredor.",
        "Tanto se enfrascó en su lectura, que se le pasaban las noches leyendo de claro en claro, y los días de turbio en turbio; y así, del poco dormir y del mucho leer, se le secó el cerebro.",
        "La libertad, Sancho, es uno de los más preciosos dones que a los hombres dieron los cielos; con ella no pueden igualarse los tesoros que encierra la tierra ni el mar encubre.",
        "Pensar que en esta vida las cosas han de durar siempre en un estado es error de poca prudencia; antes parece que todo anda en redondo, quiero decir, a la redonda.",
    )

    /**
     * Una solicitud existente solo se reutiliza si pide exactamente lo mismo. Si cambia el contenido,
     * la espera ganada por un cambio no puede gastarse en otro más grande: se empieza de cero.
     */
    fun reusable(pending: List<ApprovalRequest>, actionKey: String, payload: String?): ApprovalRequest? =
        // Una solicitud que el compañero rechazó o que caducó no se reutiliza: pedirlo otra vez es una solicitud nueva.
        pending.firstOrNull { it.actionKey == actionKey && it.payload == payload && !finishedWithoutApproval(it) }

    private fun finishedWithoutApproval(request: ApprovalRequest) =
        request.mode == ApprovalMode.PARTNER && (request.remote == RemoteStatus.REJECTED || request.remote == RemoteStatus.EXPIRED)

    /**
     * Cuándo se le puede mandar al compañero una solicitud que él tiene que aprobar, según el modo elegido en las aprobaciones:
     * con una espera (24 h o 72 h) se aguarda antes de mandarle el correo; con cualquier otro modo se le manda ya.
     */
    fun partnerSendAfter(configured: ApprovalMode, createdAt: Instant): Instant? = when (configured) {
        ApprovalMode.DELAY_24H -> createdAt.plus(Duration.ofHours(24))
        ApprovalMode.DELAY_72H -> createdAt.plus(Duration.ofHours(72))
        else -> null
    }

    fun phrase(index: Int): String = PHRASES[Math.floorMod(index, PHRASES.size)]

    fun evaluate(request: ApprovalRequest, progress: ApprovalProgress, now: Instant): ApprovalStatus = when (request.mode) {
        ApprovalMode.NONE -> ApprovalStatus.Ready
        ApprovalMode.DELAY_24H -> delay(request, hours = 24, now = now)
        ApprovalMode.DELAY_72H -> delay(request, hours = 72, now = now)
        ApprovalMode.LONG_PHRASE ->
            if (matches(progress.typed, phrase(request.phraseIndex))) ApprovalStatus.Ready else ApprovalStatus.NeedPhrase
        ApprovalMode.STEPS ->
            if (progress.steps >= request.stepsNeeded) {
                ApprovalStatus.Ready
            } else {
                ApprovalStatus.Walking(progress.steps.coerceAtLeast(0), request.stepsNeeded)
            }
        ApprovalMode.PARTNER -> when (request.remote) {
            RemoteStatus.NOT_SENT ->
                request.sendAfter?.takeIf { now.isBefore(it) }?.let(ApprovalStatus::HoldingBeforePartner) ?: ApprovalStatus.WaitingPartner(sent = false)
            RemoteStatus.WAITING -> ApprovalStatus.WaitingPartner(sent = true)
            RemoteStatus.APPROVED -> ApprovalStatus.Ready
            RemoteStatus.REJECTED -> ApprovalStatus.PartnerRejected
            RemoteStatus.EXPIRED -> ApprovalStatus.PartnerExpired
        }
    }

    /** Texto de la solicitud para quien la lee: el usuario en Aprobaciones y el compañero en el correo. */
    fun describe(actionKey: String): String = when {
        actionKey == "adult_off" -> "Apagar el bloqueo de contenido adulto"
        actionKey == "approval_mode" -> "Cambiar el modo de aprobación"
        actionKey == "day_start" -> "Cambiar el inicio del día"
        actionKey == "new_apps" -> "Quitar o aflojar el bloqueo de apps recién instaladas"
        actionKey == "embedded_browsers" -> "Quitar o aflojar el bloqueo de navegadores dentro de apps"
        actionKey == "fold_floating" -> "Dejar de cerrar las ventanas flotantes"
        actionKey == "dns_off" -> "Apagar el filtro DNS (deja de aplicarse a todo el teléfono)"
        actionKey == "dns_exclude" -> "Dejar una app fuera del filtro DNS"
        actionKey == "adult_allow" -> "Dejar pasar algo del filtro de contenido adulto"
        actionKey == "urge_sharing_off" -> "Dejar de enseñarte el resumen semanal de sus impulsos"
        actionKey == "parental_off" -> "Quitar el modo parental (los cambios dejarían de pasar por ti)"
        actionKey == "launcher_icon" -> "Cambiar el icono y el nombre de la app en el teléfono (la disimula)"
        actionKey.startsWith("streak_restore:") -> "Restaurar un día perdido de tu racha"
        actionKey == "server_change" -> "Cambiar el servidor de FocusGuard (de él salen las listas de bloqueo)"
        actionKey == "adult_word_remove" -> "Quitar una palabra o web de tu lista de contenido adulto"
        actionKey == "partner_panel" -> "Mandarle menos avisos o quitar protecciones que valían con compañero (lo que cambie dejaría de vigilarse)"
        actionKey == "partner_notices_off" -> "Dejar de enviarte avisos de lo que pasa en el teléfono"
        actionKey.startsWith("profile_unlock:") -> "Quitar el candado de un perfil (dejaría de estar protegido)"
        actionKey == "admin_off" -> "Poder desactivar el administrador del dispositivo o la accesibilidad (con ellos desactivados se podría desinstalar FocusGuard)"
        actionKey == "adult_text_exempt" -> "Dejar de leer el texto de una app en el filtro de contenido adulto (lo que ponga ahí no se revisará)"
        actionKey == "image_adaptive" -> "Reducir el modo dinámico de la detección de imágenes (apagarlo, acortar el periodo o bajar el nivel máximo)"
        actionKey == "image_vigilance" -> "Vigilar menos tras detectar contenido explícito (acortar, bajar la intensidad o apagar la vigilancia)"
        actionKey == "image_probe_off" -> "Apagar la detección de imágenes (dejaría de bloquear lo explícito en pantalla)"
        actionKey == "image_sensitivity" -> "Bajar la sensibilidad de la detección de imágenes (se marcaría menos)"
        actionKey == "sign_out" -> "Cerrar la sesión de la cuenta en este teléfono (dejaría de enviarte avisos)"
        actionKey.startsWith("strict_exit:pause:") -> "Pausar el modo estricto ${actionKey.substringAfterLast(':')} minutos (se salta tus reglas durante ese tiempo)"
        actionKey.startsWith("strict_exit:") -> "Desactivar el modo estricto (dejarían de protegerse tus reglas)"
        actionKey.startsWith("profile_off:") -> "Apagar un perfil"
        actionKey.startsWith("profile_delete:") -> "Borrar un perfil"
        actionKey.startsWith("profile_edit:") -> "Aplicar cambios que debilitan un perfil"
        actionKey.startsWith("profile_pause:") -> "Pausar un perfil"
        actionKey.startsWith("session_end:") -> "Terminar un bloqueo antes de tiempo"
        else -> "Debilitar una protección"
    }

    private const val MAX_CHARS_PER_CHANGE = 12

    /** Pegar mete de golpe mucho texto y teclear no: se ignora el cambio que añade demasiado de una vez. */
    fun isTypedChange(previous: String, new: String): Boolean {
        // Lo que cambió es lo que queda entre el principio y el final comunes: pegar un bloque sobre una selección también cuenta.
        val prefix = previous.commonPrefixWith(new).length
        val suffix = previous.substring(prefix).commonSuffixWith(new.substring(prefix)).length
        return new.length - prefix - suffix <= MAX_CHARS_PER_CHANGE
    }

    /** Igual que el texto original salvo espacios sobrantes; respeta mayúsculas y acentos a propósito. */
    fun matches(typed: String, phrase: String): Boolean = normalize(typed) == normalize(phrase)

    private fun normalize(text: String) = text.trim().replace(Regex("\\s+"), " ")

    private fun delay(request: ApprovalRequest, hours: Long, now: Instant): ApprovalStatus {
        val at = request.createdAt.plus(Duration.ofHours(hours))
        return if (now.isBefore(at)) ApprovalStatus.WaitingUntil(at) else ApprovalStatus.Ready
    }

    /**
     * Pasos recorridos desde que empezó la solicitud. El contador del sensor es acumulado
     * desde el último arranque: si bajó respecto al valor inicial, el teléfono se reinició y
     * lo contado ya es el total actual.
     */
    fun stepsSince(baseline: Long, current: Long): Int =
        (if (current >= baseline) current - baseline else current).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
