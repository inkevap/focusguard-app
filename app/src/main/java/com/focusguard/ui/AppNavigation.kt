package com.focusguard.ui

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.focusguard.ui.help.HelpScreen
import com.focusguard.ui.templates.TemplatesScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.focusguard.permissions.Permission
import com.focusguard.ui.account.AccountScreen
import com.focusguard.ui.adult.AdultScreen
import com.focusguard.ui.backup.BackupScreen
import com.focusguard.ui.habits.HabitsScreen
import com.focusguard.ui.journal.JournalScreen
import com.focusguard.ui.streak.StreakScreen
import com.focusguard.ui.urge.UrgeScreen
import com.focusguard.ui.weekly.WeeklyScreen
import com.focusguard.ui.sleep.SleepScreen
import com.focusguard.ui.dns.DnsLogScreen
import com.focusguard.ui.assessment.AssessmentScreen
import com.focusguard.ui.urge.UrgeReportScreen
import com.focusguard.ui.recovery.RecoveryScreen
import com.focusguard.ui.dns.DnsScreen
import com.focusguard.ui.approvals.ApprovalsScreen
import com.focusguard.ui.home.HomeScreen
import com.focusguard.ui.emergency.EmergencyScreen
import com.focusguard.ui.protection.permission
import com.focusguard.ui.profiles.PanicScreen
import com.focusguard.ui.protection.TroubleshooterScreen
import com.focusguard.ui.profiles.ProfileEditScreen
import com.focusguard.ui.profiles.ProfilePreviewScreen
import com.focusguard.ui.profiles.ProfilesScreen
import com.focusguard.ui.progress.ProgressScreen
import com.focusguard.ui.profiles.SessionScreen
import com.focusguard.ui.profiles.UnlockAllScreen
import com.focusguard.ui.protection.ProtectionScreen
import com.focusguard.ui.settings.SavedNotificationsScreen
import com.focusguard.ui.settings.ImageProbeScreen
import com.focusguard.ui.settings.SettingsScreen
import com.focusguard.ui.stats.StatsScreen
import com.focusguard.ui.strict.StrictScreen
import org.koin.androidx.compose.koinViewModel

/** En tabletas el contenido no se estira de lado a lado. */
private val MAX_CONTENT_WIDTH = 640.dp

private enum class TopLevel(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Inicio", FgIcons.Home),
    Profiles("profiles", "Perfiles", FgIcons.Apps),
    Progress("progress", "Progreso", FgIcons.Chart),
    Protection("protection", "Protección", FgIcons.Shield),
    Settings("settings", "Ajustes", FgIcons.Sliders),
}

