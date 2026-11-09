package com.focusguard.service

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.content.Context
import android.os.PowerManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.os.SystemClock
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.focusguard.R
import com.focusguard.appLabel
import com.focusguard.data.ApprovalRepository
import com.focusguard.data.BlockGate
import com.focusguard.data.CategoryCatalog
import com.focusguard.data.BlockLog
import com.focusguard.data.EmergencyRepository
import com.focusguard.data.InstallTimes
import com.focusguard.data.LockStyleRepository
import com.focusguard.data.AdultFilterProvider
import com.focusguard.data.NoticeLedger
import com.focusguard.data.WrongBlockReporter
import com.focusguard.data.PartnerEventOutbox
import com.focusguard.decision.AdultGate
import com.focusguard.decision.AdultTextExemptions
import com.focusguard.decision.WrongBlockReports
import com.focusguard.decision.WrongBlockReport
import com.focusguard.dns.DnsFilter
import com.focusguard.decision.PartnerEvents
import com.focusguard.model.PartnerEventType
import com.focusguard.data.NotificationLog
import com.focusguard.data.ProfilePauses
import com.focusguard.data.ProfileRepository
import com.focusguard.data.ServiceHealth
import com.focusguard.data.SettingsRepository
import com.focusguard.data.SubAppCatalog
import com.focusguard.data.StrictRepository
import com.focusguard.data.HabitsStore
import com.focusguard.data.StreakStore
import com.focusguard.data.TrustedClock
import com.focusguard.data.UsageRepository
import com.focusguard.data.WebStats
import com.focusguard.decision.BlockDecider
import com.focusguard.decision.CategoryTable
import com.focusguard.decision.Conditions
import com.focusguard.decision.EmergencyAvailability
import com.focusguard.decision.EmbeddedBrowsers
import com.focusguard.decision.EmergencyUnlock
import com.focusguard.decision.CaptureRiskClassifier
import com.focusguard.decision.EndRules
import com.focusguard.decision.ImageAlerts
import com.focusguard.vision.ImageActions
import com.focusguard.vision.GantManScorer
import com.focusguard.vision.ImageHistory
import com.focusguard.vision.NudeNetScorer
import com.focusguard.vision.ImageProbe
import com.focusguard.vision.ImageProbeStats
import com.focusguard.decision.NewApps
import com.focusguard.decision.LockBlockers
import com.focusguard.decision.Notice
import com.focusguard.decision.NoticeKind
import com.focusguard.decision.HabitInputs
import com.focusguard.decision.MoodPromptSettings
import com.focusguard.decision.Notices
import com.focusguard.decision.NudgeTracker
import com.focusguard.decision.Reminder
import com.focusguard.decision.ProtectedTimeAccumulator
import com.focusguard.decision.Streaks
import com.focusguard.decision.Periods
import com.focusguard.decision.ProfileActivation
import com.focusguard.decision.Pomodoro
import com.focusguard.decision.ScreenInfo
import com.focusguard.decision.SensitiveScreens
import com.focusguard.decision.WindowEvents
import com.focusguard.decision.UninstallGuard
import com.focusguard.decision.StatsSettings
import com.focusguard.decision.StrictMode
import com.focusguard.decision.UrlNormalizer
import com.focusguard.decision.WebKey
import com.focusguard.decision.WebObservation
import com.focusguard.decision.WebUsage
import com.focusguard.decision.WebUsageCounter
import com.focusguard.decision.PomodoroPhase
import com.focusguard.decision.LimitStatus
import com.focusguard.decision.ProfileTargets
import com.focusguard.decision.UsageAccumulator
import com.focusguard.decision.UsageTargets
import com.focusguard.lock.LockActivity
import com.focusguard.lock.LockScreenContent
import com.focusguard.lock.LockScreenView
import com.focusguard.model.ApprovalMode
import com.focusguard.model.BlockDecision
import com.focusguard.model.BlockEvent
import com.focusguard.model.BlockReason
import com.focusguard.model.GuardTargets
import com.focusguard.model.DeviceContext
import com.focusguard.model.ForegroundState
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.model.StrictProtection
import com.focusguard.model.LimitScope
import com.focusguard.model.ProfileUsage
import com.focusguard.model.UsageKey
import com.focusguard.permissions.Permission
import com.focusguard.permissions.PermissionChecker
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Detecta la app en primer plano y la URL de los navegadores, consulta a [BlockDecider]
 * y muestra la pantalla de bloqueo. Toda la lógica de decisión vive en `core-decision`.
 */
class FocusAccessibilityService : AccessibilityService(), KoinComponent {

    private val repository: ProfileRepository by inject()
    private val usageRepository: UsageRepository by inject()
    private val settings: SettingsRepository by inject()
    private val strictRepository: StrictRepository by inject()
    private val blockLog: BlockLog by inject()
    private val clock: TrustedClock by inject()
    private val approvals: ApprovalRepository by inject()
    private val deviceContext: DeviceContextProvider by inject()
    private val imageProbeStats: ImageProbeStats by inject()
    private val emergency: EmergencyRepository by inject()
    private val lockStyle: LockStyleRepository by inject()
    private val health: ServiceHealth by inject()
    private val noticeLedger: NoticeLedger by inject()
    private val subAppCatalog: SubAppCatalog by inject()
    private val webStats: WebStats by inject()
    private val blockGate: BlockGate by inject()
    private val notificationLog: NotificationLog by inject()
    private val pauses: ProfilePauses by inject()
    private val partnerEvents: PartnerEventOutbox by inject()
    private val partnerRepository: com.focusguard.data.PartnerRepository by inject()
    private val adultFilter: AdultFilterProvider by inject()
    private val streaks: StreakStore by inject()
    private val habits: HabitsStore by inject()
    private val adultGate = AdultGate()
    private val dnsFilter: DnsFilter by inject()
    private val wrongBlocks: WrongBlockReporter by inject()
    private val permissionChecker: PermissionChecker by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var browsers: BrowserTable
    private lateinit var decider: BlockDecider
    private val categories: CategoryTable by lazy { CategoryCatalog.fromAssets(this) }
    private var neverBlock: Set<String> = emptySet()
    private var overlayPackages: Set<String> = emptySet()
    private var profiles: List<Profile> = emptyList()
    private var needsDevice = false
    private var imageProbe: ImageProbe? = null
    private val installTimes: InstallTimes by lazy { InstallTimes(packageManager) }

    /** Uso acumulado por perfil con límite. Solo se toca desde el hilo principal. */
    private val usage = mutableMapOf<Long, ProfileUsage>()

    /** Uso de cada app o web en los perfiles con límite por app. Solo se toca desde el hilo principal. */
    private val targetUsage = mutableMapOf<UsageKey, ProfileUsage>()
    private var limitStatusShown = false
    private var lastLimitStatusMs = 0L
    private var lastLimitStatusPackage: String? = null
    private var currentState: ForegroundState? = null

