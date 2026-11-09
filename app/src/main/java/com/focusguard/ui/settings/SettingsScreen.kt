package com.focusguard.ui.settings

import com.focusguard.ui.icons.FgIcons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.ui.components.ActionRow
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PinField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.ScreenHeader
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.decision.NoticeSettings
import com.focusguard.decision.NotificationSettings
import com.focusguard.decision.StatsSettings
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatMinutes
import com.focusguard.ui.parseMinutes

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, hasNotificationAccess: Boolean, onGrantNotificationAccess: () -> Unit, onOpenSavedNotifications: () -> Unit, onOpenAccount: () -> Unit, onOpenHabits: () -> Unit, onOpenHelp: () -> Unit) {
    val dayStart by viewModel.dayStartMinute.collectAsStateWithLifecycle()
    val pinEnabled by viewModel.appPinEnabled.collectAsStateWithLifecycle()
    val lockStyle by viewModel.lockStyle.collectAsStateWithLifecycle()
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val imageProbe by viewModel.imageProbe.collectAsStateWithLifecycle()
    val imageProbeStats by viewModel.imageProbeSnapshot.collectAsStateWithLifecycle()
    val imageSensitivity by viewModel.imageSensitivity.collectAsStateWithLifecycle()
    val imageNotifyMinutes by viewModel.imageNotifyMinutes.collectAsStateWithLifecycle()
    val imageVigilance by viewModel.imageVigilance.collectAsStateWithLifecycle()
    val imageAdaptive by viewModel.imageAdaptive.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshLauncherIcon() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)

    Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader("Ajustes", "Preferencias y copia de seguridad", icon = FgIcons.Sliders) }
            item {
                AppCard(contentPadding = 0.dp) {
                    ActionRow("Cuenta y compañero", "Tu cuenta en el servidor: avisos y aprobaciones por correo, y copia de seguridad.", FgIcons.Users, onOpenAccount)
                }
            }
            item {
                AppCard(contentPadding = 0.dp) {
                    ActionRow("Ayuda", "Qué hace cada función, con ejemplos y excepciones.", FgIcons.Info, onOpenHelp)
                    ActionRow("Repetir el asistente de bienvenida", "Vuelve a elegir qué cuidar y cuándo, y a repasar los permisos.", FgIcons.Leaf, viewModel::restartOnboarding)
                }
            }
            item {
                AppCard(contentPadding = 0.dp) {
                    ActionRow("Hábitos y recordatorios", "Un aviso cuando llevas un rato con el teléfono, recordatorios propios y una pregunta de ánimo.", FgIcons.Leaf, onOpenHabits)
                }
            }
            item { AppIconContent(viewModel.launcherIcon, viewModel::changeLauncherIcon) }
            item { DayStartCard(dayStart, viewModel::setDayStartMinute) }
            item { NoticesCard(notices, viewModel::setNotices) }
            item { StatsCard(stats, viewModel::setStats, viewModel::clearWebStats) }
            item { ImageProbeCard(
                    imageProbe, imageProbeStats, imageSensitivity, imageNotifyMinutes, imageVigilance, imageAdaptive,
                    viewModel::setImageProbe, viewModel::setImageSensitivity, viewModel::setImageNotifyMinutes, viewModel::setImageVigilance, viewModel::setImageAdaptive,
                    viewModel::resetImageProbe,
                ) }
            item {
                NotificationsCard(notifications, viewModel::setNotifications, hasNotificationAccess, onGrantNotificationAccess, onOpenSavedNotifications)
            }
            item { LockStyleCard(lockStyle, viewModel::saveLockStyle) }
            item {
                AppCard {
                    SectionTitle("Copia de la configuración")
                    Hint("Guarda tus perfiles y ajustes en un archivo. No incluye el modo estricto, el PIN ni los lugares (redes Wi-Fi y ubicación), que dicen dónde vives. Al importar solo se añaden perfiles nuevos.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton("Exportar", { exporter.launch("focusguard-configuracion.json") }, Modifier.weight(1f))
                        SecondaryButton("Importar", { importer.launch(arrayOf("application/json", "text/plain")) }, Modifier.weight(1f))
                    }
                }
            }
            item { AppPinCard(viewModel, pinEnabled) }
        }
    }
}

