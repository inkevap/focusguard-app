package com.focusguard.ui

import androidx.compose.material3.SnackbarHostState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.focusguard.model.ApprovalMode
import com.focusguard.model.BlockMode
import com.focusguard.model.ConditionMode
import com.focusguard.model.GeoFence
import com.focusguard.model.LimitKind
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.StrictState
import com.focusguard.model.UsageLimit
import com.focusguard.model.WebRule
import com.focusguard.lock.LockScreenContent
import com.focusguard.lock.LockScreenView
import com.focusguard.model.LockStyle
import java.time.Instant
import java.time.ZoneOffset
import com.focusguard.model.BlockReason
import com.focusguard.permissions.Permission
import com.focusguard.data.AccountState
import com.focusguard.data.PartnerState
import com.focusguard.ui.account.AccountActions
import com.focusguard.ui.account.AccountContent
import com.focusguard.ui.home.HomeContent
import com.focusguard.ui.home.HomeUiState
import com.focusguard.model.SubAppRule
import com.focusguard.decision.Fix
import com.focusguard.decision.Problem
import com.focusguard.decision.ScheduleConflict
import com.focusguard.decision.Severity
import com.focusguard.ui.protection.TroubleshooterContent
import com.focusguard.decision.EmbeddedBrowserPolicy
import com.focusguard.decision.LimitProgress
import com.focusguard.decision.NewAppsPolicy
import com.focusguard.decision.ProfileStatus
import com.focusguard.decision.NoticeSettings
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.help.HelpScreen
import com.focusguard.ui.components.BreathingPanelContent
import com.focusguard.decision.BreathingPatterns
import com.focusguard.ui.templates.TemplateItem
import com.focusguard.ui.templates.TemplateLists
import com.focusguard.ui.templates.TemplatesContent
import com.focusguard.decision.CategoryTable
import com.focusguard.decision.ProfileTemplate
import com.focusguard.ui.profiles.CategoriesCard
import com.focusguard.ui.profiles.DurationChoiceContent
import com.focusguard.ui.profiles.IntervalsEditor
import com.focusguard.ui.settings.NoticesCard
import com.focusguard.ui.settings.NotificationsCard
import com.focusguard.ui.settings.SavedNotification
import com.focusguard.ui.settings.SavedNotificationsContent
import com.focusguard.ui.settings.StatsCard
import com.focusguard.decision.NotificationSettings
import com.focusguard.decision.StatsSettings
import com.focusguard.ui.profiles.ConditionsCard
import com.focusguard.ui.profiles.ConflictsCard
import com.focusguard.ui.profiles.SubAppsCard
import com.focusguard.ui.protection.ProtectionContent
import com.focusguard.decision.DayRecord
import com.focusguard.decision.StreakConfig
import com.focusguard.ui.streak.StreakContent
import com.focusguard.data.JournalEntryEntity
import com.focusguard.data.MoodEntity
import com.focusguard.decision.MoodPromptSettings
import com.focusguard.decision.NudgeSettings
import com.focusguard.decision.Reminder
import com.focusguard.ui.habits.HabitsContent
import com.focusguard.data.LauncherIcon
import com.focusguard.ui.settings.AppIconContent
import com.focusguard.data.PlannedActivityEntity
import com.focusguard.decision.UrgeOutcome
import com.focusguard.decision.UrgeTrigger
import com.focusguard.ui.journal.JournalContent
import com.focusguard.data.UsageOverview
import com.focusguard.decision.Change
import com.focusguard.decision.DayFigures
import com.focusguard.decision.DayInsight
import com.focusguard.decision.Insights
import com.focusguard.decision.Trend
import com.focusguard.ui.weekly.WeeklyContent
import com.focusguard.ui.weekly.WeeklyUi
import com.focusguard.ui.urge.UrgeFinished
import com.focusguard.ui.urge.UrgeIntro
import com.focusguard.ui.urge.UrgeRunning
import com.focusguard.ui.profiles.BlockMeDialog
import com.focusguard.ui.profiles.PauseCard
import com.focusguard.ui.profiles.PauseRecord
import com.focusguard.ui.profiles.ProfilePreviewContent
import com.focusguard.ui.profiles.ProfilePreviewState
import com.focusguard.ui.protection.EmbeddedBrowsersCard
import com.focusguard.ui.protection.NewAppsCard
import com.focusguard.ui.protection.ProtectionUiState
import com.focusguard.ui.stats.AppUsage
import com.focusguard.ui.stats.BlockedTarget
import com.focusguard.ui.stats.DayStats
import com.focusguard.ui.stats.StatsContent
import com.focusguard.ui.stats.StatsState
import com.focusguard.ui.stats.WebRow
import com.focusguard.ui.theme.FocusGuardTheme
import java.time.LocalDate
import java.time.DayOfWeek
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

