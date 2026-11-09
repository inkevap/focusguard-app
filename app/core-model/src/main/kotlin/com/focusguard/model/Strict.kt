package com.focusguard.model

import java.time.Instant

/** Cómo se nombra en el bloqueo lo que se cierra además de las pantallas de [StrictProtection]. */
object GuardTargets {
    /** Un intento de desinstalar la app: la pantalla de ajustes ofrece «Desinstalar» o pide confirmarlo. */
    const val UNINSTALL = "desinstalar la app"

    /** Un intento de desactivar el administrador del dispositivo (con él activo, Android no deja desinstalar la app). */
    const val ADMIN = "desactivar el administrador del dispositivo"

    /** Un intento de apagar el servicio de accesibilidad (sin él no se cierra ninguna otra pantalla protegida, y se podría desactivar el administrador y desinstalar). */
    const val ACCESSIBILITY = "apagar la accesibilidad"

    /** ¿Es un intento de apagar o desinstalar la propia app (los ajustes protegidos)? */
    fun isGuardAttempt(target: String) = target == StrictProtection.SETTINGS_GUARD.screenName || target == UNINSTALL
}

enum class StrictProtection {
    /** No editar perfiles ni listas mientras el bloqueo está activo. */
    EDIT_LOCK,

    /** Cerrar las pantallas de ajustes que permiten desactivar o desinstalar la app. */
    SETTINGS_GUARD,

    /** Cerrar la pantalla de apps recientes. */
    RECENTS,

    /** Impedir la pantalla dividida con una app bloqueada. */
    SPLIT_SCREEN,

    /** Cerrar el menú de apagado y reinicio (A19, H6). */
    POWER_MENU,

    /** Cerrar el panel de notificaciones y los ajustes rápidos (A18). */
    NOTIFICATION_SHADE,

    /** Cerrar los diálogos de compra dentro de apps (A15): una compra puede deshacer lo que el bloqueo protege. */
    PURCHASE_DIALOGS,

    /** Cerrar el administrador de tareas en segundo plano y el cambio de usuario (A17): otro perfil se salta el bloqueo. */
    TASK_MANAGER,

    /** Cerrar el modo de ahorro extremo de batería (A20): apaga servicios en segundo plano, entre ellos el bloqueo. */
    BATTERY_SAVER,
    ;

    /** Cómo se nombra la pantalla cerrada en el aviso de bloqueo. */
    val screenName: String
        get() = when (this) {
            EDIT_LOCK, SETTINGS_GUARD -> "ajustes"
            RECENTS -> "apps recientes"
            SPLIT_SCREEN -> "pantalla dividida"
            POWER_MENU -> "menú de apagado"
            NOTIFICATION_SHADE -> "panel de notificaciones"
            PURCHASE_DIALOGS -> "compras"
            TASK_MANAGER -> "administrador de tareas"
            BATTERY_SAVER -> "ahorro extremo de batería"
        }
}

/** Forma de salir del modo estricto o de pausarlo. Si hay varias, hacen falta todas. */
sealed interface ExitMethod {
    /** Esperar un tiempo fijo desde que se pide. */
    data class Timer(val minutes: Int) : ExitMethod

    /** Solo se puede cuando termina el horario del modo estricto. */
    data object FollowSchedule : ExitMethod

    /** Tener el teléfono conectado al cargador. */
    data object Charger : ExitMethod

    /** Introducir el PIN. */
    data object Pin : ExitMethod

    /** Pedirlo y esperar; la concesión vale [GRANT_WINDOW_MINUTES] minutos y luego hay que pedirlo de nuevo. */
    data class Cooldown(val minutes: Int) : ExitMethod

    /** Que el compañero lo apruebe: al pedir la salida le llega un correo y hasta que responda no se concede. */
    data object Partner : ExitMethod

    companion object {
        const val GRANT_WINDOW_MINUTES = 10
    }
}

data class StrictConfig(
    val enabled: Boolean = false,
    /** Sin intervalos, el modo estricto está activo siempre que esté habilitado. */
    val intervals: List<ScheduleInterval> = emptyList(),
    val protections: Set<StrictProtection> = emptySet(),
    val exitMethods: List<ExitMethod> = emptyList(),
    val pausedUntil: Instant? = null,
)

enum class StrictState {
    /** No configurado o desactivado. */
    INACTIVE,

    /** Configurado, fuera de su horario. */
    WAITING,

    ACTIVE,

    /** Pausa vigente hasta [StrictConfig.pausedUntil]. */
    PAUSED,
}

sealed interface StrictAction {
    data class Pause(val minutes: Int) : StrictAction

    data object Disable : StrictAction
}

data class ExitRequest(
    val action: StrictAction,
    val requestedAt: Instant,
    /** El compañero ya aprobó esta solicitud de salida (solo cuenta para [ExitMethod.Partner]). */
    val partnerApproved: Boolean = false,
)

/** Lo que el teléfono sabe ahora mismo, para comprobar los métodos de salida. */
data class ExitContext(
    val charging: Boolean,
    /** Null si todavía no se ha introducido el PIN. */
    val pinCorrect: Boolean?,
    /** Hay un compañero vinculado y activo que puede aprobar. */
    val partnerLinked: Boolean = false,
)

sealed interface ExitRequirement {
    data class WaitUntil(val at: Instant) : ExitRequirement

    data object NeedCharger : ExitRequirement

    data object NeedPin : ExitRequirement

    data object WaitForScheduleEnd : ExitRequirement

    /** La concesión del cooldown caducó: hay que pedirlo de nuevo. */
    data object RequestExpired : ExitRequirement

    /** El compañero aún no ha aprobado la salida. */
    data object NeedPartner : ExitRequirement

    /** Se pide la aprobación del compañero pero ya no hay ninguno vinculado: no se puede conceder hasta vincular uno. */
    data object NoPartner : ExitRequirement
}

data class ExitStatus(val satisfied: Boolean, val pending: List<ExitRequirement>)

/**
 * Candado de un solo perfil: mientras está puesto el perfil no se apaga, pausa, borra ni edita, aunque el modo estricto no esté activo.
 * Se quita cumpliendo [exitMethods] (todos, como en el modo estricto; «seguir horarios» no vale aquí).
 */
data class ProfileLock(val exitMethods: List<ExitMethod>)
