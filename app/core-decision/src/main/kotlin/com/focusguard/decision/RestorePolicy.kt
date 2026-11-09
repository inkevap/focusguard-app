package com.focusguard.decision

import com.focusguard.model.ExitMethod
import com.focusguard.model.LockStyle
import com.focusguard.model.Profile
import com.focusguard.model.StrictConfig

/** Los ajustes que viajan en una copia. `null` en un campo opcional = la copia no lo traía. */
data class BackupSettings(
    val dayStartMinute: Int? = null,
    val adultBlock: Boolean = false,
    val adultWords: Set<String> = emptySet(),
    val newApps: NewAppsPolicy? = null,
    val embeddedBrowsers: EmbeddedBrowserPolicy? = null,
    val foldFloating: Boolean = false,
)

/** Una copia de seguridad ya leída y validada, sin depender de cómo se guarde (archivo o servidor). */
data class BackupSnapshot(
    val settings: BackupSettings = BackupSettings(),
    val lockStyle: LockStyle? = null,
    val strict: StrictConfig? = null,
    val profiles: List<Profile> = emptyList(),
)

/** Cómo está este teléfono ahora, para decidir qué de la copia se puede aplicar sin aflojar nada. */
data class RestoreState(
    val approvalRequired: Boolean,
    /** Si en este teléfono ya hay un PIN del modo estricto (el PIN no viaja en la copia). */
    val hasPin: Boolean,
    val newApps: NewAppsPolicy,
    val embeddedBrowsers: EmbeddedBrowserPolicy,
    val lockStyle: LockStyle,
    val strict: StrictConfig,
    /** Si en este teléfono hay un compañero vinculado y activo (la vinculación no viaja en la copia). */
    val partnerLinked: Boolean = false,
)

/** Lo que de verdad se aplica de una copia: nada de aquí debilita una protección. */
data class RestorePlan(
    val dayStartMinute: Int?,
    val enableAdultBlock: Boolean,
    val enableFoldFloating: Boolean,
    val addAdultWords: Set<String>,
    val newApps: NewAppsPolicy?,
    val embeddedBrowsers: EmbeddedBrowserPolicy?,
    val lockStyle: LockStyle?,
    val strict: StrictConfig?,
    /** El modo estricto de la copia pide PIN para salir y aquí no hay: no se trae (se quedaría sin salida). Hay que configurarlo de nuevo. */
    val strictNeedsPin: Boolean,
    /** El modo estricto de la copia pide la aprobación de un compañero y aquí no hay: no se trae (se quedaría sin salida). Hay que vincularlo y configurarlo de nuevo. */
    val strictNeedsPartner: Boolean = false,
)

/**
 * Restaurar una copia nunca debilita (N2, N3, H8). Los interruptores solo se encienden; las políticas (apps nuevas, navegadores
 * integrados) solo si no son más flojas que las de ahora; el inicio del día no se toca con las aprobaciones activadas (moverlo puede
 * levantar un límite agotado); la pantalla de bloqueo y el modo estricto solo si en este teléfono siguen como vienen de fábrica.
 * Los perfiles de la copia se añaden siempre como nuevos (eso lo hace quien aplica el plan).
 */
object RestorePolicy {
    fun plan(incoming: BackupSnapshot, current: RestoreState): RestorePlan {
        val settings = incoming.settings
        val strict = incoming.strict?.takeIf { current.strict == StrictConfig() }?.copy(pausedUntil = null)
        val strictNeedsPin = strict != null && !current.hasPin && strict.exitMethods.any { it is ExitMethod.Pin }
        val strictNeedsPartner = strict != null && !current.partnerLinked && ExitMethod.Partner in strict.exitMethods
        return RestorePlan(
            dayStartMinute = settings.dayStartMinute.takeUnless { current.approvalRequired },
            enableAdultBlock = settings.adultBlock,
            enableFoldFloating = settings.foldFloating,
            addAdultWords = settings.adultWords,
            newApps = settings.newApps?.takeUnless { Weakening.newAppsWeakened(current.newApps, it) },
            embeddedBrowsers = settings.embeddedBrowsers?.takeUnless { Weakening.embeddedBrowsersWeakened(current.embeddedBrowsers, it) },
            lockStyle = incoming.lockStyle?.takeIf { current.lockStyle == LockStyle() },
            // Sin la pausa guardada: es de la hora de aquel teléfono. El PIN no viaja: con un modo que lo pide para salir y sin PIN aquí, no se trae.
            strict = strict.takeUnless { strictNeedsPin || strictNeedsPartner },
            strictNeedsPin = strictNeedsPin,
            strictNeedsPartner = strictNeedsPartner,
        )
    }
}