    private lateinit var notifier: Notifier
    private lateinit var appLabel: String
    private var lastGuardMs = 0L
    private val lastPhase = mutableMapOf<Long, String>()
    private var lastNoticeMs = 0L
    private val protectedTime = ProtectedTimeAccumulator()
    private val nudgeTracker = NudgeTracker()
    @Volatile private var reminders: List<Reminder> = emptyList()
    @Volatile private var lastMoodDay: LocalDate? = null
    @Volatile private var moodLoaded = false
    private var lastPurgeMs = 0L
    private val webCounter = WebUsageCounter()
    private var incognitoSeen: Pair<String, Long>? = null
    private val webViewChecks = HashMap<String, Pair<Long, Boolean>>()
    private var lastWebPruneDay: LocalDate? = null

    private var overlay: View? = null
    private val removeOverlayRunnable = Runnable { removeOverlay() }
    private var lastBlockAtMs = 0L
    private var lastSubAppCheckMs = 0L
    private var lastSubAppBlockMs = 0L
    private var anySubApps = false
    private val recheckSubApps = Runnable {
        rootInActiveWindow?.packageName?.toString()?.let { guardSubApps(it, clock.now()) }
    }
    private var currentActivity: String? = null

    override fun onServiceConnected() {
        dnsFilter.ensureRunning()
        browsers = BrowserTable.fromAssets(this)
        notifier = Notifier(this)
        appLabel = getString(R.string.app_name)
        neverBlock = NeverBlockProvider.build(this)
        overlayPackages = NeverBlockProvider.overlayPackages(this)
        decider = newDecider(settings.dayStartMinute.value)
        blockGate.install { pkg -> decide(stateOf(pkg)) is BlockDecision.Block }
        scope.launch { settings.dayStartMinute.collect { decider = newDecider(it) } }
        scope.launch {
            repository.profiles.collect {
                profiles = it
                needsDevice = Conditions.needsDevice(it)
                anySubApps = it.any { profile -> profile.subApps.isNotEmpty() }
                deviceContext.setBluetoothWanted(it.any { profile -> profile.bluetoothNames.isNotEmpty() } || EndRules.watchesBluetooth(it))
                // La posición solo se pide mientras algún perfil tenga un lugar (o un Bloquear ya espere a que llegues a uno).
                if (it.any { profile -> profile.geofence != null } || EndRules.watchesPlace(it)) deviceContext.startLocationUpdates()
                else deviceContext.stopLocationUpdates()
            }
        }
        scope.launch { habits.reminders.collect { reminders = it } }
        // Registro experimental de la detección de imágenes (apagado de serie): solo mide, no bloquea.
        imageProbe = ImageProbe(
            this, GantManScorer(this), NudeNetScorer(this), imageProbeStats, scope,
            target = { foregroundState()?.let { CaptureRiskClassifier.classify(it, categories) } },
            foregroundPackage = { foregroundState()?.packageName },
            trusted = { neverBlock },
            sensitivity = { settings.imageSensitivity.value },
            notifyEveryMs = { ImageAlerts.toMs(settings.imageNotifyMinutes.value) },
            vigilanceSettings = { settings.imageVigilance.value },
            adaptiveSettings = { settings.imageAdaptive.value },
            history = object : ImageHistory {
                override fun incidents() = settings.imageIncidents()
                override fun addIncident(nowMs: Long) = settings.addImageIncident(nowMs)
                override fun vigilanceUntilMs() = settings.imageVigilanceUntilMs
                override fun setVigilanceUntilMs(ms: Long) { settings.imageVigilanceUntilMs = ms }
            },
            wallNow = { clock.nowMs() },
            onBlock = ::blockImageContent,
            onOffer = ::offerImageBlock,
        )
        ContextCompat.registerReceiver(this, imageBlockReceiver, IntentFilter(ImageActions.BLOCK_NOW), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch { settings.imageProbe.collect { if (it) imageProbe?.start() else imageProbe?.stop() } }
        // Basta saber el día del último ánimo apuntado para saber si hoy ya se respondió.
        scope.launch { habits.lastMoodDay.collect { lastMoodDay = it; moodLoaded = true } }
        scope.launch {
            usage.putAll(usageRepository.loadAll())
            targetUsage.putAll(usageRepository.loadTargets())
            trackUsage()
        }
        scope.launch { pollForegroundFallback() }
        // La señal de vida va al final: quien espera a que el servicio esté listo (el bloqueo de arranque) no debe verla antes de que decida.
        health.beat(clock.now())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::decider.isInitialized || event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return
        val packageName = event.packageName?.toString() ?: return

        // Los cambios de contenido de ventanas ajenas a la activa (toasts, teclado) no cuentan.
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            rootInActiveWindow?.packageName?.toString() != packageName
        ) return
        // Al volver al inicio, Google (el buscador del lanzador) manda un evento suelto sin tener ninguna ventana en pantalla: no es una app en uso.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isGhostWindowEvent(packageName)) return
        val now = clock.now()

        if (guardProtectedScreens(packageName, event.className?.toString(), type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, now)) return

        // A16: en pantalla dividida, la ventana bloqueada cierra ambas aunque la activa esté permitida.
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && blockedAppInSplitScreen()) return

