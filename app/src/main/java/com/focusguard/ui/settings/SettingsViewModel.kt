package com.focusguard.ui.settings

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.BackupRepository
import com.focusguard.data.ChangeGuard
import com.focusguard.data.GuardResult
import com.focusguard.data.LockStyleRepository
import com.focusguard.data.LauncherIcon
import com.focusguard.data.LauncherIconManager
import com.focusguard.data.SettingsRepository
import com.focusguard.decision.AdaptiveSettings
import com.focusguard.decision.ImageSensitivity
import com.focusguard.decision.VigilanceSettings
import com.focusguard.vision.ImageProbeSnapshot
import com.focusguard.vision.ImageProbeStats
import com.focusguard.data.StrictRepository
import com.focusguard.data.WebStats
import com.focusguard.decision.Approvals
import com.focusguard.decision.NoticeSettings
import com.focusguard.decision.NotificationSettings
import com.focusguard.decision.StatsSettings
import com.focusguard.model.LockStyle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val strict: StrictRepository,
    private val backup: BackupRepository,
    private val guard: ChangeGuard,
    private val lockStyleRepository: LockStyleRepository,
    private val webStats: WebStats,
    private val launcherIcons: LauncherIconManager,
    private val imageProbeStats: ImageProbeStats,
) : ViewModel() {

    val imageProbe: StateFlow<Boolean> = settings.imageProbe

    val imageSensitivity: StateFlow<Int> = settings.imageSensitivity

    /**
     * Subir la sensibilidad es libre. Bajarla debilita la protección: con un compañero vinculado lo tiene que aprobar siempre él; sin compañero pasa por el
     * modo estricto (que lo impide) y por las aprobaciones que haya elegido el usuario.
     */
    fun setImageSensitivity(level: Int) {
        val target = ImageSensitivity.clamp(level)
        val current = settings.imageSensitivity.value
        if (target == current) return
        if (ImageSensitivity.weakens(current, target)) {
            val result = guard.check(ApprovalExecutor.KEY_IMAGE_SENSITIVITY, Approvals.ACTION_IMAGE_SENSITIVITY, ImageSensitivity.encode(target), viaPartner = guard.partnerLinked)
            if (result is GuardResult.Blocked) {
                message = result.message
                return
            }
        }
        settings.setImageSensitivity(target)
    }

    /**
     * Encender es libre. Apagar debilita la protección (dejaría de bloquear lo explícito): con un compañero vinculado lo tiene que aprobar siempre él; sin
     * compañero pasa por el modo estricto (que lo impide) y por las aprobaciones que haya elegido el usuario.
     */
    fun setImageProbe(value: Boolean) {
        if (!value && settings.imageProbe.value) {
            val result = guard.check(ApprovalExecutor.KEY_IMAGE_PROBE_OFF, Approvals.ACTION_IMAGE_PROBE_OFF, viaPartner = guard.partnerLinked)
            if (result is GuardResult.Blocked) {
                message = result.message
                return
            }
        }
        if (value) imageProbeStats.reset()
        settings.setImageProbe(value)
    }

    val imageVigilance: StateFlow<VigilanceSettings> = settings.imageVigilance

    /**
     * Vigilar más es libre. Vigilar menos (apagarla, acortarla, bajar la intensidad, vigilar menos cosas o dejar de vigilar tras lo sugerente) debilita la
     * protección: con un compañero vinculado lo tiene que aprobar siempre él; sin compañero pasa por el modo estricto (que lo impide) y por las aprobaciones
     * que haya elegido el usuario.
     */
    fun setImageVigilance(next: VigilanceSettings) {
        val current = settings.imageVigilance.value
        if (next == current) return
        if (current.weakens(next)) {
            val result = guard.check(ApprovalExecutor.KEY_IMAGE_VIGILANCE, Approvals.ACTION_IMAGE_VIGILANCE, next.encode(), viaPartner = guard.partnerLinked)
            if (result is GuardResult.Blocked) {
                message = result.message
                return
            }
        }
        settings.setImageVigilance(next)
    }

    val imageAdaptive: StateFlow<AdaptiveSettings> = settings.imageAdaptive

    /**
     * Adaptar más es libre. Adaptar menos (apagar el modo, acortar el periodo o bajar el nivel máximo) debilita la protección: con un compañero vinculado lo
     * tiene que aprobar siempre él; sin compañero pasa por el modo estricto (que lo impide) y por las aprobaciones que haya elegido el usuario.
     */
    fun setImageAdaptive(next: AdaptiveSettings) {
        val current = settings.imageAdaptive.value
        if (next == current) return
        if (current.weakens(next)) {
            val result = guard.check(ApprovalExecutor.KEY_IMAGE_ADAPTIVE, Approvals.ACTION_IMAGE_ADAPTIVE, next.encode(), viaPartner = guard.partnerLinked)
            if (result is GuardResult.Blocked) {
                message = result.message
                return
            }
        }
        settings.setImageAdaptive(next)
    }

    val imageNotifyMinutes: StateFlow<Int> = settings.imageNotifyMinutes

    fun setImageNotifyMinutes(minutes: Int) = settings.setImageNotifyMinutes(minutes)

    val imageProbeSnapshot: StateFlow<ImageProbeSnapshot> = imageProbeStats.snapshot

    fun resetImageProbe() = imageProbeStats.reset()

    val lockStyle: StateFlow<LockStyle> = lockStyleRepository.style

    /** Vuelve a mostrar el asistente de bienvenida. Solo crea un perfil al terminar: no afloja nada, así que no pide aprobación. */
    fun restartOnboarding() = settings.resetOnboarding()
    val notices: StateFlow<NoticeSettings> = settings.notices

    fun setNotices(value: NoticeSettings) = settings.setNotices(value)

    val notifications: StateFlow<NotificationSettings> = settings.notifications

    fun setNotifications(value: NotificationSettings) = settings.setNotifications(value)

    val stats: StateFlow<StatsSettings> = settings.stats

    fun setStats(value: StatsSettings) = settings.setStats(value)

    fun clearWebStats() {
        viewModelScope.launch {
            webStats.clear()
            message = "Estadísticas de navegación borradas."
        }
    }

    var launcherIcon by mutableStateOf(launcherIcons.current())
        private set

    /** Se vuelve a leer al abrir Ajustes: una aprobación pudo cambiarlo mientras tanto. */
    fun refreshLauncherIcon() {
        launcherIcon = launcherIcons.current()
    }

    /** Volver al icono de siempre es libre; disimular la app pasa por el guardián de cambios (modo estricto, aprobaciones). */
    fun changeLauncherIcon(icon: LauncherIcon) {
        if (icon == launcherIcon) return
        if (icon != LauncherIcon.DEFAULT) {
            val blocked = guard.blockedMessage(ApprovalExecutor.KEY_LAUNCHER_ICON, Approvals.ACTION_LAUNCHER_ICON, icon.name)
            if (blocked != null) {
                message = blocked
                return
            }
        }
        launcherIcons.set(icon)
        launcherIcon = icon
    }

    fun saveLockStyle(style: LockStyle) = lockStyleRepository.save(style)

    val dayStartMinute: StateFlow<Int> = settings.dayStartMinute
    val appPinEnabled: StateFlow<Boolean> = settings.appPinEnabled
    val hasPin: Boolean get() = strict.hasPin

    var message by mutableStateOf<String?>(null)
        private set

    fun clearMessage() { message = null }

    /**
     * Mover el inicio del día mueve el reinicio de los límites diarios, así que puede levantar un
     * bloqueo por límite: con el modo estricto activo no se puede, y con aprobación activa la pide.
     */
    fun setDayStartMinute(minute: Int) {
        if (minute == settings.dayStartMinute.value) return
        message = when (val result = guard.check(ApprovalExecutor.KEY_DAY_START, Approvals.ACTION_DAY_START, minute.toString())) {
            is GuardResult.Blocked -> result.message
            GuardResult.Allowed -> {
                settings.setDayStartMinute(minute)
                "Guardado."
            }
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            message = runCatching { backup.export(uri) }
                .fold({ "Configuración exportada." }, { "No se pudo exportar: ${it.message}" })
        }
    }

    fun import(uri: Uri) {
        // Importar solo añade perfiles, pero con el modo estricto activo no se toca la configuración.
        if (guard.isLocked()) {
            message = "El modo estricto está activo: no se puede importar ahora."
            return
        }
        viewModelScope.launch {
            message = runCatching { backup.import(uri) }
                .fold({ "Se añadieron $it perfiles." }, { "No se pudo importar: ${it.message}" })
        }
    }

    /**
     * Activa el PIN de la app. Si ya hay PIN, pide el actual para cambiarlo.
     * Devuelve un mensaje de error o null si todo fue bien.
     */
    fun enableAppPin(newPin: String, currentPin: String): String? {
        if (strict.hasPin && !strict.verifyPin(currentPin)) return "El PIN actual no es correcto."
        if (newPin.isNotBlank() && !strict.setPin(newPin)) return "El PIN debe tener de 4 a 8 dígitos."
        if (!strict.hasPin) return "Define un PIN de 4 a 8 dígitos."
        settings.setAppPinEnabled(true)
        return null
    }

    fun disableAppPin(currentPin: String): String? {
        if (!strict.verifyPin(currentPin)) return "El PIN no es correcto."
        settings.setAppPinEnabled(false)
        return null
    }

    fun verifyPin(pin: String) = strict.verifyPin(pin)
}
