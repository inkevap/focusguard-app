package com.focusguard.data

import android.content.Context
import com.focusguard.BuildConfig
import com.focusguard.server.ServerUrl
import com.focusguard.decision.AutoReactivation
import com.focusguard.decision.EmbeddedBrowserPolicy
import com.focusguard.decision.MoodPromptSettings
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.decision.AdaptiveSettings
import com.focusguard.decision.PartnerPanel
import com.focusguard.decision.ImageAlerts
import com.focusguard.decision.ImageIncidents
import com.focusguard.decision.ImageSensitivity
import com.focusguard.decision.VigilanceSettings
import com.focusguard.decision.NudgeSettings
import com.focusguard.decision.NoticeSettings
import com.focusguard.decision.NotificationSettings
import com.focusguard.decision.StatsSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ajustes globales sencillos, guardados en preferencias. */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Viene encendido de serie (punto 8 de CORRECCIONES): quien ya eligió apagarlo (con aprobación) conserva su valor guardado.
    private val _adultBlock = MutableStateFlow(prefs.getBoolean(KEY_ADULT, true))

    /** Interruptor global de contenido adulto (D1). Las listas y coincidencias llegan en F4. */
    val adultBlockEnabled: StateFlow<Boolean> = _adultBlock.asStateFlow()

    private val _partnerNotices = MutableStateFlow(prefs.getBoolean(KEY_PARTNER_NOTICES, false))

    /** Si el compañero recibe aviso de lo que pasa en el teléfono (I4). Se enciende solo cuando el compañero acepta; apagarlo con un compañero activo lo tiene que aprobar él. */
    val partnerNotices: StateFlow<Boolean> = _partnerNotices.asStateFlow()

    fun setPartnerNotices(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PARTNER_NOTICES, enabled).apply()
        _partnerNotices.value = enabled
    }

    private val _partnerPanel = MutableStateFlow(prefs.getString(KEY_PARTNER_PANEL, null)?.let(PartnerPanel::decode) ?: PartnerPanel())

    /** Qué avisos manda el teléfono y qué protecciones valen con compañero, para «Avisarle lo que pasa» y para el modo parental. Quitar algo lo tiene que aprobar el compañero. */
    val partnerPanel: StateFlow<PartnerPanel> = _partnerPanel.asStateFlow()

    fun setPartnerPanel(value: PartnerPanel) {
        prefs.edit().putString(KEY_PARTNER_PANEL, value.encode()).apply()
        _partnerPanel.value = value
    }

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, null) ?: BuildConfig.SERVER_URL)

    /** Dirección del servidor propio (`https://…`). Vacía mientras el usuario no la haya puesto. */
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    /** Guarda la dirección ya normalizada (solo `https`; en depuración también `http` local). `false` si no sirve. */
    fun setServerUrl(raw: String): Boolean {
        val value = ServerUrl.normalize(raw, BuildConfig.DEBUG) ?: return false
        prefs.edit().putString(KEY_SERVER_URL, value).apply()
        _serverUrl.value = value
        return true
    }

    private val _parentalMode = MutableStateFlow(prefs.getBoolean(KEY_PARENTAL, false))

    /**
     * Modo parental (I7): con un compañero activo (el padre o la madre), todo cambio que debilite la protección lo aprueba él, sin
     * retardos ni frases. Encenderlo refuerza y es libre; apagarlo se lo pide a él.
     */
    val parentalMode: StateFlow<Boolean> = _parentalMode.asStateFlow()

    fun setParentalMode(enabled: Boolean) {
        if (_parentalMode.value == enabled) return
        prefs.edit().putBoolean(KEY_PARENTAL, enabled).apply()
        _parentalMode.value = enabled
    }

    private val _shareUrgeReport = MutableStateFlow(prefs.getBoolean(KEY_SHARE_URGES, false))

    /**
     * Enseñar a mi compañero un resumen semanal de mis impulsos (L7): cuántos, cuántos pasé, la hora más frecuente y lo que más los desencadena.
     * Encenderlo es libre (se ve más); apagarlo con un compañero activo lo aprueba él.
     */
    val shareUrgeReport: StateFlow<Boolean> = _shareUrgeReport.asStateFlow()

    fun setShareUrgeReport(enabled: Boolean) {
        if (_shareUrgeReport.value == enabled) return
        prefs.edit().putBoolean(KEY_SHARE_URGES, enabled).apply()
        _shareUrgeReport.value = enabled
    }

    private val _dayStartMinute = MutableStateFlow(prefs.getInt(KEY_DAY_START, 0))

    /** Minuto del día en que empieza el día lógico para los límites diarios (E17). */
    val dayStartMinute: StateFlow<Int> = _dayStartMinute.asStateFlow()

    fun setDayStartMinute(minute: Int) {
        val value = minute.coerceIn(0, 24 * 60 - 1)
        prefs.edit().putInt(KEY_DAY_START, value).apply()
        _dayStartMinute.value = value
    }

    private val _notices = MutableStateFlow(NoticeSettings(
            scheduleStarting = prefs.getBoolean(KEY_NOTICE_START, true),
            limitNearing = prefs.getBoolean(KEY_NOTICE_LIMIT, true),
            limitStatus = prefs.getBoolean(KEY_NOTICE_LIMIT_STATUS, true),
            scheduleEnded = prefs.getBoolean(KEY_NOTICE_ENDED, true),
            pauseEnding = prefs.getBoolean(KEY_NOTICE_PAUSE, true),
            dailyMotivation = prefs.getBoolean(KEY_NOTICE_DAILY, false),
            weeklyReport = prefs.getBoolean(KEY_NOTICE_WEEKLY, false),
            streak = prefs.getBoolean(KEY_NOTICE_STREAK, true),
        ))

    /** Qué avisos se dan antes de que algo suceda (J3). */
    val notices: StateFlow<NoticeSettings> = _notices.asStateFlow()

    fun setNotices(value: NoticeSettings) {
        prefs.edit()
            .putBoolean(KEY_NOTICE_START, value.scheduleStarting).putBoolean(KEY_NOTICE_LIMIT, value.limitNearing).putBoolean(KEY_NOTICE_LIMIT_STATUS, value.limitStatus)
            .putBoolean(KEY_NOTICE_ENDED, value.scheduleEnded).putBoolean(KEY_NOTICE_PAUSE, value.pauseEnding).putBoolean(KEY_NOTICE_DAILY, value.dailyMotivation)
            .putBoolean(KEY_NOTICE_WEEKLY, value.weeklyReport).putBoolean(KEY_NOTICE_STREAK, value.streak)
            .apply()
        _notices.value = value
    }

    private val _newApps = MutableStateFlow(NewAppsPolicy(prefs.getBoolean(KEY_NEW_APPS, false), prefs.getInt(KEY_NEW_APPS_HOURS, NewAppsPolicy.DEFAULT_HOURS)))

    /** Bloquear las apps recién instaladas durante unas horas (A11). */
    val newApps: StateFlow<NewAppsPolicy> = _newApps.asStateFlow()

    fun setNewApps(value: NewAppsPolicy) {
        prefs.edit().putBoolean(KEY_NEW_APPS, value.enabled).putInt(KEY_NEW_APPS_HOURS, value.hours).apply()
        _newApps.value = value
    }

    private val _stats = MutableStateFlow(StatsSettings(prefs.getBoolean(KEY_STATS_WEBS, false), prefs.getBoolean(KEY_STATS_INCOGNITO, false)))

    /** Qué estadísticas de navegación se guardan (K2). Apagadas por defecto: dicen qué webs visitas. */
    val stats: StateFlow<StatsSettings> = _stats.asStateFlow()

    fun setStats(value: StatsSettings) {
        prefs.edit().putBoolean(KEY_STATS_WEBS, value.webs).putBoolean(KEY_STATS_INCOGNITO, value.incognito).apply()
        _stats.value = value
    }

    private val _nudge = MutableStateFlow(NudgeSettings(prefs.getBoolean(KEY_NUDGE, false), prefs.getStringSet(KEY_NUDGE_APPS, emptySet()).orEmpty().toSet()))

    /** Aviso de tiempo sin bloquear (K7): apagado por defecto. Solo avisa, no debilita nada: es libre. */
    val nudge: StateFlow<NudgeSettings> = _nudge.asStateFlow()

    fun setNudge(value: NudgeSettings) {
        prefs.edit().putBoolean(KEY_NUDGE, value.enabled).putStringSet(KEY_NUDGE_APPS, value.apps).apply()
        _nudge.value = value
    }

    private val _imageProbe = MutableStateFlow(prefs.getBoolean(KEY_IMAGE_PROBE, false))

    /**
     * Detección de imágenes: apagada de serie. Encendida, **bloquea lo explícito y avisa de lo sugerente**. Encenderla es libre; **apagarla debilita** y
     * pasa por [ChangeGuard] (con un compañero, siempre por él): esa decisión se toma antes de llamar a [setImageProbe], que solo guarda.
     */
    val imageProbe: StateFlow<Boolean> = _imageProbe.asStateFlow()

    fun setImageProbe(value: Boolean) {
        prefs.edit().putBoolean(KEY_IMAGE_PROBE, value).apply()
        _imageProbe.value = value
    }

    private val _imageSensitivity = MutableStateFlow(ImageSensitivity.clamp(prefs.getInt(KEY_IMAGE_SENSITIVITY, ImageSensitivity.DEFAULT)))

    /**
     * Sensibilidad de la detección de imágenes, de 1 (mínima) a 5 (máxima). Subirla es libre; **bajarla debilita** y pasa por [ChangeGuard]
     * (con un compañero, siempre por él): esa decisión se toma antes de llamar a [setImageSensitivity], que solo guarda.
     */
    val imageSensitivity: StateFlow<Int> = _imageSensitivity.asStateFlow()

    fun setImageSensitivity(level: Int) {
        val value = ImageSensitivity.clamp(level)
        prefs.edit().putInt(KEY_IMAGE_SENSITIVITY, value).apply()
        _imageSensitivity.value = value
    }

    private val _imageVigilance = MutableStateFlow(prefs.getString(KEY_IMAGE_VIGILANCE, null)?.let(VigilanceSettings::decode) ?: VigilanceSettings())

    /**
     * La vigilancia tras lo explícito (más capturas por un tiempo). Cambiarla para **vigilar más** es libre; **vigilar menos debilita** y pasa por
     * [ChangeGuard]: esa decisión se toma antes de llamar a [setImageVigilance], que solo guarda.
     */
    val imageVigilance: StateFlow<VigilanceSettings> = _imageVigilance.asStateFlow()

    fun setImageVigilance(value: VigilanceSettings) {
        prefs.edit().putString(KEY_IMAGE_VIGILANCE, value.encode()).apply()
        _imageVigilance.value = value
    }

    private val _imageAdaptive = MutableStateFlow(prefs.getString(KEY_IMAGE_ADAPTIVE, null)?.let(AdaptiveSettings::decode) ?: AdaptiveSettings())

    /**
     * El modo dinámico: con cada bloqueo de algo explícito sube el nivel de alerta (más vigilancia y más sensibilidad) y con el tiempo vuelve solo. Adaptar más
     * es libre; **adaptar menos debilita** y pasa por [ChangeGuard]: esa decisión se toma antes de llamar a [setImageAdaptive], que solo guarda.
     */
    val imageAdaptive: StateFlow<AdaptiveSettings> = _imageAdaptive.asStateFlow()

    fun setImageAdaptive(value: AdaptiveSettings) {
        prefs.edit().putString(KEY_IMAGE_ADAPTIVE, value.encode()).apply()
        _imageAdaptive.value = value
    }

    /** Cuándo se bloqueó algo explícito (solo las horas, de la última semana): es lo que lee el modo dinámico para subir o bajar el nivel. */
    fun imageIncidents(): List<Long> = ImageIncidents.decode(prefs.getString(KEY_IMAGE_INCIDENTS, "").orEmpty())

    /** Apunta un incidente en [nowMs] y devuelve el historial al día. */
    fun addImageIncident(nowMs: Long): List<Long> =
        ImageIncidents.add(imageIncidents(), nowMs).also { prefs.edit().putString(KEY_IMAGE_INCIDENTS, ImageIncidents.encode(it)).apply() }

    /** Hasta cuándo dura la vigilancia, con la hora del reloj: así sobrevive a un reinicio del servicio. */
    var imageVigilanceUntilMs: Long
        get() = prefs.getLong(KEY_IMAGE_VIGILANCE_UNTIL, 0L)
        set(value) = prefs.edit().putLong(KEY_IMAGE_VIGILANCE_UNTIL, value).apply()

    private val _imageNotifyMinutes = MutableStateFlow(ImageAlerts.clamp(prefs.getInt(KEY_IMAGE_NOTIFY_MINUTES, ImageAlerts.DEFAULT_MINUTES)))

    /** Cada cuántos minutos como mucho se ofrece bloquear algo sugerente con una notificación. Solo regula avisos: es libre. */
    val imageNotifyMinutes: StateFlow<Int> = _imageNotifyMinutes.asStateFlow()

    fun setImageNotifyMinutes(minutes: Int) {
        val value = ImageAlerts.clamp(minutes)
        prefs.edit().putInt(KEY_IMAGE_NOTIFY_MINUTES, value).apply()
        _imageNotifyMinutes.value = value
    }

    private val _moodPrompt = MutableStateFlow(MoodPromptSettings(prefs.getBoolean(KEY_MOOD, false), prefs.getInt(KEY_MOOD_MINUTE, 20 * 60).coerceIn(0, LAST_MINUTE)))

    /** La pregunta de ánimo que sale sola (K5): apagada por defecto. */
    val moodPrompt: StateFlow<MoodPromptSettings> = _moodPrompt.asStateFlow()

    fun setMoodPrompt(value: MoodPromptSettings) {
        prefs.edit().putBoolean(KEY_MOOD, value.enabled).putInt(KEY_MOOD_MINUTE, value.minuteOfDay).apply()
        _moodPrompt.value = value
    }

    private val _notifications = MutableStateFlow(
        NotificationSettings(prefs.getBoolean(KEY_NOTIF_CAPTURE, false), prefs.getBoolean(KEY_NOTIF_HIDE_NOTICE, false)),
    )

    /** Qué se hace con las notificaciones (J1, J2). Apagado por defecto. */
    val notifications: StateFlow<NotificationSettings> = _notifications.asStateFlow()

    fun setNotifications(value: NotificationSettings) {
        prefs.edit().putBoolean(KEY_NOTIF_CAPTURE, value.captureBlocked).putBoolean(KEY_NOTIF_HIDE_NOTICE, value.hideBackgroundNotice).apply()
        _notifications.value = value
    }

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))

    /** Si ya se pasó (o se saltó) la bienvenida de la primera vez (O3). */
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    fun setOnboardingDone() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
        _onboardingDone.value = true
    }

    private val _adminOffGrantUntilMs = MutableStateFlow(prefs.getLong(KEY_ADMIN_GRANT, 0L))

    /** Hasta cuándo (hora de confianza, en ms) se puede desactivar el administrador del dispositivo sin que se cierre la pantalla; 0 si no hay permiso. */
    val adminOffGrantUntilMs: StateFlow<Long> = _adminOffGrantUntilMs.asStateFlow()

    fun grantAdminOff(untilMs: Long) {
        prefs.edit().putLong(KEY_ADMIN_GRANT, untilMs).apply()
        _adminOffGrantUntilMs.value = untilMs
    }

    /** Vuelve a enseñar el asistente de la primera vez. */
    fun resetOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, false).apply()
        _onboardingDone.value = false
    }

    private val _embedded = MutableStateFlow(prefs.getString(KEY_EMBEDDED, null)?.let(EmbeddedBrowserPolicy::decode) ?: EmbeddedBrowserPolicy())

    /** Bloquear las apps que enseñan una vista web propia (B8). */
    val embeddedBrowsers: StateFlow<EmbeddedBrowserPolicy> = _embedded.asStateFlow()

    fun setEmbeddedBrowsers(value: EmbeddedBrowserPolicy) {
        prefs.edit().putString(KEY_EMBEDDED, value.encode()).apply()
        _embedded.value = value
    }

    private val _detectedWebViewApps = MutableStateFlow(prefs.getStringSet(KEY_DETECTED_WEBVIEW, null).orEmpty().toSet())

    /** Apps en las que el servicio ya vio una vista web, para sugerirlas (B8). */
    val detectedWebViewApps: StateFlow<Set<String>> = _detectedWebViewApps.asStateFlow()

    fun addDetectedWebViewApp(packageName: String) {
        if (packageName in _detectedWebViewApps.value) return
        val updated = _detectedWebViewApps.value + packageName
        prefs.edit().putStringSet(KEY_DETECTED_WEBVIEW, updated).apply()
        _detectedWebViewApps.value = updated
    }

    private val _foldFloating = MutableStateFlow(prefs.getBoolean(KEY_FOLD_FLOATING, false))

    /** Intentar cerrar las ventanas flotantes de una app bloqueada (A7). */
    val foldFloating: StateFlow<Boolean> = _foldFloating.asStateFlow()

    fun setFoldFloating(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FOLD_FLOATING, enabled).apply()
        _foldFloating.value = enabled
    }

    private val _appPinEnabled = MutableStateFlow(prefs.getBoolean(KEY_APP_PIN, false))

    /** Pedir el PIN al abrir la app (H9). Usa el mismo PIN que el modo estricto. */
    val appPinEnabled: StateFlow<Boolean> = _appPinEnabled.asStateFlow()

    fun setAppPinEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_PIN, enabled).apply()
        _appPinEnabled.value = enabled
    }

    private val _adultOffUntilMs = MutableStateFlow(prefs.getLong(KEY_ADULT_OFF_UNTIL, NO_TIME).takeIf { it != NO_TIME })

    /** Si el bloqueo adulto se apagó con una aprobación, la hora en que se enciende solo (D6). */
    val adultOffUntilMs: StateFlow<Long?> = _adultOffUntilMs.asStateFlow()

    fun setAdultBlockEnabled(enabled: Boolean) = storeAdult(enabled, until = null)

    /** Apagado con una aprobación: pasada esa hora se enciende solo. */
    fun turnAdultOffUntil(reactivateAtMs: Long) = storeAdult(false, until = reactivateAtMs)

    private fun storeAdult(enabled: Boolean, until: Long?) {
        prefs.edit().putBoolean(KEY_ADULT, enabled).putLong(KEY_ADULT_OFF_UNTIL, until ?: NO_TIME).apply()
        _adultBlock.value = enabled
        _adultOffUntilMs.value = until
    }

    /** Enciende el bloqueo adulto si ya pasó la hora de reactivación. Devuelve si lo encendió. */
    fun reactivateAdultIfDue(nowMs: Long): Boolean {
        if (!AutoReactivation.due(_adultOffUntilMs.value, nowMs)) return false
        setAdultBlockEnabled(true)
        return true
    }

    /** Un conjunto de textos guardado en preferencias, con su flujo. */
    inner class StringSetSetting(private val key: String) {
        private val _values = MutableStateFlow(prefs.getStringSet(key, null).orEmpty().toSet())
        val flow: StateFlow<Set<String>> = _values.asStateFlow()

        fun add(entry: String) = change { it + entry }

        fun remove(entry: String) = change { it - entry }

        private fun change(transform: (Set<String>) -> Set<String>) {
            val updated = transform(_values.value)
            if (updated == _values.value) return
            prefs.edit().putStringSet(key, updated).apply()
            _values.value = updated
        }
    }

    /** Una hora guardada que puede no existir (`null`): en las preferencias, `NO_TIME` quiere decir «nunca». */
    private inner class OptionalTime(private val key: String) {
        private val state = MutableStateFlow(prefs.getLong(key, NO_TIME).takeIf { it != NO_TIME })
        val flow: StateFlow<Long?> = state.asStateFlow()

        fun set(ms: Long?) {
            prefs.edit().putLong(key, ms ?: NO_TIME).apply()
            state.value = ms
        }
    }

    private val lastServerBackup = OptionalTime(KEY_LAST_BACKUP)
    private val lastSync = OptionalTime(KEY_LAST_SYNC)
    private val lastDnsSummary = OptionalTime(KEY_LAST_DNS_SUMMARY)
    private val lastUrgeReport = OptionalTime(KEY_LAST_URGE_REPORT)

    /** Cuándo se subió la última copia al servidor (hora de confianza); null si nunca. */
    val lastServerBackupMs: StateFlow<Long?> = lastServerBackup.flow

    fun setLastServerBackupMs(ms: Long?) = lastServerBackup.set(ms)

    /** Cuándo se sincronizó por última vez con los otros dispositivos (hora de confianza); null si nunca. */
    val lastSyncMs: StateFlow<Long?> = lastSync.flow

    fun setLastSyncMs(ms: Long?) = lastSync.set(ms)

    /** El último día (desde 1970, hora local) cuyo total de consultas DNS ya se contó al compañero; null si ninguno. */
    val lastDnsSummaryDay: Long? get() = lastDnsSummary.flow.value

    fun setLastDnsSummaryDay(day: Long?) = lastDnsSummary.set(day)

    /** El lunes (días desde 1970) de la última semana cuyo resumen de impulsos ya se contó al compañero; null si ninguna. */
    val lastUrgeReportWeek: Long? get() = lastUrgeReport.flow.value

    fun setLastUrgeReportWeek(monday: Long?) = lastUrgeReport.set(monday)

    private val _dnsFilter = MutableStateFlow(prefs.getBoolean(KEY_DNS_FILTER, false))

    /** Filtro DNS propio por VPN local (D5). */
    val dnsFilterEnabled: StateFlow<Boolean> = _dnsFilter.asStateFlow()

    fun setDnsFilterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DNS_FILTER, enabled).apply()
        _dnsFilter.value = enabled
    }

    private val dnsExcludedSetting = StringSetSetting(KEY_DNS_EXCLUDED)

    /** Apps cuyas consultas DNS no pasan por el filtro (D5). */
    val dnsExcludedApps: StateFlow<Set<String>> get() = dnsExcludedSetting.flow

    fun addDnsExcludedApp(packageName: String) = dnsExcludedSetting.add(packageName)

    fun removeDnsExcludedApp(packageName: String) = dnsExcludedSetting.remove(packageName)

    private val adultTextExemptSetting = StringSetSetting(KEY_ADULT_TEXT_EXEMPT)

    /** Apps que el usuario eligió para que el filtro adulto no lea su texto (además de las de serie, `AdultTextExemptions`). */
    val adultTextExemptApps: StateFlow<Set<String>> get() = adultTextExemptSetting.flow

    fun addAdultTextExemptApp(packageName: String) = adultTextExemptSetting.add(packageName)

    fun removeAdultTextExemptApp(packageName: String) = adultTextExemptSetting.remove(packageName)

    private val adultAllowedSetting = StringSetSetting(KEY_ADULT_ALLOWED)
    private val adultWordsSetting = StringSetSetting(KEY_ADULT_WORDS)

    /** Lo que el usuario dejó pasar a propósito del filtro adulto (D7). */
    val adultAllowed: StateFlow<Set<String>> get() = adultAllowedSetting.flow

    fun addAdultAllowed(entry: String) = adultAllowedSetting.add(entry)

    fun removeAdultAllowed(entry: String) = adultAllowedSetting.remove(entry)

    /** Palabras y webs propias del usuario que se suman a las del servidor (D2). */
    val adultWords: StateFlow<Set<String>> get() = adultWordsSetting.flow

    fun addAdultWord(entry: String) = adultWordsSetting.add(entry)

    fun removeAdultWord(entry: String) = adultWordsSetting.remove(entry)

    private companion object {
        /** El último minuto del día (23:59). */
        const val LAST_MINUTE = 24 * 60 - 1

        const val NO_TIME = 0L
        const val KEY_ADULT = "adult_block_enabled"
        const val KEY_DNS_FILTER = "dns_filter"
        const val KEY_LAST_BACKUP = "last_server_backup"
        const val KEY_LAST_SYNC = "last_sync"
        const val KEY_LAST_DNS_SUMMARY = "last_dns_summary"
        const val KEY_LAST_URGE_REPORT = "last_urge_report"
        const val KEY_SHARE_URGES = "share_urge_report"
        const val KEY_DNS_EXCLUDED = "dns_excluded_apps"
        const val KEY_ADULT_OFF_UNTIL = "adult_off_until"
        const val KEY_ADULT_ALLOWED = "adult_allowed"
        const val KEY_ADULT_TEXT_EXEMPT = "adult_text_exempt_apps"
        const val KEY_ADULT_WORDS = "adult_words"
        const val KEY_PARTNER_NOTICES = "partner_notices"
        const val KEY_APP_PIN = "app_pin_enabled"
        const val KEY_DAY_START = "day_start_minute"
        const val KEY_PARENTAL = "parental_mode"
        const val KEY_PARTNER_PANEL = "partner_panel"
        const val KEY_NUDGE = "nudge_enabled"
        const val KEY_IMAGE_PROBE = "image_probe"
        const val KEY_IMAGE_SENSITIVITY = "image_sensitivity"
        const val KEY_IMAGE_NOTIFY_MINUTES = "image_notify_minutes"
        const val KEY_IMAGE_VIGILANCE = "image_vigilance"
        const val KEY_IMAGE_ADAPTIVE = "image_adaptive"
        const val KEY_IMAGE_INCIDENTS = "image_incidents"
        const val KEY_IMAGE_VIGILANCE_UNTIL = "image_vigilance_until"
        const val KEY_NUDGE_APPS = "nudge_apps"
        const val KEY_MOOD = "mood_prompt"
        const val KEY_MOOD_MINUTE = "mood_prompt_minute"
        const val KEY_STATS_WEBS = "stats_webs"
        const val KEY_STATS_INCOGNITO = "stats_incognito"
        const val KEY_NOTIF_CAPTURE = "notif_capture"
        const val KEY_NOTIF_HIDE_NOTICE = "notif_hide_notice"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_ADMIN_GRANT = "admin_off_grant_until"
        const val KEY_EMBEDDED = "embedded_browsers"
        const val KEY_DETECTED_WEBVIEW = "detected_webview_apps"
        const val KEY_FOLD_FLOATING = "fold_floating"
        const val KEY_NEW_APPS = "new_apps_enabled"
        const val KEY_NEW_APPS_HOURS = "new_apps_hours"
        const val KEY_NOTICE_START = "notice_schedule_starting"
        const val KEY_NOTICE_LIMIT = "notice_limit_nearing"
        const val KEY_NOTICE_LIMIT_STATUS = "notice_limit_status"
        const val KEY_NOTICE_ENDED = "notice_schedule_ended"
        const val KEY_NOTICE_PAUSE = "notice_pause_ending"
        const val KEY_NOTICE_DAILY = "notice_daily_motivation"
        const val KEY_NOTICE_WEEKLY = "notice_weekly_report"
        const val KEY_NOTICE_STREAK = "notice_streak"
        const val KEY_SERVER_URL = "server_url"
    }
}
