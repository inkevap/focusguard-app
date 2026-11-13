package com.focusguard.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.AccountRepository
import com.focusguard.data.PartnerRepository
import com.focusguard.data.PartnerState
import com.focusguard.decision.NoticeSettings
import com.focusguard.decision.NotificationSettings
import com.focusguard.decision.StatsSettings
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.PinField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatMinutes
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.parseMinutes
import com.focusguard.ui.profiles.BottomAction
import com.focusguard.ui.profiles.GroupStack
import org.koin.compose.koinInject

/** Las subpantallas de Ajustes: cada bloque que no cabe en una fila. */
private enum class SettingsPage(val title: String) {
    NOTICES("Avisos"),
    NOTIFICATIONS("Notificaciones de apps"),
    LOCK("Pantalla de bloqueo"),
    ICON("Icono de la app"),
    PIN("PIN de la app"),
    DAY_START("Inicio del día"),
    STATS("Estadísticas de navegación"),
    FILE("Copia en un archivo"),
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    hasNotificationAccess: Boolean,
    onGrantNotificationAccess: () -> Unit,
    onOpenSavedNotifications: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenHelp: () -> Unit,
) {
    val dayStart by viewModel.dayStartMinute.collectAsStateWithLifecycle()
    val pinEnabled by viewModel.appPinEnabled.collectAsStateWithLifecycle()
    val lockStyle by viewModel.lockStyle.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    // Solo para el resumen de la raíz: la cuenta y el compañero se gestionan en su pantalla.
    val account by koinInject<AccountRepository>().state.collectAsStateWithLifecycle()
    val partner by koinInject<PartnerRepository>().state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshLauncherIcon() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)

    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    SubPageHost(
        page = page,
        onBack = { page = null },
        root = {
            FgScreen("Ajustes", large = true, snackbar = snackbar) {
                item {
                    FgGroup {
                        FgRow(
                            "Cuenta", icon = FgIcons.Globe, onClick = onOpenAccount,
                            subtitle = account.email ?: "Sin cuenta",
                        )
                    }
                }
                item {
                    FgGroup {
                        FgRow(
                            "Compañero", icon = FgIcons.Users, onClick = onOpenAccount,
                            value = when (partner) {
                                PartnerState.None -> "Ninguno"
                                is PartnerState.Invited -> "Invitado"
                                is PartnerState.Active -> "Activo"
                            },
                        )
                        FgRow("Copia de seguridad", icon = FgIcons.Shield, onClick = onOpenBackup)
                        FgRow("Copia en un archivo", icon = FgIcons.Book, onClick = { page = SettingsPage.FILE })
                    }
                }
                item {
                    FgGroup {
                        FgRow("Avisos", icon = FgIcons.Bell, value = "${notices.activeCount()} de 8", onClick = { page = SettingsPage.NOTICES })
                        FgRow(
                            "Notificaciones de apps", icon = FgIcons.Info,
                            value = if (notifications.captureBlocked || notifications.hideBackgroundNotice) "Activadas" else "No",
                            onClick = { page = SettingsPage.NOTIFICATIONS },
                        )
                        FgRow("Pantalla de bloqueo", icon = FgIcons.Block, onClick = { page = SettingsPage.LOCK })
                        FgRow("Icono de la app", icon = FgIcons.Apps, value = viewModel.launcherIcon.label, onClick = { page = SettingsPage.ICON })
                        FgRow("PIN de la app", icon = FgIcons.Lock, value = if (pinEnabled) "Activado" else "No", onClick = { page = SettingsPage.PIN })
                    }
                }
                item {
                    FgGroup {
                        FgRow("Inicio del día", value = formatMinutes(dayStart), onClick = { page = SettingsPage.DAY_START })
                        FgRow(
                            "Estadísticas de navegación",
                            value = if (stats.webs || stats.incognito) "Activadas" else "No",
                            onClick = { page = SettingsPage.STATS },
                        )
                    }
                }
                item {
                    FgGroup {
                        FgRow("Ayuda", icon = FgIcons.Lifebuoy, onClick = onOpenHelp)
                        FgActionRow(
                            "Repetir la bienvenida", viewModel::restartOnboarding,
                            subtitle = "Vuelve a elegir qué cuidar y cuándo, y a repasar los permisos.",
                        )
                    }
                }
            }
        },
        sub = { current ->
            SettingsSubPage(
                current, viewModel, snackbar, onBack = { page = null },
                dayStart = dayStart, pinEnabled = pinEnabled, lockStyle = lockStyle, notices = notices, stats = stats, notifications = notifications,
                hasNotificationAccess = hasNotificationAccess, onGrantNotificationAccess = onGrantNotificationAccess,
                onOpenSavedNotifications = onOpenSavedNotifications,
                onExport = { exporter.launch("focusguard-configuracion.json") },
                onImport = { importer.launch(arrayOf("application/json", "text/plain")) },
            )
        },
    )
}