/** Avisos de lo que está por pasar (J3, E10). Cada uno se puede apagar. */
@Composable
internal fun NoticesCard(settings: NoticeSettings, onChange: (NoticeSettings) -> Unit) {
    AppCard {
        SectionTitle("Avisos", FgIcons.Bell)
        SwitchRow("Antes de que empiece un horario", "Un aviso 10 minutos antes.", settings.scheduleStarting, { onChange(settings.copy(scheduleStarting = it)) })
        SwitchRow("Antes de que se acabe un límite", "Cuando te quedan 5 minutos de tiempo.", settings.limitNearing, { onChange(settings.copy(limitNearing = it)) })
        SwitchRow("Cuánto te queda mientras usas una app", "Una notificación fija con lo que llevas y lo que queda de tu límite.", settings.limitStatus, { onChange(settings.copy(limitStatus = it)) })
        SwitchRow("Cuando termina un horario", "Un aviso al acabar, para que sepas que ya puedes usar tus apps.", settings.scheduleEnded, { onChange(settings.copy(scheduleEnded = it)) })
        SwitchRow("Cuando una pausa va a terminar", "Un aviso antes y otro al volver a bloquear.", settings.pauseEnding, { onChange(settings.copy(pauseEnding = it)) })
        SwitchRow("Una frase cada día", "Una frase motivadora a las 9:00.", settings.dailyMotivation, { onChange(settings.copy(dailyMotivation = it)) })
        SwitchRow("Resumen de la semana", "Los lunes a las 9:00, cuántos bloqueos hubo.", settings.weeklyReport, { onChange(settings.copy(weeklyReport = it)) })
        SwitchRow("Tu racha", "Un aviso a las 20:00 si hoy aún no has llegado a la hora de bloqueo, y otro al llegar a un hito.", settings.streak, { onChange(settings.copy(streak = it)) })
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
    AppCard {
        SectionTitle("Notificaciones", FgIcons.Bell)
        SwitchRow(
            "Quitar las de apps bloqueadas",
            "Se guardan 7 días en este teléfono, recortadas, para verlas cuando quieras.",
            settings.captureBlocked,
            { onChange(settings.copy(captureBlocked = it)) },
        )
        SwitchRow(
            "Esconder el aviso de segundo plano",
            "Esconde 24 h el aviso del sistema de que FocusGuard sigue funcionando.",
            settings.hideBackgroundNotice,
            { onChange(settings.copy(hideBackgroundNotice = it)) },
        )
        if (!hasAccess && (settings.captureBlocked || settings.hideBackgroundNotice)) {
            Hint("Para que funcione hace falta darle acceso a las notificaciones.")
            PrimaryButton("Dar acceso", onGrantAccess)
        }
        SecondaryButton("Ver las guardadas", onOpenSaved, Modifier.padding(top = 8.dp))
    }
}

/** Estadísticas de navegación (K2): apagadas por defecto, solo dominios y solo en este teléfono. */
@Composable
internal fun StatsCard(settings: StatsSettings, onChange: (StatsSettings) -> Unit, onClear: () -> Unit) {
    AppCard {
        SectionTitle("Estadísticas de navegación", FgIcons.Globe)
        Hint("Se guardan solo en este teléfono, solo el dominio (nunca la dirección completa) y 35 días.")
        SwitchRow("Guardar las webs que visitas", "Las verás en Estadísticas, con visitas y tiempo.", settings.webs, { onChange(settings.copy(webs = it)) })
        SwitchRow("Contar el tiempo en incógnito", "Solo cuánto tiempo, sin saber qué webs.", settings.incognito, { onChange(settings.copy(incognito = it)) })
        SecondaryButton("Borrar estas estadísticas", onClear, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun DayStartCard(dayStart: Int, onSave: (Int) -> Unit) {
    var text by remember(dayStart) { mutableStateOf(formatMinutes(dayStart)) }
    val minute = parseMinutes(text)?.takeIf { it < 24 * 60 }
    AppCard {
        SectionTitle("Inicio del día")
        Hint("Los límites diarios se reinician a esta hora. Si duermes de madrugada, pon por ejemplo 04:00.")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Hora (HH:mm)") },
            singleLine = true,
            isError = minute == null,
            supportingText = { if (minute == null) Text("Escribe una hora válida, por ejemplo 04:00") },
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton("Guardar hora", { minute?.let(onSave) }, Modifier.padding(top = 8.dp), enabled = minute != null)
    }
}

@Composable
private fun AppPinCard(viewModel: SettingsViewModel, enabled: Boolean) {
    var newPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AppCard {
        SectionTitle("PIN de la app", FgIcons.Lock)
        Hint(
            if (enabled) "Se pide al abrir FocusGuard. Es el mismo PIN que usa el modo estricto."
            else "Pide un PIN cada vez que abres FocusGuard, para que nadie cambie tus reglas.",
        )
        if (viewModel.hasPin) PinField("PIN actual", currentPin, { currentPin = it })
        PinField(
            label = if (viewModel.hasPin) "Nuevo PIN (déjalo vacío para conservar el actual)" else "PIN de 4 a 8 dígitos",
            value = newPin,
            onChange = { newPin = it },
            modifier = Modifier.padding(top = 8.dp),
            error = error,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(
                text = if (enabled) "Cambiar PIN" else "Activar PIN",
                onClick = {
                    error = viewModel.enableAppPin(newPin, currentPin)
                    if (error == null) { newPin = ""; currentPin = "" }
                },
                modifier = Modifier.weight(1f),
            )
            if (enabled) {
                SecondaryButton(
                    text = "Quitar PIN",
                    onClick = {
                        error = viewModel.disableAppPin(currentPin)
                        if (error == null) currentPin = ""
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