private fun NavController.goTo(route: String) = navigate(route) {
    popUpTo(TopLevel.Home.route) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppNavigation(
    granted: Map<Permission, Boolean>,
    onGrant: (Permission) -> Unit,
    onOpenAppDetails: () -> Unit,
    startRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
) {
    val nav = rememberNavController()
    // Los atajos del icono y del panel rápido abren directamente una pantalla.
    LaunchedEffect(startRoute) {
        val target = startRoute ?: return@LaunchedEffect
        nav.navigate(target) { launchSingleTop = true }
        onRouteConsumed()
    }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val missingPermissions = granted.count { (permission, ok) -> permission.required && !ok }
    val openPermissions: () -> Unit = { nav.navigate("permissions") }

    // Con el teclado abierto la barra inferior sobra y taparía los campos.
    val keyboardOpen = WindowInsets.isImeVisible
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (!keyboardOpen && TopLevel.entries.any { it.route == route }) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        NavigationBarItem(
                            selected = route == item.route,
                            onClick = { nav.goTo(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Este es el único sitio que reparte los insets: el teclado se resta aquí y las pantallas de dentro no lo repiten.
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter,
        ) {
        NavHost(navController = nav, startDestination = TopLevel.Home.route, modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH)) {
            composable(TopLevel.Home.route) {
                HomeScreen(
                    viewModel = koinViewModel(),
                    missingPermissions = missingPermissions,
                    onOpenPermissions = openPermissions,
                    onOpenProfiles = { nav.goTo(TopLevel.Profiles.route) },
                    onNewSession = { pomodoro -> nav.navigate(if (pomodoro) "session/pomodoro" else Routes.QUICK_BLOCK) },
                    onPanic = { nav.navigate(Routes.PANIC) },
                    streakViewModel = koinViewModel(),
                    onSleep = { nav.navigate(Routes.SLEEP) },
                    onStreak = { nav.navigate(Routes.STREAK) },
                    onUrge = { nav.navigate(Routes.URGE) },
                )
            }
            composable(TopLevel.Profiles.route) {
                ProfilesScreen(
                    viewModel = koinViewModel(),
                    onOpen = { nav.navigate("profile/$it") },
                    onNew = { nav.navigate("profile/0") },
                    onTemplates = { nav.navigate(Routes.TEMPLATES) },
                    onUnlockAll = { nav.navigate(Routes.UNLOCK_ALL) },
                )
            }
            composable(TopLevel.Progress.route) {
                ProgressScreen(
                    streakViewModel = koinViewModel(),
                    onStreak = { nav.navigate(Routes.STREAK) },
                    onStats = { nav.navigate(Routes.STATS) },
                    onWeekly = { nav.navigate(Routes.WEEKLY) },
                    onUrgeReport = { nav.navigate(Routes.URGE_REPORT) },
                    onJournal = { nav.navigate(Routes.JOURNAL) },
                    onRecovery = { nav.navigate(Routes.RECOVERY) },
                    onAssessment = { nav.navigate(Routes.ASSESSMENT) },
                    onHabits = { nav.navigate(Routes.HABITS) },
                )
            }
            composable(Routes.STATS) { StatsScreen(viewModel = koinViewModel(), onOpenPermissions = openPermissions, onWeekly = { nav.navigate(Routes.WEEKLY) }, onBack = { nav.popBackStack() }) }
            composable(Routes.HABITS) { HabitsScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }
            composable(Routes.WEEKLY) { WeeklyScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }, onOpenPermissions = openPermissions) }
            composable(TopLevel.Protection.route) {
                ProtectionScreen(
                    viewModel = koinViewModel(),
                    missingPermissions = missingPermissions,
                    onOpenPermissions = openPermissions,
                    onOpenStrict = { nav.navigate("strict") },
                    onOpenApprovals = { nav.navigate("approvals") },
                    onOpenTroubleshooter = { nav.navigate(Routes.TROUBLESHOOTER) },
                    onOpenAdult = { nav.navigate(Routes.ADULT) },
                    onOpenDns = { nav.navigate(Routes.DNS) },
                    onOpenImages = { nav.navigate(Routes.IMAGES) },
                )
            }
            composable(TopLevel.Settings.route) {
                SettingsScreen(
                    viewModel = koinViewModel(),
                    hasNotificationAccess = granted[Permission.NOTIFICATION_ACCESS] == true,
                    onGrantNotificationAccess = { onGrant(Permission.NOTIFICATION_ACCESS) },
                    onOpenSavedNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                    onOpenAccount = { nav.navigate(Routes.ACCOUNT) },
                    onOpenBackup = { nav.navigate(Routes.BACKUP) },
                    onOpenHelp = { nav.navigate(Routes.HELP) },
                )
            }
            composable(
                route = "${Routes.HELP}?topic={topic}",
                arguments = listOf(navArgument("topic") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                HelpScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) }, topicId = entry.arguments?.getString("topic"))
            }
            composable(Routes.TEMPLATES) {
                TemplatesScreen(
                    viewModel = koinViewModel(),
                    onBack = { nav.popBackStack() },
                    onCreated = { id -> nav.navigate("profile/$id") { popUpTo(Routes.TEMPLATES) { inclusive = true } } },
                )
            }
            composable(Routes.URGE_REPORT) { UrgeReportScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }
            composable(Routes.ASSESSMENT) {
                AssessmentScreen(
                    viewModel = koinViewModel(), onBack = { nav.popBackStack() },
                    onProgram = { nav.navigate(Routes.RECOVERY) }, onUrge = { nav.navigate(Routes.URGE) },
                )
            }
            composable(Routes.RECOVERY) {
                RecoveryScreen(
                    viewModel = koinViewModel(), onBack = { nav.popBackStack() },
                    onJournal = { nav.navigate(Routes.JOURNAL) }, onAccount = { nav.navigate(Routes.ACCOUNT) },
                )
            }
            composable(Routes.DNS) { DnsScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }, onOpenLog = { nav.navigate(Routes.DNS_LOG) }) }
            composable(Routes.DNS_LOG) { DnsLogScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }
            composable(Routes.IMAGES) { ImageProbeScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }
            composable(Routes.ADULT) { AdultScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }
            composable(Routes.BACKUP) { BackupScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }
            composable(Routes.ACCOUNT) { AccountScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }, onOpenBackup = { nav.navigate(Routes.BACKUP) }) }
            composable(Routes.NOTIFICATIONS) { SavedNotificationsScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }

            composable("session/{type}") { entry ->
                SessionScreen(
                    viewModel = koinViewModel(),
                    pomodoro = entry.arguments?.getString("type") == "pomodoro",
                    onDone = { nav.popBackStack() },
                )
            }
            composable(Routes.EMERGENCY) { EmergencyScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }
            composable(Routes.TROUBLESHOOTER) {
                TroubleshooterScreen(
                    viewModel = koinViewModel(),
                    onFix = { fix -> fix.permission()?.let(onGrant) ?: onOpenAppDetails() },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.UNLOCK_ALL) { UnlockAllScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }
            composable(Routes.URGE) { UrgeScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }, onJournal = { nav.navigate(Routes.JOURNAL) { popUpTo(Routes.URGE) { inclusive = true } } }, onReport = { nav.navigate(Routes.URGE_REPORT) }) }
            composable(Routes.JOURNAL) { JournalScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }
            composable(Routes.STREAK) { StreakScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }) }
            composable(Routes.SLEEP) { SleepScreen(viewModel = koinViewModel(), onDone = { nav.popBackStack() }) }
            composable(Routes.PANIC) { PanicScreen(viewModel = koinViewModel(), onDone = { nav.popBackStack() }) }
            composable("approvals") { ApprovalsScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }, onHelp = { nav.navigate("${Routes.HELP}?topic=aprobaciones") }) }
            composable("strict") { StrictScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() }, onHelp = { nav.navigate("${Routes.HELP}?topic=modo-estricto") }) }
            composable("permissions") {
                PermissionsScreen(granted = granted, onGrant = onGrant, onOpenAppDetails = onOpenAppDetails, onBack = { nav.popBackStack() })
            }
            composable(route = "preview/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                ProfilePreviewScreen(
                    viewModel = koinViewModel(),
                    profileId = id,
                    // Si se llegó desde el editor, se vuelve a él en vez de apilar otro.
                    onEdit = {
                        if (!nav.popBackStack("profile/{id}", inclusive = false)) {
                            nav.navigate("profile/$id") { popUpTo("preview/$id") { inclusive = true } }
                        }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(route = "profile/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                ProfileEditScreen(
                    viewModel = koinViewModel(),
                    profileId = entry.arguments?.getLong("id") ?: 0L,
                    onDone = { nav.popBackStack() },
                    onPreview = { id -> nav.navigate("preview/$id") },
                )
            }
        }
        }
    }
}