@Composable
private fun SettingsSubPage(
    page: SettingsPage,
    viewModel: SettingsViewModel,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    dayStart: Int,
    pinEnabled: Boolean,
    lockStyle: com.focusguard.model.LockStyle,
    notices: NoticeSettings,
    stats: StatsSettings,
    notifications: NotificationSettings,
    hasNotificationAccess: Boolean,
    onGrantNotificationAccess: () -> Unit,
    onOpenSavedNotifications: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    when (page) {
        SettingsPage.DAY_START -> DayStartPage(dayStart, viewModel::setDayStartMinute, snackbar, onBack)
        SettingsPage.PIN -> AppPinPage(viewModel, pinEnabled, snackbar, onBack)
        SettingsPage.LOCK -> LockStylePage(lockStyle, viewModel::saveLockStyle, snackbar, onBack)
        else -> FgScreen(page.title, onBack = onBack, snackbar = snackbar) {
            item {
                when (page) {
                    SettingsPage.NOTICES -> NoticesCard(notices, viewModel::setNotices)
                    SettingsPage.NOTIFICATIONS -> NotificationsCard(notifications, viewModel::setNotifications, hasNotificationAccess, onGrantNotificationAccess, onOpenSavedNotifications)
                    SettingsPage.ICON -> AppIconContent(viewModel.launcherIcon, viewModel::changeLauncherIcon)
                    SettingsPage.STATS -> StatsCard(stats, viewModel::setStats, viewModel::clearWebStats)
                    SettingsPage.FILE -> FileGroups(onExport, onImport)
                    else -> Unit
                }
            }
        }
    }
}

private fun NoticeSettings.activeCount() = listOf(
    scheduleStarting, limitNearing, limitStatus, scheduleEnded, pauseEnding, dailyMotivation, weeklyReport, streak,
).count { it }

/** Exportar e importar la configuración en un archivo (sin cuenta ni servidor). */
@Composable
private fun FileGroups(onExport: () -> Unit, onImport: () -> Unit) {
    GroupStack {
        FgGroup(
            footer = "Guarda tus perfiles y ajustes en un archivo. No incluye el modo estricto, el PIN ni los lugares (redes Wi-Fi y ubicación), que dicen dónde vives. " +
                "Al importar solo se añaden perfiles nuevos.",
        ) {
            FgActionRow("Exportar", onExport)
            FgActionRow("Importar", onImport)
        }
    }
}

/** Avisos de lo que está por pasar (J3, E10). Cada uno se puede apagar. */
@Composable
internal fun NoticesCard(settings: NoticeSettings, onChange: (NoticeSettings) -> Unit) {
    GroupStack {
        FgGroup(header = "Horarios y pausas") {
            FgSwitchRow("Antes de que empiece un horario", settings.scheduleStarting, { onChange(settings.copy(scheduleStarting = it)) }, subtitle = "Un aviso 10 minutos antes.")
            FgSwitchRow("Cuando termina un horario", settings.scheduleEnded, { onChange(settings.copy(scheduleEnded = it)) }, subtitle = "Un aviso al acabar, para que sepas que ya puedes usar tus apps.")
            FgSwitchRow("Cuando una pausa va a terminar", settings.pauseEnding, { onChange(settings.copy(pauseEnding = it)) }, subtitle = "Un aviso antes y otro al volver a bloquear.")
        }
        FgGroup(header = "Límites") {
            FgSwitchRow("Antes de que se acabe un límite", settings.limitNearing, { onChange(settings.copy(limitNearing = it)) }, subtitle = "Cuando te quedan 5 minutos de tiempo.")
            FgSwitchRow(
                "Cuánto te queda mientras usas una app", settings.limitStatus, { onChange(settings.copy(limitStatus = it)) },
                subtitle = "Una notificación fija con lo que llevas y lo que queda de tu límite.",
            )
        }
        FgGroup(header = "Ánimo y resúmenes") {
            FgSwitchRow("Una frase cada día", settings.dailyMotivation, { onChange(settings.copy(dailyMotivation = it)) }, subtitle = "Una frase motivadora a las 9:00.")
            FgSwitchRow("Resumen de la semana", settings.weeklyReport, { onChange(settings.copy(weeklyReport = it)) }, subtitle = "Los lunes a las 9:00, cuántos bloqueos hubo.")
            FgSwitchRow(
                "Tu racha", settings.streak, { onChange(settings.copy(streak = it)) },
                subtitle = "Un aviso a las 20:00 si hoy aún no has llegado a la hora de bloqueo, y otro al llegar a un hito.",
            )
        }
    }
}