/** Capturas de las pantallas principales en claro y oscuro: sirven para revisar el diseño sin dispositivo. */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.Light.NoActionBar")

    private val weekdays = (1..5).map { ScheduleInterval(DayOfWeek.of(it), 9 * 60, 17 * 60) }
    private val sample = listOf(
        Profile(1, "Estudio", "📚", apps = setOf("a", "b", "c", "d"), webs = listOf(WebRule("reddit.com"), WebRule("x.com")), intervals = weekdays),
        Profile(2, "Noche tranquila", "🌙", enabled = false, apps = setOf("a", "b", "c", "d", "e", "f"), intervals = DayOfWeek.entries.map { ScheduleInterval(it, 23 * 60, 7 * 60) }),
        Profile(3, "Juegos", "🎮", apps = setOf("a"), limit = UsageLimit(LimitKind.TIME, 60)),
        Profile(4, "Solo trabajo", "💼", mode = BlockMode.ALLOW_ONLY, apps = setOf("a", "b")),
    )
    private val home = HomeUiState(sample, emptyList(), nowMs = 0, editLocked = false, activeNow = 2, missingPermissions = 0, streak = 12)
    private val protection = ProtectionUiState(adultBlock = true, newApps = NewAppsPolicy(enabled = true, hours = 48), embedded = EmbeddedBrowserPolicy(), detectedWebViewApps = emptySet(), foldFloating = false, approvalMode = ApprovalMode.DELAY_24H, pendingApprovals = 1, strictState = StrictState.INACTIVE, missingPermissions = 1)

    private fun homeContent(state: HomeUiState, dark: Boolean) = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = dark) {
            HomeContent(state, SnackbarHostState(), {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    private fun conditions(profile: Profile, permission: Boolean, dark: Boolean) = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = dark) {
            ConditionsCard(profile, permission, {}, {}, {}, { null }, permission, { listOf("Auriculares de Ana", "Coche") }, {}, {}, {}, { true }, {}, {}, {}, Modifier.padding(16.dp))
        }
    }

    private val atHome = Profile(
        5, "Noche en casa",
        wifiNames = setOf("Casa"), bluetoothNames = setOf("Coche"), requireCharging = true,
        geofence = GeoFence(40.4168, -3.7038, 200), conditionMode = ConditionMode.ANY,
    )

    private fun subApps(selected: Set<String>) = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            SubAppsCard(
                rules = listOf("YouTube Shorts", "Reels de Instagram", "Stories de Instagram", "Spotlight de Snapchat")
                    .mapIndexed { i, label -> SubAppRule("id$i", label, setOf("p")) },
                selected = selected,
                onToggle = { _, _ -> },
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    @Test fun horariosEnConflicto() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            ConflictsCard(
                listOf(ScheduleConflict(2, "Estudio", DayOfWeek.MONDAY, 12 * 60, 13 * 60), ScheduleConflict(3, "Noche tranquila", DayOfWeek.SUNDAY, 23 * 60, 60)),
                Modifier.padding(16.dp),
            )
        }
    }

    private val problems = listOf(Problem(Fix.OVERLAY, Severity.REQUIRED), Problem(Fix.BATTERY, Severity.RECOMMENDED))

    @Test fun solucionadorConProblemas() = paparazzi.snapshot { FocusGuardTheme(darkTheme = false) { TroubleshooterContent(problems, {}, {}) } }

    @Test fun solucionadorTodoEnOrden() = paparazzi.snapshot { FocusGuardTheme(darkTheme = false) { TroubleshooterContent(emptyList(), {}, {}) } }

    private val previewState = ProfilePreviewState(
        profile = sample[0].copy(limit = UsageLimit(LimitKind.TIME, 60), wifiNames = setOf("Casa")),
        status = ProfileStatus.ActiveNow(150),
        limit = LimitProgress(LimitKind.TIME, 42, 60, exhausted = false),
    )

    @Test fun vistaPrevia() = paparazzi.snapshot { FocusGuardTheme(darkTheme = false) { ProfilePreviewContent(previewState, {}) } }

    @Test fun vistaPreviaOscuro() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = true) { ProfilePreviewContent(previewState.copy(status = ProfileStatus.StartsIn(95)), {}) }
    }

    @Test fun pausaEnCurso() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            PauseCard(
                ProfileStatus.Paused(Instant.parse("2026-10-27T10:30:00Z")),
                listOf(PauseRecord(Instant.parse("2026-10-26T16:00:00Z"), 15), PauseRecord(Instant.parse("2026-10-25T09:00:00Z"), 30)),
                {}, {}, enabled = true, modifier = Modifier.padding(16.dp),
            )
        }
    }

    @Test fun pausaDisponible() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { PauseCard(ProfileStatus.ActiveNow(120), emptyList(), {}, {}, enabled = true, modifier = Modifier.padding(16.dp)) }
    }

    @Test fun bloqueMe() = paparazzi.snapshot { FocusGuardTheme(darkTheme = false) { BlockMeDialog({}, {}) } }

    @Test fun navegadoresIntegrados() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            EmbeddedBrowsersCard(
                EmbeddedBrowserPolicy(enabled = true, apps = setOf("com.instagram.android")),
                detected = setOf("com.example.tienda"), {}, Modifier.padding(16.dp), installed = setOf("com.instagram.android", "com.facebook.katana"),
            )
        }
    }

    @Test fun appsNuevas() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { NewAppsCard(NewAppsPolicy(enabled = true, hours = 48), {}, Modifier.padding(16.dp)) }
    }

    @Test fun avisos() = paparazzi.snapshot { FocusGuardTheme(darkTheme = false) { NoticesCard(NoticeSettings(limitNearing = false), {}) } }

    @Test fun selectoresDeHorario() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            AppCard(Modifier.padding(16.dp)) {
                androidx.compose.foundation.layout.Column {
                    IntervalsEditor(weekdays.take(1), { _, _, _ -> }, {})
                }
            }
        }
    }

    @Test fun selectoresDeHorarioOscuro() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = true) {
            AppCard(Modifier.padding(16.dp)) {
                androidx.compose.foundation.layout.Column {
                    IntervalsEditor(emptyList(), { _, _, _ -> }, {})
                }
            }
        }
    }

    @Test fun selectorDeDuracion() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            AppCard(Modifier.padding(16.dp)) {
                DurationChoiceContent("90", {}, listOf(15, 30, 60, 120), 1440, nowMs = Instant.parse("2026-10-28T17:00:00Z").toEpochMilli())
            }
        }
    }

    @Test fun categorias() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { CategoriesCard(setOf("social", "shopping"), { _, _ -> }, Modifier.padding(16.dp)) }
    }

    @Test fun seccionesSinElegir() = subApps(emptySet())

    @Test fun seccionesElegidas() = subApps(setOf("id0", "id2"))

    @Test fun condicionesClaro() = conditions(atHome, permission = true, dark = false)

    @Test fun condicionesOscuro() = conditions(atHome, permission = true, dark = true)

    @Test fun condicionesVacioSinPermiso() = conditions(Profile(6, "Nuevo"), permission = false, dark = false)

    @Test fun inicioClaro() = homeContent(home, dark = false)

    @Test fun inicioOscuro() = homeContent(home, dark = true)

    @Test fun inicioSinPerfiles() = homeContent(home.copy(profiles = emptyList(), activeNow = 0), dark = false)

    @Test fun inicioFaltanPermisos() = homeContent(home.copy(missingPermissions = 3, activeNow = 0), dark = false)

    private val permissions = Permission.entries.associateWith { it == Permission.OVERLAY }
    private val stats = StatsState(
        perDay = (0..6).map { LocalDate.of(2026, 10, 20 + it) to listOf(0, 2, 5, 3, 8, 1, 4)[it] },
        topBlocked = listOf(BlockedTarget("Instagram", 9, "com.instagram.android"), BlockedTarget("reddit.com", 6), BlockedTarget("YouTube", 3, "com.google.android.youtube")),
        days = (0..6).map { LocalDate.of(2026, 10, 21 + it) },
        day = LocalDate.of(2026, 10, 27),
        usage = DayStats(
            topApps = listOf(AppUsage("WhatsApp", 74, 41), AppUsage("Chrome", 51, 12), AppUsage("YouTube", 18, 1)),
            unlocks = 63,
            webs = listOf(WebRow("reddit.com", 7, 32), WebRow("wikipedia.org", 3, 8)),
            incognitoMinutes = 12,
        ),
    )

    @Test fun permisos() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { PermissionsScreen(permissions, {}, {}, {}) }
    }

    @Test fun estadisticas() = paparazzi.snapshot { FocusGuardTheme(darkTheme = false) { StatsContent(stats, {}) } }

    @Test fun estadisticasDeNavegacion() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { StatsCard(StatsSettings(webs = true), {}, {}) }
    }

    @Test fun notificaciones() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { NotificationsCard(NotificationSettings(captureBlocked = true), {}, hasAccess = false, {}, {}) }
    }

    @Test fun notificacionesGuardadas() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            SavedNotificationsContent(
                listOf(
                    SavedNotification(1, "Instagram", Instant.parse("2026-10-27T09:30:00Z"), "Ana", "Te ha enviado una foto"),
                    SavedNotification(2, "WhatsApp", Instant.parse("2026-10-27T08:10:00Z"), "Grupo familia", "¿Quién trae el pan?"),
                ),
                {},
            )
        }
    }

    @Test fun estadisticasSinAccesoDeUso() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { StatsContent(StatsState(perDay = stats.perDay.map { it.first to 0 }, usage = DayStats(usageAvailable = false)), {}) }
    }

    private val accountActions = AccountActions({}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    private fun account(serverUrl: String, state: AccountState, partner: PartnerState = PartnerState.None, notices: Boolean = true, busy: Boolean = false) = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { AccountContent(serverUrl, state, partner, notices, false, emptyList(), busy, accountActions) }
    }

    /** La tarjeta sola: dentro de la pantalla de cuenta queda por debajo de lo que cabe en la captura. */
    @Test fun personasAQuienesAyudas() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) {
                com.focusguard.ui.account.HelpingCard(
                    listOf(
                        com.focusguard.server.HelpedInfo("1", "luis@example.com", mutual = false, pending = 2),
                        com.focusguard.server.HelpedInfo("2", "marta@example.com", mutual = true, pending = 0),
                    ),
                    PartnerState.None, busy = false, actions = accountActions,
                )
            }
        }
    }

    @Test fun cuentaSinServidor() = account("", AccountState())

    @Test fun cuentaSinSesion() = account("https://focusguard.midominio.com", AccountState())

    @Test fun cuentaSinConfirmarElCorreo() = account("https://focusguard.midominio.com", AccountState("ana@example.com", emailVerified = false, deviceRegistered = true))

    @Test fun cuentaConfirmada() = account("https://focusguard.midominio.com", AccountState("ana@example.com", emailVerified = true, deviceRegistered = true))

    @Test fun cuentaCompanieroInvitado() = account(
        "https://focusguard.midominio.com",
        AccountState("ana@example.com", emailVerified = true, deviceRegistered = true),
        PartnerState.Invited("p1", "luis@example.com"),
    )

    @Test fun cuentaCompanieroActivo() = account(
        "https://focusguard.midominio.com",
        AccountState("ana@example.com", emailVerified = true, deviceRegistered = true),
        PartnerState.Active("p1", "luis@example.com"),
    )

    private fun onboarding(step: Int) = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { OnboardingScreen(permissions, {}, {}, { _, _ -> }, {}, startStep = step) }
    }

    @Test fun bienvenida() = onboarding(0)

    @Test fun bienvenidaObjetivo() = onboarding(1)

    @Test fun bienvenidaQueCuidar() = onboarding(2)

    @Test fun bienvenidaCuando() = onboarding(3)

    @Test fun bienvenidaAdulto() = onboarding(4)

    @Test fun bienvenidaCompanero() = onboarding(5)

    @Test fun bienvenidaEstricto() = onboarding(6)

    @Test fun bienvenidaPermisos() = onboarding(7)

    @Test fun bienvenidaPrueba() = onboarding(8)

    private fun templateItem(template: ProfileTemplate, installed: Set<String>) =
        TemplateItem(template, template.toProfile(CategoryTable(mapOf("com.instagram.android" to "social", "com.facebook.katana" to "social"), mapOf("instagram.com" to "social", "x.com" to "social")), installed))

    @Test fun plantillas() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            val social = ProfileTemplate("redes", "📵", "Redes sociales", "Instagram, Facebook, X y compañía, con 45 minutos al día entre todas.", categories = setOf("social"), limit = UsageLimit(LimitKind.TIME, 45))
            val night = ProfileTemplate("noche", "🌙", "Descanso nocturno", "Todos los días de 22:00 a 07:00 solo se permite el reloj.", BlockMode.ALLOW_ONLY, apps = setOf("clock"), intervals = DayOfWeek.entries.map { ScheduleInterval(it, 22 * 60, 7 * 60) })
            TemplatesContent(
                TemplateLists(personal = listOf(templateItem(social, setOf("com.instagram.android"))), generic = listOf(templateItem(night, setOf("clock"))), loaded = true),
                {}, {},
            )
        }
    }

    @Test fun ayuda() = paparazzi.snapshot { FocusGuardTheme(darkTheme = false) { HelpScreen(onBack = {}, onOpen = {}) } }

    @Test fun ayudaBuscando() = paparazzi.snapshot { FocusGuardTheme(darkTheme = false) { HelpScreen(onBack = {}, onOpen = {}, topicId = "temporizador-ayudame") } }

    @Test fun pin() = paparazzi.snapshot { FocusGuardTheme(darkTheme = false) { PinGateScreen({ false }, {}) } }

    private val fixedNow = Instant.parse("2026-10-27T10:00:00Z")

    private fun lock(
        reason: BlockReason,
        target: String,
        style: LockStyle = LockStyle(),
        showEmergency: Boolean = false,
        availableAt: Instant? = null,
    ) = paparazzi.snapshot(
        LockScreenView(
            paparazzi.context,
            LockScreenContent.of(
                paparazzi.context, reason = reason, target = target, availableAt = availableAt, displayName = target,
                style = style, now = fixedNow, motivationIndex = 0, zone = ZoneOffset.UTC,
            ),
            showEmergency,
        ) {},
    )

    @Test fun pantallaDeBloqueo() = lock(BlockReason.PROFILE_APP, "Instagram")

    @Test fun pantallaDeBloqueoAlIntentarDesinstalar() = lock(
        BlockReason.STRICT_MODE, "ajustes", style = LockStyle(guardMessage = "Lo instalaste por una razón. Respira antes de seguir."),
    )

    @Test fun pantallaDeBloqueoConEmergencia() = lock(BlockReason.PROFILE_APP, "Instagram", showEmergency = true)

    @Test fun pantallaDeBloqueoPorLimite() =
        lock(BlockReason.LIMIT_REACHED, "Juegos", availableAt = Instant.parse("2026-10-28T04:00:00Z"))

    @Test fun pantallaDeBloqueoPersonalizada() = lock(
        BlockReason.PROFILE_APP, "Instagram",
        LockStyle(message = "Hoy toca estudiar. Después, lo que quieras.", paletteId = "sand", emoji = "📚", rotating = true),
    )

    @Test fun proteccionClaro() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { ProtectionContent(protection, SnackbarHostState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun proteccionOscuro() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = true) { ProtectionContent(protection, SnackbarHostState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    private val adultActions = com.focusguard.ui.adult.AdultActions({}, { true }, {}, { _, _ -> true }, {})

    @Test fun contenidoAdultoSinListas() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { com.focusguard.ui.adult.AdultContentBody(com.focusguard.ui.adult.AdultUiState(), false, adultActions) }
    }

    @Test fun contenidoAdultoConListas() = paparazzi.snapshot {
        val state = com.focusguard.ui.adult.AdultUiState(
            domains = 84_213, words = 142, version = 17, offUntilMs = 1_800_000_000_000L,
            allowed = listOf("ejemplo.es"), userWords = listOf("mala palabra"),
        )
        FocusGuardTheme(darkTheme = false) { com.focusguard.ui.adult.AdultContentBody(state, false, adultActions) }
    }

    private val dnsActions = com.focusguard.ui.dns.DnsActions({}, {}, {}, {})

    @Test fun filtroDnsApagado() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { com.focusguard.ui.dns.DnsContent(false, com.focusguard.dns.DnsStatus.Stopped, emptyList(), dnsActions) }
    }

    @Test fun filtroDnsSinFiltrar() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            com.focusguard.ui.dns.DnsContent(true, com.focusguard.dns.DnsStatus.FailOpen, listOf("com.ejemplo.banco"), dnsActions)
        }
    }

    private val backupActions = com.focusguard.ui.backup.BackupActions({ _, _ -> }, {}, {}, { _, _ -> }, {}, {}, {})

    @Test fun copiaDeSeguridad() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { com.focusguard.ui.backup.BackupContent(1_800_000_000_000L, false, null, false, backupActions) }
    }

    @Test fun sincronizacionSinActivar() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) { com.focusguard.ui.backup.SyncCard(false, null, false, backupActions) }
        }
    }

    @Test fun sincronizacionActivada() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) { com.focusguard.ui.backup.SyncCard(true, 1_800_000_000_000L, false, backupActions) }
        }
    }

    private val streakToday = LocalDate.of(2026, 10, 28)
    private fun streakDays(vararg ranges: Pair<Int, Int>) = ranges.flatMap { (from, to) ->
        (from..to).map { d -> streakToday.minusDays(d.toLong()).let { it to DayRecord(it, protectedMs = 2 * 60 * 60_000L) } }
    }.toMap()

    private fun streak(records: Map<LocalDate, DayRecord>, config: StreakConfig = StreakConfig(), dark: Boolean = false) = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = dark) { StreakContent(records, config, streakToday, {}, {}, {}) }
    }

    /** Doce días seguidos hasta hoy, con un día perdido hace tres semanas y otro hace 14 días. */
    @Test fun rachaEnCurso() = streak(streakDays(0 to 11, 15 to 20), StreakConfig(goalDays = 30))

    @Test fun rachaEnCursoOscuro() = streak(streakDays(0 to 11, 15 to 20), StreakConfig(goalDays = 30), dark = true)

    /** Se perdió ayer una racha de seis días: se puede restaurar. */
    @Test fun rachaPerdidaConRestauracion() = streak(streakDays(2 to 7) + (streakToday to DayRecord(streakToday, protectedMs = 20 * 60_000L)))

    @Test fun rachaSinDatos() = streak(emptyMap())

    @Test fun impulsoIntro() = paparazzi.snapshot { FocusGuardTheme(darkTheme = false) { UrgeIntro({}, {}) } }

    /** A los 6 minutos de la cuenta atrás, a medio inspirar. */
    @Test fun impulsoEnCurso() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { UrgeRunning(remainingMs = 14 * 60_000L, elapsedMs = 6 * 60_000L, breathing = { BreathingPanelContent(BreathingPatterns.CALM, {}, true, {}, true, {}, false, {}, { 2_000L }) }, onPassed = {}, onGaveIn = {}) }
    }

    @Test fun impulsoEnCursoOscuro() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = true) { UrgeRunning(remainingMs = 14 * 60_000L, elapsedMs = 6 * 60_000L, breathing = { BreathingPanelContent(BreathingPatterns.BOX, {}, true, {}, false, {}, true, {}, { 6_000L }) }, onPassed = {}, onGaveIn = {}) }
    }

    @Test fun impulsoTerminado() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { UrgeFinished(UrgeOutcome.PASSED, UrgeTrigger.STRESS, {}, {}, {}) }
    }

    private val journalToday = LocalDate.of(2026, 10, 28)

    @Test fun diario() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            JournalContent(
                entries = listOf(
                    JournalEntryEntity(journalToday.minusDays(1).toEpochDay(), "Cuando me aburrí por la noche cogí el teléfono. Mejor habría sido salir a caminar.", 0),
                    JournalEntryEntity(journalToday.minusDays(3).toEpochDay(), "Un buen día: llamé a un amigo cuando me llegó el impulso.", 0),
                ),
                activities = listOf(
                    PlannedActivityEntity(1, journalToday.toEpochDay(), "Salir a caminar 20 minutos", doneMs = 1L),
                    PlannedActivityEntity(2, journalToday.toEpochDay(), "Llamar a mi hermana"),
                    PlannedActivityEntity(3, journalToday.plusDays(1).toEpochDay(), "Ir al gimnasio"),
                ),
                moods = listOf(MoodEntity(journalToday.toEpochDay(), 0, 4), MoodEntity(journalToday.minusDays(1).toEpochDay(), 0, 2)),
                today = journalToday, onSave = {}, onMood = {}, onPlan = { _, _ -> }, onComplete = {},
            )
        }
    }

    private val insightsToday = LocalDate.of(2026, 10, 28)
    private val twoWeeks = (14 downTo 1).map { back ->
        DayFigures(insightsToday.minusDays(back.toLong()), (if (back > 7) 190L + back * 4 else 150L + back * 12) * 60_000L, 40 + back, 3 + back % 3)
    }
    private val overview = UsageOverview(Trend.BETTER, 95 * 60_000L, Change(-12), Insights.weekly(twoWeeks, insightsToday))

    @Test fun estadisticasConInsights() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            StatsContent(stats.copy(overview = overview, usage = stats.usage.copy(insight = DayInsight(42 * 60_000L, 190 * 60_000L, 4))), {})
        }
    }

    @Test fun informeSemanal() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { WeeklyContent(WeeklyUi(overview.report, streakDays = 5, urgesPassed = 3, urgesGaveIn = 1, loaded = true), {}) }
    }

    @Test fun informeSemanalSinAcceso() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { WeeklyContent(WeeklyUi(loaded = true), {}) }
    }

    @Test fun habitos() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            HabitsContent(
                nudge = NudgeSettings(enabled = true, apps = setOf("a", "b", "c")),
                moodPrompt = MoodPromptSettings(enabled = true, minuteOfDay = 20 * 60),
                reminders = listOf(Reminder(1, 9 * 60, "Beber un vaso de agua"), Reminder(2, 18 * 60 + 30, "Salir a caminar", enabled = false)),
                onNudge = {}, onMoodPrompt = {}, onAdd = { _, _, _ -> }, onToggle = { _, _ -> }, onDelete = {},
            )
        }
    }

    @Test fun iconoDeLaApp() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { AppIconContent(LauncherIcon.CALCULATOR, {}) }
    }

    /** Los cuatro iconos del lanzador sobre su fondo, para ver que el dibujo cabe y se reconoce. */
    @Test fun iconosDelLanzador() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            androidx.compose.foundation.layout.Row(Modifier.padding(16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                listOf(
                    com.focusguard.R.drawable.ic_launcher_fg to 0xFF0B6E6A,
                    com.focusguard.R.drawable.ic_launcher_calculator_fg to 0xFFFF7043,
                    com.focusguard.R.drawable.ic_launcher_notes_fg to 0xFFFFC107,
                    com.focusguard.R.drawable.ic_launcher_clock_fg to 0xFF212121,
                ).forEach { (icon, color) ->
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(72.dp).background(androidx.compose.ui.graphics.Color(color), androidx.compose.foundation.shape.CircleShape),
                    ) {
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(icon), contentDescription = null, modifier = Modifier.size(72.dp))
                    }
                }
            }
        }
    }

    /** Todos los iconos propios en claro y oscuro, para comprobar que tienen el mismo peso y se reconocen. */
    @Test fun hojaDeIconos() = paparazzi.snapshot {
        val icons = with(com.focusguard.ui.icons.FgIcons) {
            listOf(ShieldFocus, Shield, Home, Chart, Lock, Sliders, Timer, Flame, Wind, Book, Users, Lifebuoy, Target, Block, Check, CheckCircle, Info, Warning, Add, Back, ChevronRight, Delete, Apps, Globe, Bell, Leaf)
        }
        FocusGuardTheme(darkTheme = false) {
            androidx.compose.foundation.layout.Column(Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background).padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                icons.chunked(7).forEach { row ->
                    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                        row.forEach { androidx.compose.material3.Icon(it, contentDescription = null, modifier = Modifier.size(40.dp), tint = androidx.compose.material3.MaterialTheme.colorScheme.primary) }
                    }
                }
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
                    androidx.compose.foundation.layout.Box(Modifier.size(72.dp).background(androidx.compose.ui.graphics.Color(0xFF0B6E6A), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))) {
                        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.focusguard.R.drawable.ic_launcher_fg), contentDescription = null, modifier = Modifier.size(72.dp))
                    }
                }
            }
        }
    }

    @Test fun consultasDns() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            val day = 20_000L
            com.focusguard.ui.dns.DnsLogContent(
                days = listOf(com.focusguard.data.DnsDay(day, 12, 340), com.focusguard.data.DnsDay(day - 1, 3, 120), com.focusguard.data.DnsDay(day - 5, 0, 8)),
                selected = day, showBlocked = true,
                hosts = listOf(
                    com.focusguard.data.DnsQueryEntity(day, "anuncios.ejemplo.com", true, 7, 0),
                    com.focusguard.data.DnsQueryEntity(day, "rastreador.ejemplo.net", true, 5, 0),
                ),
                today = day,
                actions = com.focusguard.ui.dns.DnsLogActions({}, {}, {}),
            )
        }
    }

    @Test fun consultasDnsSinDatos() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            com.focusguard.ui.dns.DnsLogContent(emptyList(), null, true, emptyList(), 20_000L, com.focusguard.ui.dns.DnsLogActions({}, {}, {}))
        }
    }

    @Test fun programaDeRecuperacion() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            val today = 20_000L
            com.focusguard.ui.recovery.RecoveryContent(
                progress = mapOf(
                    1 to com.focusguard.decision.StepRecord(today - 2, ""),
                    2 to com.focusguard.decision.StepRecord(today - 1, "BOREDOM|STRESS"),
                ),
                today = today,
                actions = com.focusguard.ui.recovery.RecoveryActions({ _, _ -> }, {}, {}, {}),
            )
        }
    }

    @Test fun programaDeRecuperacionRecienEmpezado() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            com.focusguard.ui.recovery.RecoveryContent(emptyMap(), 20_000L, com.focusguard.ui.recovery.RecoveryActions({ _, _ -> }, {}, {}, {}))
        }
    }

    private val assessmentActions = com.focusguard.ui.assessment.AssessmentActions({ _, _ -> }, {}, {}, {}, {}, {}, {})

    @Test fun testDeAdiccionPreguntas() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            com.focusguard.ui.assessment.AssessmentQuestions(listOf(2, 3, null, null, null, null, null, null, null, null), false, assessmentActions)
        }
    }

    @Test fun testDeAdiccionResultado() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            com.focusguard.ui.assessment.AssessmentResult(
                saved = com.focusguard.data.SavedResult(2, 24, 31),
                history = listOf(
                    com.focusguard.data.AssessmentResultEntity(2, 1_800_000_000_000L, 24, false),
                    com.focusguard.data.AssessmentResultEntity(1, 1_797_000_000_000L, 31, true),
                ),
                signedIn = true, busy = false, actions = assessmentActions,
            )
        }
    }

    @Test fun testDeAdiccionResultadoMuyAlto() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = true) {
            com.focusguard.ui.assessment.AssessmentResult(com.focusguard.data.SavedResult(1, 36, null), emptyList(), signedIn = false, busy = false, actions = assessmentActions)
        }
    }

    private val sampleUrgeReport = com.focusguard.decision.UrgeReport(
        total = 14, passed = 10, gaveIn = 3, abandoned = 1,
        byHour = List(24) { if (it in 21..23) 3 else if (it == 2) 1 else if (it == 15) 2 else 0 },
        byWeekday = listOf(1, 2, 1, 2, 4, 3, 1),
        triggers = listOf(com.focusguard.decision.UrgeTrigger.BOREDOM to 6, com.focusguard.decision.UrgeTrigger.STRESS to 3),
        weeks = (0 until 8).map { com.focusguard.decision.UrgeWeek(LocalDate.of(2026, 9, 7).plusWeeks(it.toLong()), listOf(0, 1, 2, 3, 2, 3, 2, 1)[it], listOf(0, 1, 1, 2, 1, 2, 2, 1)[it]) },
    )

    @Test fun informeDeImpulsos() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) { com.focusguard.ui.urge.UrgeReportContent(sampleUrgeReport, sharing = true, onSharing = {}) }
    }

    @Test fun informeDeImpulsosSinDatos() = paparazzi.snapshot {
        FocusGuardTheme(darkTheme = false) {
            com.focusguard.ui.urge.UrgeReportContent(
                com.focusguard.decision.UrgeReports.build(emptyList(), LocalDate.of(2026, 10, 28), java.time.ZoneOffset.UTC), sharing = false, onSharing = {},
            )
        }
    }
}
