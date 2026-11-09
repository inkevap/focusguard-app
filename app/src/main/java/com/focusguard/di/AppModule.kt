package com.focusguard.di

import com.focusguard.decision.BackupSettings
import com.focusguard.data.UrgeReportSharing
import com.focusguard.data.AssessmentStore
import com.focusguard.data.RecoveryStore
import com.focusguard.data.DnsQueryLog
import com.focusguard.data.SyncLocal
import com.focusguard.data.SyncSecrets
import com.focusguard.data.SyncRepository
import com.focusguard.data.KeystoreSyncSecrets
import com.focusguard.data.AccountRepository
import com.focusguard.decision.Approvals
import com.focusguard.data.ApprovalApplier
import com.focusguard.data.ApprovalsReport
import com.focusguard.data.AdultFilterProvider
import com.focusguard.data.BlocklistRepository
import com.focusguard.data.BlocklistStore
import com.focusguard.data.EventStore
import com.focusguard.dns.DnsConfigStore
import com.focusguard.dns.DnsFilter
import com.focusguard.dns.DohClient
import com.focusguard.dns.PrefsDnsConfigStore
import com.focusguard.data.FileBlocklistStore
import com.focusguard.data.PartnerApprovals
import com.focusguard.data.PartnerEventOutbox
import com.focusguard.data.PrefsEventStore
import com.focusguard.service.PartnerSync
import com.focusguard.data.HelpingRepository
import com.focusguard.data.PartnerRepository
import com.focusguard.data.PartnerStore
import com.focusguard.data.PrefsPartnerStore
import com.focusguard.data.AppDatabase
import com.focusguard.data.ApprovalExecutor
import com.focusguard.data.ApprovalGate
import com.focusguard.data.ApprovalRepository
import com.focusguard.data.BackupRepository
import com.focusguard.data.ServerBackupRepository
import com.focusguard.data.WrongBlockReporter
import com.focusguard.data.BlockLog
import com.focusguard.data.EmergencyRepository
import com.focusguard.data.BlockGate
import com.focusguard.data.LockStyleRepository
import com.focusguard.data.NotificationLog
import com.focusguard.data.NoticeLedger
import com.focusguard.data.BreathingPrefs
import com.focusguard.data.CategoryCatalog
import com.focusguard.data.TemplateCatalog
import com.focusguard.ui.templates.TemplatesViewModel
import com.focusguard.data.ChangeGuard
import com.focusguard.data.ProfileLockRepository
import com.focusguard.ui.profiles.ProfileLockViewModel
import com.focusguard.data.ProfilePauses
import com.focusguard.data.ProfileRepository
import com.focusguard.data.ServiceHealth
import com.focusguard.data.SettingsRepository
import com.focusguard.data.StrictGuard
import com.focusguard.data.HabitsStore
import com.focusguard.data.JournalStore
import com.focusguard.data.LauncherIconManager
import com.focusguard.data.StreakStore
import com.focusguard.data.UrgeLog
import com.focusguard.data.StrictRepository
import com.focusguard.data.SubAppCatalog
import com.focusguard.data.SystemUsageEvents
import com.focusguard.data.UsageHistory
import com.focusguard.data.TrustedClock
import com.focusguard.data.UsageRepository
import com.focusguard.data.WebStats
import org.koin.android.ext.koin.androidContext
import com.focusguard.service.DeviceContextProvider
import com.focusguard.service.Notifier
import com.focusguard.service.StepCounter
import com.focusguard.BuildConfig
import com.focusguard.push.FirebasePushTokenSource
import com.focusguard.push.PushHandler
import com.focusguard.server.AccountStore
import com.focusguard.server.HttpTransport
import com.focusguard.server.KeystoreAccountStore
import com.focusguard.server.ServerApi
import com.focusguard.server.ServerUrl
import com.focusguard.server.UrlConnectionTransport
import com.focusguard.ui.urge.UrgeReportViewModel
import com.focusguard.ui.assessment.AssessmentViewModel
import com.focusguard.ui.recovery.RecoveryViewModel
import com.focusguard.ui.account.AccountViewModel
import com.focusguard.ui.approvals.ApprovalsViewModel
import com.focusguard.ui.adult.AdultViewModel
import com.focusguard.ui.backup.BackupViewModel
import com.focusguard.ui.sleep.SleepViewModel
import com.focusguard.ui.sleep.clockPackages
import com.focusguard.ui.dns.DnsLogViewModel
import com.focusguard.ui.dns.DnsViewModel
import com.focusguard.ui.emergency.EmergencyViewModel
import com.focusguard.ui.habits.HabitsViewModel
import com.focusguard.ui.journal.JournalViewModel
import com.focusguard.ui.streak.StreakViewModel
import com.focusguard.ui.urge.UrgeViewModel
import com.focusguard.ui.weekly.WeeklyViewModel
import com.focusguard.ui.OnboardingViewModel
import com.focusguard.ui.profiles.ProfileEditViewModel
import com.focusguard.ui.profiles.ProfilePreviewViewModel
import com.focusguard.permissions.PermissionChecker
import com.focusguard.ui.protection.ProtectionViewModel
import com.focusguard.ui.protection.TroubleshooterViewModel
import com.focusguard.ui.settings.SettingsViewModel
import com.focusguard.ui.settings.SavedNotificationsViewModel
import com.focusguard.ui.stats.StatsViewModel
import com.focusguard.ui.profiles.ProfilesViewModel
import com.focusguard.ui.profiles.installedPackages
import com.focusguard.ui.profiles.SessionViewModel
import com.focusguard.ui.strict.StrictViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AppDatabase.create(androidContext()) }
    single { get<AppDatabase>().profileDao() }
    single { ProfileRepository(get()) }
    single { get<AppDatabase>().usageDao() }
    single { UsageRepository(get()) }
    single { get<AppDatabase>().blockEventDao() }
    single { BlockLog(get()) }
    single { get<AppDatabase>().webUsageDao() }
    single { get<AppDatabase>().dnsQueryDao() }
    single { get<AppDatabase>().recoveryDao() }
    single { get<AppDatabase>().assessmentDao() }
    single { AssessmentStore(get(), get(), get(), { get<AccountRepository>().state.value.signedIn }) }
    single { RecoveryStore(get(), get()) }
    single { UrgeReportSharing(get(), get(), get()) }
    single { DnsQueryLog(get(), get(), get(), get()) }
    single { WebStats(get()) }
    single { SystemUsageEvents(androidContext()) }
    single { get<AppDatabase>().dayUsageDao() }
    single { get<AppDatabase>().habitsDao() }
    single { HabitsStore(get(), get(), get()) }
    single { LauncherIconManager(androidContext(), get()) }
    single { UsageHistory(androidContext(), get(), get(), get()) }
    single { get<AppDatabase>().capturedNotificationDao() }
    single { NotificationLog(get()) }
    single { BlockGate() }
    single { WrongBlockReporter(get(), get()) }
    single { ServerBackupRepository(get(), get(), get(), get()) }
    single { BackupRepository(androidContext(), get(), get(), get(), get(), get()) }
    single { TrustedClock(androidContext()) }
    single { SettingsRepository(androidContext()) }
    single { com.focusguard.vision.ImageProbeStats() }
    single { BreathingPrefs(androidContext()) }
    single { StrictRepository(androidContext()) }
    single { StrictGuard(get(), get()) }
    single { ApprovalRepository(androidContext(), get()) }
    single { ApprovalGate(get()) }
    single { ProfileLockRepository(androidContext()) }
    single { ChangeGuard(get(), get(), get(), get(), get()) }
    single { get<AppDatabase>().profilePauseDao() }
    single { get<AppDatabase>().dayRecordDao() }
    single { StreakStore(androidContext(), get(), get(), get()) }
    single { get<AppDatabase>().urgeDao() }
    single { get<AppDatabase>().journalDao() }
    single { UrgeLog(get(), get()) }
    single { JournalStore(get(), get(), get()) }
    single { ProfilePauses(get(), get(), get(), get()) }
    single { ApprovalExecutor(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    single { StepCounter(androidContext()) }
    single { EmergencyRepository(androidContext(), get(), get()) }
    single { LockStyleRepository(androidContext()) }
    single { ServiceHealth() }
    single { NoticeLedger(androidContext()) }
    single<HttpTransport> { UrlConnectionTransport() }
    single<AccountStore> { KeystoreAccountStore(androidContext()) }
    single {
        val settings = get<SettingsRepository>()
        ServerApi(get(), { ServerUrl.normalize(settings.serverUrl.value, BuildConfig.DEBUG) }, get())
    }
    single<PartnerStore> { PrefsPartnerStore(androidContext()) }
    single { HelpingRepository(get()) }
    single<SyncSecrets> { KeystoreSyncSecrets(androidContext()) }
    single { SyncRepository(get(), get<BackupRepository>(), get(), get<TrustedClock>()::nowMs, { get<AccountStore>().email }) }
    single {
        PartnerRepository(
            get(), get(),
            // Sin compañero activo no hay a quién pedirle nada: el modo parental se apaga con él, para no dejar al usuario sin poder cambiar nada.
            {
                get<ApprovalRepository>().partnerGone()
                get<SettingsRepository>().setParentalMode(false)
                // Un compañero nuevo no recibe el resumen sin que el usuario lo elija otra vez.
                get<SettingsRepository>().setShareUrgeReport(false)
            },
            { get<SettingsRepository>().setPartnerNotices(true) },
        )
    }
    single {
        AccountRepository(
            get(), get(), { android.os.Build.MODEL }, get<SettingsRepository>()::setServerUrl,
            onSignOut = {
                get<PartnerRepository>().clear()
                get<HelpingRepository>().clear()
                get<SyncRepository>().disable()
                get<PartnerEventOutbox>().clear()
                get<SettingsRepository>().setParentalMode(false)
            },
            // Con la sesión caducada no se pasa por el cierre: lo de la cuenta anterior se vacía aquí, antes de pedir lo de la nueva.
            onSignIn = {
                get<HelpingRepository>().clear()
                get<PartnerRepository>().refresh()
                get<HelpingRepository>().refresh()
            },
            pushToken = FirebasePushTokenSource,
        )
    }
    single { Notifier(androidContext()) }
    single {
        PushHandler(
            signedIn = { get<AccountRepository>().state.value.signedIn },
            syncApprovals = {
                val resolved = get<PartnerApprovals>().syncReportingResolved()
                val applied = get<ApprovalApplier>().applyApproved()
                ApprovalsReport(resolved, applied.applied, applied.failed)
            },
            refreshPartner = { get<PartnerRepository>().refreshReportingActivation() },
            notifyResolved = { get<Notifier>().notifyApprovalResolved(it) },
            notifyPartnerConfirmed = { get<Notifier>().notifyPartnerConfirmed() },
        )
    }
    single { PartnerApprovals(get(), get<ApprovalRepository>(), get(), get<TrustedClock>()::now) }
    single {
        ApprovalApplier(
            store = get<ApprovalRepository>(),
            consume = { get<PartnerApprovals>().consume(it) },
            apply = { get<ApprovalExecutor>().apply(it) },
            blockedReason = { get<ApprovalExecutor>().blockedReason(it) },
            describe = { Approvals.describe(it.actionKey) },
        )
    }
    single<EventStore> { PrefsEventStore(androidContext()) }
    single<DnsConfigStore> { PrefsDnsConfigStore(androidContext()) }
    single { DohClient(get()) }
    single { DnsFilter(get(), get(), get(), androidContext(), get()) }
    single<BlocklistStore> { FileBlocklistStore(androidContext()) }
    single { BlocklistRepository(get(), get()) }
    single { AdultFilterProvider(get(), get()) }
    single {
        val context = androidContext()
        PartnerEventOutbox(get(), get(), get(), get(), { get<SettingsRepository>().partnerNotices.value }, get<TrustedClock>()::now, { PartnerSync.flushSoon(context) }, { type -> get<SettingsRepository>().let { it.partnerPanel.value.active(it.parentalMode.value).allows(type) } })
    }
    single { CategoryCatalog.fromAssets(androidContext()) }
    single { TemplateCatalog.fromAssets(androidContext()) }
    single { PermissionChecker(androidContext()) }
    single { DeviceContextProvider(androidContext()) }
    single { SubAppCatalog.fromAssets(androidContext()) }
    viewModel { ProfilesViewModel(get(), get(), get(), get()) }
    viewModel { ProfileEditViewModel(get(), get(), get(), get(), get(), get(), { installedPackages(androidContext()) }) }
    viewModel { ProfileLockViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ProfilePreviewViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SessionViewModel(get(), get(), get(), get()) }
    viewModel { AdultViewModel(get(), get(), get(), get()) }
    viewModel { DnsViewModel(get(), get(), get()) }
    viewModel { BackupViewModel(get(), get()) }
    viewModel { DnsLogViewModel(get()) }
    viewModel { RecoveryViewModel(get()) }
    viewModel { AssessmentViewModel(get(), get()) }
    viewModel { UrgeReportViewModel(get(), get(), get(), get(), get()) }
    viewModel { SleepViewModel(get(), get(), { clockPackages(androidContext()) }) }
    viewModel { StreakViewModel(get(), get()) }
    viewModel { UrgeViewModel(get()) }
    viewModel { HabitsViewModel(get(), get()) }
    viewModel { WeeklyViewModel(get(), get(), get(), get()) }
    viewModel { JournalViewModel(get(), get()) }
    viewModel { EmergencyViewModel(get(), get(), get(), get(), get()) }
    viewModel { TroubleshooterViewModel(get(), get(), get(), get()) }
    viewModel { TemplatesViewModel(get(), get(), get(), { installedPackages(androidContext()) }) }
    viewModel { StrictViewModel(get(), get(), get(), get(), get<ApprovalRepository>(), get()) }
    viewModel { ApprovalsViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { OnboardingViewModel(get(), get(), get(), { installedPackages(androidContext()) }) }
    viewModel { SavedNotificationsViewModel(get(), androidContext()) }
    viewModel { StatsViewModel(get(), androidContext(), get(), get(), get(), get(), get()) }
    viewModel { AccountViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { ProtectionViewModel(get(), get(), get(), get(), get()) }
}