/** Notificaciones de apps bloqueadas (J1) y aviso de segundo plano (J2). Necesitan el acceso a notificaciones. */
@Composable
internal fun NotificationsCard(
    settings: NotificationSettings,
    onChange: (NotificationSettings) -> Unit,
    hasAccess: Boolean,
    onGrantAccess: () -> Unit,
    onOpenSaved: () -> Unit,
) {
    val needsAccess = !hasAccess && (settings.captureBlocked || settings.hideBackgroundNotice)
    GroupStack {
        FgGroup(footer = if (needsAccess) "Para que funcione hace falta darle acceso a las notificaciones." else null) {
            FgSwitchRow(
                "Quitar las de apps bloqueadas", settings.captureBlocked, { onChange(settings.copy(captureBlocked = it)) },
                subtitle = "Se guardan 7 días en este teléfono, recortadas, para verlas cuando quieras.",
            )
            FgSwitchRow(
                "Esconder el aviso de segundo plano", settings.hideBackgroundNotice, { onChange(settings.copy(hideBackgroundNotice = it)) },
                subtitle = "Esconde 24 h el aviso del sistema de que FocusGuard sigue funcionando.",
            )
            if (needsAccess) FgActionRow("Dar acceso", onGrantAccess)
        }
        FgGroup { FgRow("Ver las guardadas", onClick = onOpenSaved) }
    }
}

/** Estadísticas de navegación (K2): apagadas por defecto, solo dominios y solo en este teléfono. */
@Composable
internal fun StatsCard(settings: StatsSettings, onChange: (StatsSettings) -> Unit, onClear: () -> Unit) {
    GroupStack {
        FgGroup(footer = "Se guardan solo en este teléfono, solo el dominio (nunca la dirección completa) y 35 días.") {
            FgSwitchRow("Guardar las webs que visitas", settings.webs, { onChange(settings.copy(webs = it)) }, subtitle = "Las verás en Estadísticas, con visitas y tiempo.")
            FgSwitchRow("Contar el tiempo en incógnito", settings.incognito, { onChange(settings.copy(incognito = it)) }, subtitle = "Solo cuánto tiempo, sin saber qué webs.")
        }
        FgGroup { FgActionRow("Borrar estas estadísticas", onClear, danger = true) }
    }
}

@Composable
private fun DayStartPage(dayStart: Int, onSave: (Int) -> Unit, snackbar: SnackbarHostState, onBack: () -> Unit) {
    var text by remember(dayStart) { mutableStateOf(formatMinutes(dayStart)) }
    val minute = parseMinutes(text)?.takeIf { it < 24 * 60 }
    FgScreen(
        "Inicio del día", onBack = onBack, snackbar = snackbar,
        bottomBar = { BottomAction { PrimaryButton("Guardar hora", { minute?.let(onSave) }, Modifier.fillMaxWidth(), enabled = minute != null) } },
    ) {
        item {
            FgGroup(footer = "Los límites diarios se reinician a esta hora. Si duermes de madrugada, pon por ejemplo 04:00.") {
                FgCell {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Hora (HH:mm)") },
                        singleLine = true,
                        isError = minute == null,
                        supportingText = { if (minute == null) Text("Escribe una hora válida, por ejemplo 04:00") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPinPage(viewModel: SettingsViewModel, enabled: Boolean, snackbar: SnackbarHostState, onBack: () -> Unit) {
    var newPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    FgScreen(
        "PIN de la app", onBack = onBack, snackbar = snackbar,
        bottomBar = {
            BottomAction {
                PrimaryButton(
                    text = if (enabled) "Cambiar PIN" else "Activar PIN",
                    onClick = {
                        error = viewModel.enableAppPin(newPin, currentPin)
                        if (error == null) { newPin = ""; currentPin = "" }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        item {
            GroupStack {
                FgGroup(
                    footer = if (enabled) "Se pide al abrir FocusGuard. Es el mismo PIN que usa el modo estricto."
                    else "Pide un PIN cada vez que abres FocusGuard, para que nadie cambie tus reglas.",
                ) {
                    if (viewModel.hasPin) FgCell { PinField("PIN actual", currentPin, { currentPin = it }) }
                    FgCell {
                        PinField(
                            label = if (viewModel.hasPin) "Nuevo PIN (déjalo vacío para conservar el actual)" else "PIN de 4 a 8 dígitos",
                            value = newPin,
                            onChange = { newPin = it },
                            error = error,
                        )
                    }
                }
                if (enabled) {
                    FgGroup {
                        FgActionRow("Quitar PIN", {
                            error = viewModel.disableAppPin(currentPin)
                            if (error == null) currentPin = ""
                        }, danger = true)
                    }
                }
            }
        }
    }
}
