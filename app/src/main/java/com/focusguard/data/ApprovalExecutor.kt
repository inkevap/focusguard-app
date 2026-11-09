package com.focusguard.data

import com.focusguard.decision.AutoReactivation
import com.focusguard.dns.DnsFilter
import com.focusguard.decision.EmbeddedBrowserPolicy
import com.focusguard.decision.AdaptiveSettings
import com.focusguard.decision.ImageSensitivity
import com.focusguard.decision.VigilanceSettings
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.decision.ProfileLocks
import com.focusguard.decision.UninstallGuard
import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.ProfileKind
import com.focusguard.model.StrictAction
import java.time.LocalDate
import org.json.JSONObject

/** Aplica la acción de una solicitud ya aprobada. */
class ApprovalExecutor(
    private val profiles: ProfileRepository,
    private val usage: UsageRepository,
    private val settings: SettingsRepository,
    private val approvals: ApprovalRepository,
    private val guard: ChangeGuard,
    private val pauses: ProfilePauses,
    private val account: AccountRepository,
    private val clock: TrustedClock,
    private val dns: DnsFilter,
    private val streaks: StreakStore,
    private val icons: LauncherIconManager,
    private val strict: StrictRepository,
    private val profileLocks: ProfileLockRepository,
) {

    /**
     * Motivo por el que ahora no se puede aplicar [request] (modo estricto), para saberlo antes de gastar una aprobación. La salida del
     * propio modo estricto es la única que no se frena: es justo lo que el compañero aprueba, y no cambia nada por sí sola.
     */
    fun blockedReason(request: ApprovalRequest): String? =
        when {
            !isStrictExit(request.actionKey) && guard.isLocked() -> ChangeGuard.LOCKED
            ProfileLocks.targetOf(request.actionKey)?.let(guard::isProfileLocked) == true -> ChangeGuard.PROFILE_LOCKED
            else -> null
        }

    /** Devuelve un mensaje de error, o null si se aplicó. */
    suspend fun apply(request: ApprovalRequest): String? {
        blockedReason(request)?.let { return it }
        val key = request.actionKey
        when {
            // El compañero aprueba la salida: se anota en la solicitud de salida y el usuario la confirma cuando cumpla el resto de métodos.
            isStrictExit(key) -> if (!strict.markPartnerApproved(strictActionOf(key) ?: return "Solicitud incompleta.")) {
                approvals.remove(request.id)
                return "La solicitud de salida del modo estricto ya no existe: pídela de nuevo."
            }
            // El compañero aprueba quitar el candado de un perfil: el usuario lo confirma cuando cumpla el resto de métodos.
            key.startsWith(PREFIX_PROFILE_UNLOCK) -> if (!profileLocks.markPartnerApproved(key.substringAfter(':').toLongOrNull() ?: return "Solicitud incompleta.")) {
                approvals.remove(request.id)
                return "La solicitud de quitar el candado ya no existe: pídela de nuevo."
            }
            // Apagado con una aprobación: se enciende solo pasado un tiempo (D6).
            key == KEY_ADULT_OFF -> settings.turnAdultOffUntil(AutoReactivation.reactivateAt(clock.nowMs()))
            key == KEY_DNS_OFF -> dns.setEnabled(false)
            key == KEY_DNS_EXCLUDE -> {
                settings.addDnsExcludedApp(request.payload ?: return "Solicitud incompleta.")
                dns.restart()
            }
            key == KEY_ADULT_TEXT_EXEMPT -> settings.addAdultTextExemptApp(request.payload ?: return "Solicitud incompleta.")
            key == KEY_ADULT_ALLOW -> settings.addAdultAllowed(request.payload ?: return "Solicitud incompleta.")
            key == KEY_SERVER_CHANGE -> if (!account.changeServer(request.payload ?: return "Solicitud incompleta.")) return "No se pudo cambiar: cierra la sesión y comprueba la dirección."
            key == KEY_ADULT_WORD_REMOVE -> settings.removeAdultWord(request.payload ?: return "Solicitud incompleta.")
            key == KEY_APPROVAL_MODE -> approvals.setMode(ApprovalMode.valueOf(request.payload ?: return "Solicitud incompleta."))
            key == KEY_EMBEDDED -> applyDecoded(request, EmbeddedBrowserPolicy::decode, settings::setEmbeddedBrowsers)?.let { return it }
            key == KEY_IMAGE_ADAPTIVE -> applyDecoded(request, AdaptiveSettings::decode, settings::setImageAdaptive)?.let { return it }
            key == KEY_IMAGE_VIGILANCE -> applyDecoded(request, VigilanceSettings::decode, settings::setImageVigilance)?.let { return it }
            key == KEY_IMAGE_PROBE_OFF -> settings.setImageProbe(false)
            key == KEY_IMAGE_SENSITIVITY -> applyDecoded(request, ImageSensitivity::decode, settings::setImageSensitivity)?.let { return it }
            // Solo se pide aprobación para apagarlo: encenderlo es libre.
            key == KEY_FOLD_FLOATING -> settings.setFoldFloating(false)
            key == KEY_SIGN_OUT -> account.signOut()
            key == KEY_ADMIN_OFF -> settings.grantAdminOff(UninstallGuard.grantUntil(clock.nowMs()))
            key == KEY_PARTNER_NOTICES_OFF -> settings.setPartnerNotices(false)
            key == KEY_NEW_APPS -> applyDecoded(request, NewAppsPolicy::decode, settings::setNewApps)?.let { return it }
            key == KEY_DAY_START -> settings.setDayStartMinute(request.payload?.toIntOrNull() ?: return "Solicitud incompleta.")
            key == KEY_PARENTAL_OFF -> settings.setParentalMode(false)
            key == KEY_URGE_SHARING_OFF -> settings.setShareUrgeReport(false)
            key == KEY_LAUNCHER_ICON -> icons.set(LauncherIcon.entries.firstOrNull { it.name == request.payload } ?: return "Solicitud incompleta.")
            key.startsWith(PREFIX_STREAK_RESTORE) -> {
                val day = key.substringAfter(':').toLongOrNull()?.let(LocalDate::ofEpochDay)
                if (day == null || !streaks.restore(day)) {
                    approvals.remove(request.id)
                    return StreakStore.CANNOT_RESTORE
                }
            }
            key.startsWith("profile_edit:") -> {
                val edited = ProfileJson.fromJson(JSONObject(request.payload ?: return "Solicitud incompleta."))
                val current = profiles.get(edited.id)
                if (current == null) {
                    approvals.remove(request.id)
                    return "El perfil ya no existe."
                }
                profiles.save(current.withEditableFieldsFrom(edited))
            }
            key.startsWith("profile_pause:") -> {
                val id = key.substringAfter(':').toLongOrNull()
                if (id == null || request.payload?.toIntOrNull()?.let { pauses.pause(id, it) } != true) {
                    approvals.remove(request.id)
                    return "No se pudo pausar: el perfil ya no existe o la duración no es válida."
                }
            }
            key.startsWith("profile_off:") || key.startsWith("profile_delete:") || key.startsWith("session_end:") -> {
                val id = key.substringAfter(':').toLong()
                val target = profiles.get(id)
                if (target == null || !target.canEndEarly) {
                    approvals.remove(request.id)
                    return when {
                        target == null -> "El perfil ya no existe."
                        target.kind == ProfileKind.PANIC -> ChangeGuard.PANIC
                        else -> ChangeGuard.NO_EARLY_END
                    }
                }
                if (key.startsWith("profile_off:")) {
                    profiles.setEnabled(id, false)
                } else {
                    // Borrar un perfil y terminar una sesión son lo mismo: se quita el perfil y su uso.
                    profiles.delete(id)
                    usage.delete(id)
                }
            }
            else -> return "Acción desconocida."
        }
        approvals.remove(request.id)
        return null
    }

    /** Aplica la política que lleva la solicitud; si su texto no se entiende, la descarta y devuelve el motivo. */
    private inline fun <T : Any> applyDecoded(request: ApprovalRequest, decode: (String) -> T?, apply: (T) -> Unit): String? {
        val value = request.payload?.let(decode)
        if (value == null) {
            approvals.remove(request.id)
            return "Solicitud incompleta."
        }
        apply(value)
        return null
    }

    companion object {
        const val KEY_ADULT_OFF = "adult_off"
        const val KEY_ADULT_WORD_REMOVE = "adult_word_remove"
        const val KEY_ADULT_ALLOW = "adult_allow"
        const val KEY_DNS_OFF = "dns_off"
        const val KEY_DNS_EXCLUDE = "dns_exclude"
        const val KEY_ADULT_TEXT_EXEMPT = "adult_text_exempt"
        const val KEY_SERVER_CHANGE = "server_change"
        const val KEY_APPROVAL_MODE = "approval_mode"
        const val KEY_DAY_START = "day_start"
        const val KEY_NEW_APPS = "new_apps"
        const val KEY_EMBEDDED = "embedded_browsers"
        const val KEY_IMAGE_SENSITIVITY = "image_sensitivity"
        const val KEY_IMAGE_PROBE_OFF = "image_probe_off"
        const val KEY_IMAGE_VIGILANCE = "image_vigilance"
        const val KEY_IMAGE_ADAPTIVE = "image_adaptive"
        const val KEY_FOLD_FLOATING = "fold_floating"
        const val KEY_SIGN_OUT = "sign_out"
        const val KEY_ADMIN_OFF = "admin_off"
        const val KEY_LAUNCHER_ICON = "launcher_icon"
        const val KEY_PARENTAL_OFF = "parental_off"
        const val KEY_URGE_SHARING_OFF = "urge_sharing_off"
        const val KEY_PARTNER_NOTICES_OFF = "partner_notices_off"
        private const val PREFIX_PROFILE_UNLOCK = "profile_unlock:"
        fun profileUnlock(id: Long) = "$PREFIX_PROFILE_UNLOCK$id"
        private const val PREFIX_STRICT_EXIT = "strict_exit:"
        private const val STRICT_DISABLE = "disable"
        private const val STRICT_PAUSE = "pause:"
        fun isStrictExit(key: String) = key.startsWith(PREFIX_STRICT_EXIT)
        fun strictExit(action: StrictAction) = PREFIX_STRICT_EXIT + when (action) {
            StrictAction.Disable -> STRICT_DISABLE
            is StrictAction.Pause -> "$STRICT_PAUSE${action.minutes}"
        }
        fun strictActionOf(key: String): StrictAction? {
            val what = key.removePrefix(PREFIX_STRICT_EXIT)
            return when {
                what == STRICT_DISABLE -> StrictAction.Disable
                what.startsWith(STRICT_PAUSE) -> what.removePrefix(STRICT_PAUSE).toIntOrNull()?.let(StrictAction::Pause)
                else -> null
            }
        }
        private const val PREFIX_STREAK_RESTORE = "streak_restore:"
        fun streakRestore(day: LocalDate) = "$PREFIX_STREAK_RESTORE${day.toEpochDay()}"
        fun profileOff(id: Long) = "profile_off:$id"
        fun profilePause(id: Long) = "profile_pause:$id"
        fun profileDelete(id: Long) = "profile_delete:$id"
        fun profileEdit(id: Long) = "profile_edit:$id"
        fun sessionEnd(id: Long) = "session_end:$id"
    }
}