        val state = currentState(packageName)
        // Teclado y barra del sistema no cambian lo que se usa; los cambios de contenido tampoco cambian de app.
        if (packageName !in overlayPackages && (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || state.isBrowser)) {
            currentState = state
            // Los diálogos y menús no son actividades de la app: se ignoran para no perder la actividad real.
            if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                event.className?.toString()?.takeIf { it.startsWith(packageName) }?.let { currentActivity = it }
            }
        }
        val decision = decide(state, now, scan = true)
        if (decision is BlockDecision.Block) block(decision, packageName) else guardSubApps(packageName, now)
    }

    /** El evento es de una app sin ninguna ventana visible mientras otra es la activa (ver [WindowEvents.isGhost]). */
    private fun isGhostWindowEvent(packageName: String): Boolean {
        val active = rootInActiveWindow?.packageName?.toString()
        // Sin ventana activa conocida (justo al cambiar de app) no se descarta nada: mejor bloquear de más que dejar pasar.
        if (active == null || active == packageName) return false
        return WindowEvents.isGhost(packageName, active) { windows.mapNotNull { it.root?.packageName?.toString() } }
    }

    /** Si un perfil activo bloquea una sección de esta app (Shorts, Reels…) y está en pantalla, vuelve atrás. */
    private fun guardSubApps(packageName: String, now: Instant) {
        if (!anySubApps || !subAppCatalog.detector.covers(packageName)) return
        val nowMs = now.toEpochMilli()
        if (nowMs - lastSubAppCheckMs < SUBAPP_CHECK_INTERVAL_MS) {
            // Un evento frenado no se pierde: se repite una vez cuando acaba el intervalo.
            handler.removeCallbacks(recheckSubApps)
            handler.postDelayed(recheckSubApps, SUBAPP_CHECK_INTERVAL_MS)
            return
        }
        lastSubAppCheckMs = nowMs
        val enabled = decider.activeSubApps(profiles, now, usage, currentDeviceContext(), emergency.activeUntil, targetUsage)
        if (enabled.isEmpty()) return
        val root = rootInActiveWindow ?: return
        val rule = subAppCatalog.detector.detect(
            enabled.keys, packageName, currentActivity,
            hasViewId = { root.findAccessibilityNodeInfosByViewId(it).isNotEmpty() },
            hasText = { root.findAccessibilityNodeInfosByText(it).isNotEmpty() },
        ) ?: return
        // Un «atrás» por segundo como máximo evita bucles, sin frenar un bloqueo de app que llegue a la vez.
        if (nowMs - lastSubAppBlockMs < MIN_BLOCK_INTERVAL_MS) return
        lastSubAppBlockMs = nowMs
        record(BlockDecision.Block(BlockReason.SUB_APP, enabled.getValue(rule.id), rule.label))
        performGlobalAction(GLOBAL_ACTION_BACK)
        Toast.makeText(this, getString(R.string.lock_reason_subapp, rule.label), Toast.LENGTH_SHORT).show()
    }

    private fun closeProtectedScreen(target: String, reason: BlockReason) {
        val nowMs = clock.nowMs()
        if (nowMs - lastGuardMs < MIN_BLOCK_INTERVAL_MS) return
        lastGuardMs = nowMs

        val block = BlockDecision.Block(reason, profileId = 0, target = target)
        record(block)
        showLockScreen(block)
        // Atrás, luego inicio (ESPECIFICACION 4).
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, BACK_INTERVAL_MS)
    }

    /**
     * Cierra las pantallas protegidas: ajustes que desactivan o desinstalan la app, recientes y pantalla
     * dividida (modo estricto activo), y fecha y hora (también con aprobaciones activadas, porque mover
     * el reloj acorta esperas). Devuelve true si actuó.
     */
    private fun guardProtectedScreens(packageName: String, className: String?, windowChanged: Boolean, now: Instant): Boolean {
        val config = strictRepository.config.value
        val strictActive = StrictMode.isActive(config, clock.localAt(now), now)
        val panicActive = ProfileActivation.isPanicActive(profiles, now)
        // Durante el pánico se cierran también los ajustes que apagan la app, como con la protección del modo estricto.
        val protections = buildSet {
            if (strictActive) addAll(config.protections)
            // Las protecciones que se eligieron para el compañero valen siempre que haya uno, con o sin modo estricto.
            if (partnerRepository.isActive) addAll(settings.partnerPanel.value.active(settings.parentalMode.value).protections)
            if (panicActive) add(StrictProtection.SETTINGS_GUARD)
        }
        val settingsGuarded = StrictProtection.SETTINGS_GUARD in protections
        val clockGuarded = settingsGuarded || approvals.mode.value != ApprovalMode.NONE

        // El nombre de la actividad basta para fecha y hora y no cuesta recorrer la pantalla.
        if (clockGuarded && SensitiveScreens.isDateTimeSettings(ScreenInfo(packageName, emptyList(), emptySet(), className))) {
            closeProtectedScreen("fecha y hora", BlockReason.CLOCK_PROTECTED)
            return true
        }

        // Fuera del modo estricto solo se mira la pantalla al abrir una ventana nueva de Ajustes, no en cada scroll.
        val mightBeSensitive =
            (SensitiveScreens.isSettingsPackage(packageName) && (settingsGuarded || StrictProtection.BATTERY_SAVER in protections || StrictProtection.TASK_MANAGER in protections)) ||
                (clockGuarded && windowChanged && SensitiveScreens.isPlatformSettings(packageName)) ||
                (StrictProtection.RECENTS in protections && packageName in neverBlock)
        // El sistema genera muchos eventos de ventana: ahí solo se busca lo que importa, sin recorrer la pantalla.
        val shellGuarded = windowChanged && SensitiveScreens.isSystemShell(packageName) &&
            (StrictProtection.POWER_MENU in protections || StrictProtection.NOTIFICATION_SHADE in protections)
        // El diálogo de compra se reconoce por el paquete y la actividad: no hace falta leer la pantalla.
        val purchaseGuarded = windowChanged && StrictProtection.PURCHASE_DIALOGS in protections && SensitiveScreens.isBillingPackage(packageName)
        val screen = when {
            mightBeSensitive -> readScreen(packageName, className)
            purchaseGuarded -> ScreenInfo(packageName, emptyList(), emptySet(), className)
            shellGuarded -> readSystemShell(packageName, className)
            else -> null
        }

        // Desactivar el administrador permitiría desinstalar la app, y apagar este servicio deja sin vigilar la pantalla que lo desactiva: las dos pantallas
        // se cierran, con o sin modo estricto, salvo con un permiso vigente.
        if (windowChanged && SensitiveScreens.isSettingsPackage(packageName) &&
            UninstallGuard.shouldGuard(permissionChecker.isGranted(Permission.ADMIN), settings.adminOffGrantUntilMs.value, now.toEpochMilli())
        ) {
            val guardScreen = screen ?: readScreen(packageName, className)
            val target = when {
                SensitiveScreens.isDeviceAdminScreen(guardScreen, appLabel) -> GuardTargets.ADMIN
                SensitiveScreens.isAccessibilityScreen(guardScreen, appLabel) -> GuardTargets.ACCESSIBILITY
                else -> null
            }
            if (target != null) {
                closeProtectedScreen(target, BlockReason.ADMIN_PROTECTED)
                return true
            }
        }

        // Consultar las ventanas es caro: solo cuando cambia una ventana, no en cada cambio de contenido.
        val split = windowChanged && StrictProtection.SPLIT_SCREEN in protections && isSplitScreen()
        val protectedScreen = screen?.let { SensitiveScreens.classify(it, appLabel) }?.takeIf { it in protections }
        when {
            screen != null && clockGuarded && SensitiveScreens.isDateTimeSettings(screen) ->
                closeProtectedScreen("fecha y hora", BlockReason.CLOCK_PROTECTED)
            protectedScreen != null -> {
                // En los ajustes de la propia app, ofrecer «Desinstalar» es otro intento que el de entrar en los demás ajustes.
                val uninstall = protectedScreen == StrictProtection.SETTINGS_GUARD && screen != null && SensitiveScreens.isUninstallScreen(screen)
                closeProtectedScreen(if (uninstall) GuardTargets.UNINSTALL else protectedScreen.screenName, BlockReason.STRICT_MODE)
            }
            split -> closeProtectedScreen(StrictProtection.SPLIT_SCREEN.screenName, BlockReason.STRICT_MODE)
            else -> return false
        }
        return true
    }

    /** Del menú de apagado basta la actividad; del resto, unas pocas búsquedas por identificador. */
    private fun readSystemShell(packageName: String, className: String?): ScreenInfo {
        val root = if (SensitiveScreens.isPowerMenuClass(className)) null else rootInActiveWindow
        val ids = SensitiveScreens.shellViewIds.filterTo(mutableSetOf()) { root?.findAccessibilityNodeInfosByViewId(it)?.isNotEmpty() == true }
        return ScreenInfo(packageName, emptyList(), ids, className)
    }

    /** Textos e identificadores de la ventana activa, acotados para no recorrer árboles enormes. */
    private fun readScreen(packageName: String, className: String? = null): ScreenInfo {
        val texts = mutableListOf<String>()
        val ids = mutableSetOf<String>()
        walkNodes(rootInActiveWindow) { node ->
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
            node.viewIdResourceName?.let(ids::add)
            false
        }
        return ScreenInfo(packageName, texts, ids, className)
    }

    /** Recorre el árbol a lo ancho, con tope de nodos, hasta que [visit] devuelva true. Devuelve si alguno lo hizo. */
    private inline fun walkNodes(root: AccessibilityNodeInfo?, visit: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        root?.let(queue::add)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            if (visit(node)) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return false
    }

    private fun blockedAppInSplitScreen(): Boolean {
        val appWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !it.isInPictureInPictureMode }
        if (appWindows.size < 2) return false
        for (window in appWindows) {
            val pkg = window.root?.packageName?.toString() ?: continue
            val decision = decide(stateOf(pkg))
            if (decision is BlockDecision.Block) {
                block(decision, pkg)
                return true
            }
        }
        return false
    }

    private fun isSplitScreen(): Boolean = windows.count {
        it.type == AccessibilityWindowInfo.TYPE_APPLICATION && !it.isInPictureInPictureMode
    } > 1

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        // Si lo apagó el usuario (y no es un reinicio del servicio por una actualización), el compañero lo sabe.
        if (!permissionChecker.isGranted(Permission.ACCESSIBILITY)) partnerEvents.record(PartnerEventType.PROTECTION_OFF, "accesibilidad")
        health.clear()
        blockGate.clear()
        handler.removeCallbacksAndMessages(null)
        val ids = profiles.mapTo(mutableSetOf()) { it.id }
        val snapshot = usage.filterKeys { it in ids }.toMap()
        val targetSnapshot = targetUsage.filterKeys { it.profileId in ids }.toMap()
        // El contador se vacía aquí, en el hilo principal donde se escribe; lo que se guarda va aparte.
        val webs = webCounter.drain()
        val today = clock.localAt(clock.now()).toLocalDate()
        val protectedByDay = protectedTime.drain()
        CoroutineScope(Dispatchers.IO).launch {
            streaks.addProtected(protectedByDay)
            persist(snapshot, targetSnapshot, webs, today)
        }
        imageProbe?.stop()
        runCatching { unregisterReceiver(imageBlockReceiver) }
        removeOverlay()
        deviceContext.stopLocationUpdates()
        deviceContext.setBluetoothWanted(false)
        scope.cancel()
        super.onDestroy()
    }

    /** El estado del teléfono solo se lee si algún perfil depende de él. */
    private fun currentDeviceContext() = if (needsDevice) deviceContext.current() else DeviceContext()

    /** [scan]: solo desde un evento de accesibilidad se vuelve a leer la pantalla; el seguimiento y los sondeos reutilizan lo último que se vio. */
    private fun decide(state: ForegroundState, now: Instant = clock.now(), scan: Boolean = false): BlockDecision {
        val decision = decideProfiles(state, now)
        // El contenido adulto va después de los perfiles: si un perfil ya bloquea, se muestra su motivo. No lo suspende un desbloqueo de emergencia.
        return if (decision is BlockDecision.Allow) decideAdult(state, now, scan) else decision
    }

    /** D3 y D4: la dirección de la barra del navegador y, con un tope de frecuencia, el texto de la pantalla. */
    private fun decideAdult(state: ForegroundState, now: Instant, scan: Boolean): BlockDecision {
        val nowMs = now.toEpochMilli()
        settings.reactivateAdultIfDue(nowMs)
        val match = adultGate.check(
            packageName = state.packageName,
            url = state.url,
            enabled = settings.adultBlockEnabled.value,
            exempt = state.packageName in neverBlock,
            nowMs = nowMs,
            scan = scan,
            filter = adultFilter.filter.value,
            skipText = AdultTextExemptions.skipsText(state.packageName, state.isBrowser, settings.adultTextExemptApps.value),
        ) {
            // Solo la ventana activa: si es de otra app no se sabe nada de esta.
            if (rootInActiveWindow?.packageName?.toString() == state.packageName) readScreen(state.packageName).texts else null
        }
        if (match == null) return BlockDecision.Allow
        // Una web bloqueada por su dirección tiene dominio; una pantalla bloqueada por su texto (o con una dirección ya vieja), solo la app.
        val byUrl = state.url?.let(adultFilter.filter.value::checkUrl) != null
        val host = if (byUrl) UrlNormalizer.hostOrNull(state.url.orEmpty()) else null
        return BlockDecision.Block(
            BlockReason.ADULT_CONTENT, 0, host ?: state.packageName, targetIsApp = host == null,
            detail = match.matched.take(ADULT_DETAIL_WORDS).joinToString(", "),
        )
    }

    private fun decideProfiles(state: ForegroundState, now: Instant): BlockDecision =
        decider.decide(
            state, profiles, now, usage, currentDeviceContext(), emergency.activeUntil, targetUsage,
            webViewApp = { hasWebView(state.packageName) },
            newAppUntil = settings.newApps.value.takeIf { it.enabled }
                ?.let { NewApps.blockedUntil(it, installTimes.of(state.packageName, now.toEpochMilli()), now) },
        )

    private fun newDecider(dayStartMinute: Int) = BlockDecider(neverBlock, dayStartMinute = dayStartMinute, categories = categories)

    /**
     * ¿Enseña esta app una vista web ahora y el ajuste de navegadores integrados la alcanza (B8)? Solo se mira la ventana
     * activa, y el resultado se recuerda un momento: recorrer la pantalla en cada decisión sería caro.
     */
    private fun hasWebView(packageName: String): Boolean {
        if (!settings.embeddedBrowsers.value.applies(packageName) || browsers.isBrowser(packageName)) return false
        val nowMs = SystemClock.elapsedRealtime()
        webViewChecks[packageName]?.takeIf { nowMs - it.first < WEB_VIEW_CHECK_MS }?.let { return it.second }
        // Solo cuenta la ventana activa. Si es de otra app no se sabe nada de esta, y no se recuerda: no es un «no».
        val root = rootInActiveWindow?.takeIf { it.packageName?.toString() == packageName } ?: return false
        val found = walkNodes(root) { it.className?.toString() == EmbeddedBrowsers.WEB_VIEW_CLASS }
        webViewChecks[packageName] = nowMs to found
        if (found) settings.addDetectedWebViewApp(packageName)
        return found
    }

    /**
     * Intenta cerrar la ventana flotante (imagen en imagen) de una app bloqueada pulsando su botón de cerrar (A7).
     * Android no tiene una acción para esto: se busca el botón por su descripción y puede no encontrarse.
     */
    private fun closeFloatingWindow(packageName: String) {
        val root = windows.filter { it.isInPictureInPictureMode }.firstNotNullOfOrNull { it.root?.takeIf { r -> r.packageName?.toString() == packageName } } ?: return
        walkNodes(root) { node ->
            val description = node.contentDescription?.toString()
            val isClose = node.isClickable && description != null && CLOSE_WORDS.any { description.contains(it, ignoreCase = true) }
            if (isClose) node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            isClose
        }
    }

    /** Una app sin dirección: la web de un navegador no se conoce fuera de primer plano. */
    private fun stateOf(packageName: String) = ForegroundState(packageName, url = null, isBrowser = browsers.isBrowser(packageName))

    private fun currentState(packageName: String): ForegroundState {
        val isBrowser = browsers.isBrowser(packageName)
        val url = if (isBrowser) readAddressBar(packageName) else null
        return ForegroundState(packageName, url, isBrowser)
    }

    private fun readAddressBar(packageName: String): String? {
        val viewId = browsers.addressBarViewId(packageName) ?: return null
        return rootInActiveWindow
            ?.findAccessibilityNodeInfosByViewId(viewId)
            ?.firstOrNull()
            ?.text
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    private fun block(decision: BlockDecision.Block, packageName: String) {
        val now = clock.nowMs()
        // Evita bucles: no más de un bloqueo por segundo (ESPECIFICACION 6).
        if (now - lastBlockAtMs < MIN_BLOCK_INTERVAL_MS) return
        lastBlockAtMs = now

        record(decision)
        showLockScreen(decision)
        // El objetivo es el propio paquete cuando se bloquea la app entera, sea cual sea el motivo (también por límite).
        if (browsers.isBrowser(packageName) && decision.target != packageName) {
            closeBrowserTab(packageName)
        } else {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        if (settings.foldFloating.value) handler.postDelayed({ closeFloatingWindow(packageName) }, FLOATING_CLOSE_DELAY_MS)
    }

    /** Atrás tres veces separadas 500 ms; si la web sigue bloqueada a los 5 s, al inicio. */
    private fun closeBrowserTab(packageName: String) {
        repeat(BACK_PRESSES) { i ->
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, i * BACK_INTERVAL_MS)
        }
        handler.postDelayed({
            val stillBlocked = decide(currentState(packageName)) is BlockDecision.Block
            if (stillBlocked) performGlobalAction(GLOBAL_ACTION_HOME)
        }, HOME_FALLBACK_MS)
    }

    /** Solo las pantallas de bloqueo de un perfil ofrecen el desbloqueo de emergencia, y solo si queda cupo. */
    private fun emergencyAvailable(reason: BlockReason): Boolean {
        return reason in EmergencyUnlock.ELIGIBLE_REASONS && emergency.availability(clock.now(), profiles) is EmergencyAvailability.Available
    }

    /** «Volver» en una pantalla de bloqueo: quita la ventana y, si era una web, saca al navegador de esa página para no entrar en bucle. */
    private fun dismissLock(decision: BlockDecision.Block) {
        removeOverlay()
        val state = currentState ?: return
        if (state.isBrowser && decision.reason in WEB_REASONS) leaveBlockedPage(state.packageName)
    }

    /**
     * Atrás hasta que la página ya no esté bloqueada; si no basta, una página en blanco; y si tampoco, al inicio.
     * Sin esto «Volver» devolvía al navegador a la misma web, que se bloqueaba otra vez, y no se podía usar el navegador.
     */
    private fun leaveBlockedPage(packageName: String) {
        var attempt = 0
        val step = object : Runnable {
            override fun run() {
                if (decide(currentState(packageName)) !is BlockDecision.Block) return
                attempt++
                when {
                    attempt <= BACK_PRESSES -> {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        handler.postDelayed(this, BACK_INTERVAL_MS)
                    }
                    attempt == BACK_PRESSES + 1 && openBlankPage(packageName) -> handler.postDelayed(this, BLANK_WAIT_MS)
                    else -> performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
        }
        handler.post(step)
    }

    /** Escribe `about:blank` en la barra de direcciones y la envía. Devuelve si se pudo (la tecla Intro sin teclado exige Android 11). */
    private fun openBlankPage(packageName: String): Boolean {
        val viewId = browsers.addressBarViewId(packageName) ?: return false
        val bar = rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull() ?: return false
        if (!bar.isEditable) return false
        bar.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val text = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "about:blank") }
        if (!bar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) return false
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && bar.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    /** Cómo se llama un perfil en la pantalla de bloqueo: los de sistema por su tipo, los del usuario por su emoji y título. */
    private fun blockerLabel(profile: Profile): String = when (profile.kind) {
        ProfileKind.QUICK_BLOCK -> getString(R.string.lock_blocker_quick)
        ProfileKind.POMODORO -> getString(R.string.lock_blocker_pomodoro)
        // Ayúdame y Dormir son el mismo tipo de bloqueo: cada uno se nombra por su título, no como «botón de pánico» (que el usuario no pulsó).
        ProfileKind.PANIC -> profile.title.takeIf { it.isNotBlank() } ?: getString(R.string.lock_blocker_panic)
        ProfileKind.NORMAL -> listOf(profile.emoji, profile.title).filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun showLockScreen(decision: BlockDecision.Block) {
        removeOverlay()
        val showEmergency = emergencyAvailable(decision.reason)
        val name = appLabel(decision.target)
        val (named, more) = LockBlockers.limit(LockBlockers.of(decision, profiles).map(::blockerLabel))
        val content = LockScreenContent.of(
            context = this, reason = decision.reason, target = decision.target, availableAt = decision.availableAt,
            displayName = name, style = lockStyle.style.value, now = clock.now(), blockers = named, moreBlockers = more, detail = decision.detail,
        )
        val report = WrongBlockReports.of(decision.reason, decision.target, decision.targetIsApp)
        val view = LockScreenView(this, content, showEmergency, onDismiss = { dismissLock(decision) }, onReport = report?.let { r -> { reportWrongBlock(r) } })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        try {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).addView(view, params)
            overlay = view
            handler.removeCallbacks(removeOverlayRunnable)
            // Se queda hasta que el usuario pulse «Volver»; el tope largo solo evita una ventana huérfana si el servicio pierde el evento.
            handler.postDelayed(removeOverlayRunnable, OVERLAY_SAFETY_MS)
        } catch (e: RuntimeException) {
            // Plan B: actividad propia con el mismo contenido.
            startActivity(LockActivity.intent(this, decision, name, showEmergency))
        }
    }

    /** B10: el usuario dice que este bloqueo es un error. Un solo envío a la vez; sale solo el dominio o el paquete, y solo con cuenta. */
    private fun reportWrongBlock(report: WrongBlockReport) {
        if (reporting) return
        reporting = true
        scope.launch {
            try {
                val message = when (wrongBlocks.send(report)) {
                    WrongBlockReporter.Outcome.SENT -> R.string.lock_report_thanks
                    WrongBlockReporter.Outcome.NEEDS_ACCOUNT -> R.string.lock_report_needs_account
                    WrongBlockReporter.Outcome.FAILED -> R.string.lock_report_failed
                }
                Toast.makeText(this@FocusAccessibilityService, message, Toast.LENGTH_LONG).show()
            } finally {
                reporting = false
            }
        }
    }

    private var reporting = false

    private fun record(decision: BlockDecision.Block) {
        val event = BlockEvent(clock.now(), decision.profileId, decision.reason, decision.target)
        val partnerEvent = PartnerEvents.classify(decision.reason, decision.target)
        CoroutineScope(Dispatchers.IO).launch {
            blockLog.record(event)
            partnerEvent?.let { partnerEvents.record(it, decision.target) }
        }
    }

    private fun removeOverlay() {
        handler.removeCallbacks(removeOverlayRunnable)
        val view = overlay ?: return
        overlay = null
        runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view) }
    }

    /**
     * Cada segundo suma el uso de los perfiles con límite y vuelve a decidir, para bloquear
     * en cuanto el límite se agota aunque no haya ningún evento de accesibilidad.
     */
    private suspend fun trackUsage() {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        var lastSaveMs = 0L
        var lastBeatMs = 0L
        var lastEndCheckMs = 0L
        while (scope.isActive) {
            delay(TRACK_INTERVAL_MS)
            val now = clock.now()
            val nowMs = now.toEpochMilli()
            if (nowMs - lastBeatMs >= BEAT_INTERVAL_MS) {
                lastBeatMs = nowMs
                health.beat(now)
            }
            val state = currentState
            val localNow = clock.localAt(now)
            val context = lazy { currentDeviceContext() }
            val interactive = power.isInteractive
            for (profile in profiles) {
                val limit = profile.limit ?: continue
                val inUse = interactive && state != null && UsageTargets.counts(profile, state, neverBlock, categories, localNow, now, { context.value })
                val periodStartMs = Periods.startMs(limit.period, localNow, settings.dayStartMinute.value)
                if (limit.scope == LimitScope.PER_TARGET) {
                    tickTargets(profile.id, if (inUse && state != null) ProfileTargets.counterOf(profile, state, categories) else null, nowMs, periodStartMs)
                } else {
                    usage[profile.id] = UsageAccumulator.tick(usage[profile.id] ?: ProfileUsage(), nowMs, inUse, periodStartMs)
                }
            }
            updateLimitStatus(state, interactive, now, localNow, nowMs) { context.value }
            if (state != null) {
                val decision = decide(state, now)
                if (decision is BlockDecision.Block) block(decision, state.packageName)
            }
            // Bloqueo activo para las rachas: el filtro de contenido adulto encendido, o algún perfil bloqueando ahora sin un desbloqueo de emergencia vigente.
            protectedTime.tick(nowMs, settings.adultBlockEnabled.value || decider.isProtecting(profiles, now, usage, context.value, emergency.activeUntil, targetUsage), streaks.dayOf(now))
            val nudge = settings.nudge.value
            nudgeTracker.tick(nowMs, nudge.enabled && interactive && state != null && state.packageName in nudge.apps)
            recordWebs(settings.stats.value, state, interactive, nowMs)
            announcePomodoroPhases(now)
            if (nowMs - lastEndCheckMs >= END_CHECK_INTERVAL_MS) {
                lastEndCheckMs = nowMs
                endQuickBlocksWhoseConditionIsMet(context.value)
            }
            if (interactive) {
                announceUpcoming(now, localNow)
                announceNudge(nowMs)
            }
            if (nowMs - lastPurgeMs >= PURGE_INTERVAL_MS) {
                lastPurgeMs = nowMs
                installTimes.clear()
                webViewChecks.clear()
                announceFinishedQuickBlocks(repository.purgeExpired(now))
                blockLog.prune(now.minusSeconds(RETENTION_DAYS * 86_400L))
                runCatching { notificationLog.prune(nowMs) }
                if (lastWebPruneDay != localNow.toLocalDate()) {
                    webStats.prune(localNow.toLocalDate().minusDays(RETENTION_DAYS))
                    runCatching { pauses.prune(now.minusSeconds(RETENTION_DAYS * 86_400L)) }
                    lastWebPruneDay = localNow.toLocalDate()
                }
            }
            if (nowMs - lastSaveMs >= SAVE_INTERVAL_MS) {
                lastSaveMs = nowMs
                saveUsage()
            }
        }
    }

    /** Suma un segundo al contador de [counter] y deja de contar los demás del perfil (así una vuelta a ellos cuenta como apertura). */
    private fun tickTargets(profileId: Long, counter: String?, nowMs: Long, periodStartMs: Long) {
        for (key in targetUsage.keys.filter { it.profileId == profileId && it.target != counter }) {
            targetUsage[key] = UsageAccumulator.tick(targetUsage.getValue(key), nowMs, false, periodStartMs)
        }
        if (counter == null) return
        val key = UsageKey(profileId, counter)
        targetUsage[key] = UsageAccumulator.tick(targetUsage[key] ?: ProfileUsage(), nowMs, true, periodStartMs)
    }

    /**
     * Mientras se usa algo con límite, una notificación fija dice cuánto llevas y cuánto queda. Si hay varios límites a la vez,
     * enseña el que está más cerca de agotarse. Se actualiza cada pocos segundos, no cada segundo.
     */
    private fun updateLimitStatus(state: ForegroundState?, interactive: Boolean, now: Instant, localNow: LocalDateTime, nowMs: Long, context: () -> DeviceContext) {
        val show = settings.notices.value.limitStatus && interactive && state != null
        if (!show) {
            clearLimitStatus()
            return
        }
        // Al cambiar de app se actualiza al momento; si no, cada pocos segundos.
        if (state!!.packageName == lastLimitStatusPackage && nowMs - lastLimitStatusMs < LIMIT_STATUS_INTERVAL_MS) return
        lastLimitStatusMs = nowMs
        lastLimitStatusPackage = state.packageName
        val dayStart = settings.dayStartMinute.value
        val closest = profiles.mapNotNull { profile ->
            val limit = profile.limit ?: return@mapNotNull null
            if (!UsageTargets.counts(profile, state!!, neverBlock, categories, localNow, now, context)) return@mapNotNull null
            val counter = ProfileTargets.counterOf(profile, state, categories)
            val used = if (limit.scope == LimitScope.PER_TARGET) counter?.let { targetUsage[UsageKey(profile.id, it)] } else usage[profile.id]
            Triple(profile, counter ?: state.packageName, LimitStatus.of(limit, used, nowMs, localNow, dayStart))
        }.filterNot { it.third.exhausted }.minByOrNull { it.third.left.toFloat() / it.third.amount }
        if (closest == null) {
            clearLimitStatus()
            return
        }
        val (profile, target, status) = closest
        val name = if (target == state!!.packageName) appLabel(target) else target
        notifier.showLimitStatus("$name · ${blockerLabel(profile)}", status.describe())
        limitStatusShown = true
    }

    private fun clearLimitStatus() {
        if (!limitStatusShown) return
        limitStatusShown = false
        notifier.clearLimitStatus()
    }

    /**
     * La app en primer plano. Si todavía no llegó ningún cambio de ventana (por ejemplo, justo después de que el servicio arrancara), se pregunta a la ventana
     * activa: sin esto, el bloqueo por imágenes se descartaba en silencio por no saber qué cerrar.
     */
    private fun foregroundState(): ForegroundState? =
        currentState ?: rootInActiveWindow?.packageName?.toString()?.takeIf { it !in overlayPackages }?.let(::stateOf)

    /** La detección de imágenes vio contenido explícito en [packageName]: se cierra con la pantalla de bloqueo de siempre (y el compañero lo sabe, como con cualquier bloqueo). */
    private fun blockImageContent(packageName: String?) {
        val pkg = packageName ?: return
        block(BlockDecision.Block(BlockReason.IMAGE_CONTENT, 0, pkg, targetIsApp = true), pkg)
    }

    /** La detección de imágenes vio algo sugerente: se ofrece bloquearlo con una notificación (si el sistema deja mostrarla). */
    private fun offerImageBlock() {
        if (!notifier.canNotify()) return
        runCatching { notifier.notifyImageOffer() }
    }

    /** «Bloquear ahora» en la notificación: cierra lo que haya en primer plano. */
    private val imageBlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            notifier.cancelImageOffer()
            blockImageContent(foregroundState()?.packageName)
        }
    }

    /** Levanta los Bloquear ya cuya salida (lugar, cargador, Wi-Fi, Bluetooth) se cumple ahora, y avisa de que terminaron. */
    private suspend fun endQuickBlocksWhoseConditionIsMet(context: DeviceContext) {
        val watched = profiles.filter(EndRules::watches)
        if (watched.isEmpty()) return
        val met = watched.filter { EndRules.met(it, context) }
        met.forEach { repository.endByCondition(it.id) }
        announceFinishedQuickBlocks(met)
    }

    /** Avisa antes de que empiece un horario o se agote un límite, una sola vez por suceso. */
    private suspend fun announceUpcoming(now: Instant, localNow: LocalDateTime) {
        if (now.toEpochMilli() - lastNoticeMs < NOTICE_INTERVAL_MS) return
        lastNoticeMs = now.toEpochMilli()
        if (!notifier.canNotify()) return
        // El estado de la racha recorre todos los días guardados: solo se calcula con sus avisos encendidos.
        val streak = if (settings.notices.value.streak) {
            runCatching { Streaks.state(streaks.records.first(), streaks.dayOf(now), streaks.config.value) }.getOrNull()
        } else null
        for (notice in noticeLedger.unseen(Notices.due(profiles, usage, now, localNow, settings.notices.value, settings.dayStartMinute.value, streak = streak, habits = habitInputs(now)))) {
            // El resumen semanal cuenta los bloqueos justo al publicarlo, no antes. Si algo falla, el aviso se reintenta en el siguiente ciclo.
            val ready = if (notice.kind == NoticeKind.WEEKLY_REPORT) {
                val count = runCatching { blockLog.countSince(now.minusSeconds(7 * 86_400L)) }.getOrNull() ?: continue
                notice.copy(count = count)
            } else notice
            if (runCatching { notifier.notifyNotice(ready) }.isSuccess) noticeLedger.markSeen(notice)
        }
    }

    /** Los recordatorios propios y la pregunta de ánimo; esta solo sale si hoy aún no se ha respondido. */
    private fun habitInputs(now: Instant): HabitInputs {
        // Sin saber aún si hoy hay un ánimo apuntado (el primer dato llega al arrancar), la pregunta espera al siguiente ciclo.
        val moodPrompt = settings.moodPrompt.value.takeIf { moodLoaded } ?: MoodPromptSettings()
        if (reminders.isEmpty() && !moodPrompt.enabled) return HabitInputs()
        return HabitInputs(reminders, moodPrompt, moodLoggedToday = lastMoodDay == streaks.dayOf(now))
    }

    /** Aviso de tiempo (K7): sin bloquear, cuando el uso de las apps elegidas suma 15 minutos en la última hora. */
    private fun announceNudge(nowMs: Long) {
        // Primero se comprueba que se puede avisar: `shouldNudge` da el aviso por hecho y empieza la espera de una hora.
        if (!settings.nudge.value.enabled || !notifier.canNotify() || !nudgeTracker.shouldNudge(nowMs)) return
        runCatching { notifier.notifyNotice(Notice("nudge:$nowMs", NoticeKind.TIME_NUDGE, minutes = nudgeTracker.usedMinutes())) }
    }

    /** Un bloqueo rápido se borra al caducar: este es el único momento en que se puede avisar de que terminó. */
    private fun announceFinishedQuickBlocks(expired: List<Profile>) {
        if (!settings.notices.value.scheduleEnded || !notifier.canNotify()) return
        expired.filter { it.kind == ProfileKind.QUICK_BLOCK }.forEach {
            runCatching { notifier.notifyNotice(Notice("quickend:${it.id}:${it.activeUntil}", NoticeKind.QUICK_BLOCK_ENDED, it.id, it.title)) }
        }
    }

    /** Avisa al cambiar de fase del Pomodoro. La primera observación de cada sesión no avisa. */
    private fun announcePomodoroPhases(now: Instant) {
        profiles.filter { it.pomodoro != null }.forEach { profile ->
            val plan = profile.pomodoro ?: return@forEach
            val phase = Pomodoro.phaseAt(plan, now)
            val key = when (phase) {
                is PomodoroPhase.Focus -> "focus${phase.session}"
                is PomodoroPhase.Break -> "break${phase.session}"
                PomodoroPhase.NotStarted -> "notstarted"
                PomodoroPhase.Finished -> "finished"
            }
            val previous = lastPhase.put(profile.id, key)
            if (previous == null || previous == key) return@forEach
            val (title, text) = when (phase) {
                is PomodoroPhase.Focus -> "Foco ${phase.session}/${plan.sessions}" to "A concentrarse ${plan.focusMinutes} minutos."
                is PomodoroPhase.Break -> "Descanso" to "Tómate ${plan.breakMinutes} minutos."
                PomodoroPhase.Finished -> "Pomodoro terminado" to "Completaste ${plan.sessions} ${if (plan.sessions == 1) "sesión" else "sesiones"}."
                PomodoroPhase.NotStarted -> return@forEach
            }
            notifier.notifySession(NOTIFICATION_ID_BASE + profile.id.toInt(), title, text)
        }
        lastPhase.keys.retainAll(profiles.mapTo(mutableSetOf()) { it.id })
    }

    private suspend fun saveUsage() {
        val ids = profiles.mapTo(mutableSetOf()) { it.id }
        val snapshot = usage.filterKeys { it in ids }.toMap()
        val targetSnapshot = targetUsage.filterKeys { it.profileId in ids }.toMap()
        val webs = webCounter.drain()
        val protectedByDay = protectedTime.drain()
        val today = clock.localAt(clock.now()).toLocalDate()
        withContext(Dispatchers.IO) {
            // Primero el tiempo protegido y por separado: un fallo al guardar el uso no lo pierde, y si él falla se devuelve al acumulador.
            if (!streaks.addProtected(protectedByDay)) protectedTime.putBack(protectedByDay)
            persist(snapshot, targetSnapshot, webs, today)
        }
    }

    /** Guarda el uso de los límites y las estadísticas de webs. Lo de las webs se asigna al día del volcado (como mucho 10 s de desfase). */
    private suspend fun persist(usageSnapshot: Map<Long, ProfileUsage>, targetSnapshot: Map<UsageKey, ProfileUsage>, webs: Map<WebKey, WebUsage>, today: LocalDate) {
        try {
            usageRepository.saveAll(usageSnapshot)
            usageRepository.saveTargets(targetSnapshot)
        } finally {
            webStats.add(today, webs)
        }
    }

    /** Cuenta la web que se ve en el navegador, según qué estadísticas haya activado el usuario (K2). */
    private fun recordWebs(stats: StatsSettings, state: ForegroundState?, interactive: Boolean, nowMs: Long) {
        val browser = state?.takeIf { stats.any && interactive && it.isBrowser }
        val observation = when {
            browser == null -> WebObservation.None
            else -> when (isIncognito(browser.packageName, nowMs)) {
                true -> WebObservation.Incognito
                false -> WebObservation.Web(UrlNormalizer.hostOrNull(browser.url))
                // Sin poder mirar la pantalla no se sabe: por privacidad no se guarda el dominio.
                null -> WebObservation.None
            }
        }
        webCounter.record(observation, stats, nowMs)
    }

    /**
     * ¿Está el navegador en incógnito? Null si no se puede mirar. Solo se recuerda el sí, unos segundos: salir de
     * incógnito contado de más es inocuo, pero contar una pestaña de incógnito como normal guardaría su dominio.
     */
    private fun isIncognito(packageName: String, nowMs: Long): Boolean? {
        val viewId = browsers.incognitoViewId(packageName) ?: return false
        if (incognitoSeen?.let { (pkg, at) -> pkg == packageName && nowMs - at < INCOGNITO_CHECK_MS } == true) return true
        val root = rootInActiveWindow ?: return null
        val found = root.findAccessibilityNodeInfosByViewId(viewId).isNotEmpty()
        incognitoSeen = if (found) packageName to nowMs else null
        return found
    }

    /**
     * Respaldo (ESPECIFICACION 1.1): consulta los eventos de uso cada 300 ms con la pantalla
     * encendida y algún perfil activo, por si accesibilidad pierde un cambio de ventana.
     * No lee URL: las webs las decide solo el servicio de accesibilidad.
     */
    private suspend fun pollForegroundFallback() {
        val checker = PermissionChecker(this)
        val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        var lastQueryEnd = 0L
        var foreground: String? = null

        while (scope.isActive) {
            delay(POLL_INTERVAL_MS)
            if (!power.isInteractive || profiles.none { it.enabled } || !checker.isGranted(Permission.USAGE_STATS)) {
                lastQueryEnd = 0L // al volver, se recalcula desde cero
                continue
            }
            // UsageStats guarda sus eventos con la hora del teléfono, no con la de confianza.
            val now = System.currentTimeMillis()
            val from = if (lastQueryEnd == 0L) now - USAGE_WINDOW_MS else lastQueryEnd - 1_000L
            lastQueryEnd = now

            val events = usageStats.queryEvents(from, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) foreground = event.packageName
            }

            val packageName = foreground ?: continue
            val decision = decide(stateOf(packageName))
            if (decision is BlockDecision.Block) block(decision, packageName)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 300L
        const val MAX_NODES = 300
        const val TRACK_INTERVAL_MS = 1_000L
        const val LIMIT_STATUS_INTERVAL_MS = 10_000L
        const val SAVE_INTERVAL_MS = 10_000L
        const val BEAT_INTERVAL_MS = 15_000L
        const val NOTICE_INTERVAL_MS = 15_000L
        const val PURGE_INTERVAL_MS = 60_000L
        const val END_CHECK_INTERVAL_MS = 5_000L
        const val INCOGNITO_CHECK_MS = 3_000L
        const val WEB_VIEW_CHECK_MS = 1_500L
        const val FLOATING_CLOSE_DELAY_MS = 600L
        val CLOSE_WORDS = listOf("close", "cerrar", "dismiss", "descartar")
        const val RETENTION_DAYS = 35L
        const val NOTIFICATION_ID_BASE = 1_000
        const val USAGE_WINDOW_MS = 60 * 60 * 1000L
        const val MIN_BLOCK_INTERVAL_MS = 1_000L
        const val SUBAPP_CHECK_INTERVAL_MS = 300L
        const val BACK_PRESSES = 3
        const val BACK_INTERVAL_MS = 500L
        const val HOME_FALLBACK_MS = 5_000L
        const val OVERLAY_SAFETY_MS = 10 * 60 * 1000L
        const val BLANK_WAIT_MS = 800L
        /** Cuántas coincidencias del filtro adulto enseña la pantalla de bloqueo. */
        const val ADULT_DETAIL_WORDS = 4

        /** Motivos que vienen de la página que enseña un navegador. */
        val WEB_REASONS = setOf(BlockReason.PROFILE_WEB, BlockReason.ADULT_CONTENT, BlockReason.NOT_ALLOWED)
    }
}
