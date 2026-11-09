package com.focusguard.ui.strict

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.StrictMode
import com.focusguard.model.ExitMethod
import com.focusguard.model.ExitRequirement
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.StrictAction
import com.focusguard.model.StrictConfig
import com.focusguard.model.StrictProtection
import com.focusguard.model.StrictState
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.CheckRow
import com.focusguard.ui.components.HelpAction
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.StepperField
import com.focusguard.ui.components.PinField
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.StatusCard
import com.focusguard.ui.components.StatusKind
import com.focusguard.ui.components.SwitchCard
import com.focusguard.ui.profiles.IntervalsEditor
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.label
import com.focusguard.ui.rememberTrustedNowMs
import java.time.Duration
import java.time.Instant

@Composable
fun StrictScreen(viewModel: StrictViewModel, onBack: () -> Unit, onHelp: () -> Unit = {}) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val request by viewModel.request.collectAsStateWithLifecycle()
    val nowMs = rememberTrustedNowMs()
    val now = Instant.ofEpochMilli(nowMs)
    val state = viewModel.state(now)

    Scaffold(
        topBar = {
            FgTopBar("Modo estricto", onBack = onBack, actions = { HelpAction(onHelp) })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StateCard(state, config, now) }
            if (state == StrictState.PAUSED) item { PrimaryButton("Reanudar ahora", viewModel::resume) }
            if (state != StrictState.INACTIVE || request != null) {
                item { ExitCard(viewModel, config, request?.action, now, state) }
            }
            if (viewModel.canEditConfig(now)) {
                item { ConfigEditor(viewModel, config) }
            } else {
                item { Hint("La configuración se bloquea mientras el modo estricto está activo. Para cambiarla, pide antes pausarlo o desactivarlo.") }
            }
        }
    }
}

@Composable
private fun StateCard(state: StrictState, config: StrictConfig, now: Instant) {
    when (state) {
        StrictState.ACTIVE -> StatusCard("Activo ahora", "Tus perfiles y los ajustes protegidos no se pueden tocar.", FgIcons.Lock, StatusKind.Active)
        StrictState.PAUSED -> StatusCard(
            "En pausa",
            "Se reanuda en ${formatRemaining(Duration.between(now, config.pausedUntil ?: now))}.",
            FgIcons.Info,
            StatusKind.Neutral,
        )
        StrictState.WAITING -> StatusCard("En espera", "Se activará cuando llegue su horario.", FgIcons.Info, StatusKind.Neutral)
        StrictState.INACTIVE -> StatusCard("Desactivado", "Actívalo abajo para proteger tus reglas.", FgIcons.Info, StatusKind.Neutral)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExitCard(viewModel: StrictViewModel, config: StrictConfig, action: StrictAction?, now: Instant, state: StrictState) {
    var pin by remember { mutableStateOf("") }
    AppCard {
        if (action == null) {
            SectionTitle("Pausar o desactivar")
            Hint("Para hacerlo tendrás que cumplir los métodos de salida que elegiste.")
            if (state == StrictState.ACTIVE) {
                Text("Pausar durante", style = MaterialTheme.typography.titleSmall)
                FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrictMode.PAUSE_MINUTES.forEach { minutes ->
                        SecondaryButton("$minutes min", { viewModel.requestExit(StrictAction.Pause(minutes)) })
                    }
                }
            }
            SecondaryButton("Desactivar el modo estricto", { viewModel.requestExit(StrictAction.Disable) }, Modifier.padding(top = 12.dp))
            return@AppCard
        }

        val status = viewModel.status(pin, now)
        SectionTitle(
            when (action) {
                is StrictAction.Pause -> "Pausar ${action.minutes} minutos"
                StrictAction.Disable -> "Desactivar el modo estricto"
            },
        )
        status?.pending?.forEach { Text("• ${requirementText(it, now)}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp)) }
        if (status?.satisfied == true) Text("Todo cumplido. Ya puedes confirmar.", modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge)
        if (config.exitMethods.any { it is ExitMethod.Pin }) {
            PinField("PIN", pin, { pin = it }, Modifier.padding(top = 12.dp))
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton("Confirmar", { viewModel.confirm(pin) }, Modifier.weight(1f), enabled = status?.satisfied == true)
            SecondaryButton("Cancelar", viewModel::cancelRequest, Modifier.weight(1f))
        }
    }
}

internal fun requirementText(requirement: ExitRequirement, now: Instant) = when (requirement) {
    is ExitRequirement.WaitUntil -> "Espera ${formatRemaining(Duration.between(now, requirement.at))}"
    ExitRequirement.NeedCharger -> "Conecta el cargador"
    ExitRequirement.NeedPin -> "Escribe tu PIN"
    ExitRequirement.WaitForScheduleEnd -> "Solo se puede cuando termine el horario"
    ExitRequirement.RequestExpired -> "La espera caducó: cancela y vuelve a pedirlo"
    ExitRequirement.NeedPartner -> "Espera a que tu compañero lo apruebe (te avisaremos)"
    ExitRequirement.NoPartner -> "Ya no tienes compañero vinculado: vincula uno para poder pedirle que lo apruebe"
}

@Composable
private fun ConfigEditor(viewModel: StrictViewModel, config: StrictConfig) {
    var enabled by remember(config) { mutableStateOf(config.enabled) }
    var protections by remember(config) { mutableStateOf(config.protections) }
    var intervals by remember(config) { mutableStateOf(config.intervals) }
    var timerOn by remember(config) { mutableStateOf(config.exitMethods.any { it is ExitMethod.Timer }) }
    var timerMinutes by remember(config) { mutableStateOf((config.exitMethods.filterIsInstance<ExitMethod.Timer>().firstOrNull()?.minutes ?: 10).toString()) }
    var cooldownOn by remember(config) { mutableStateOf(config.exitMethods.any { it is ExitMethod.Cooldown }) }
    var cooldownMinutes by remember(config) { mutableStateOf((config.exitMethods.filterIsInstance<ExitMethod.Cooldown>().firstOrNull()?.minutes ?: 30).toString()) }
    var followOn by remember(config) { mutableStateOf(ExitMethod.FollowSchedule in config.exitMethods) }
    var chargerOn by remember(config) { mutableStateOf(ExitMethod.Charger in config.exitMethods) }
    var pinOn by remember(config) { mutableStateOf(ExitMethod.Pin in config.exitMethods) }
    var partnerOn by remember(config) { mutableStateOf(ExitMethod.Partner in config.exitMethods) }
    var newPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchCard("Activar modo estricto", "Mientras esté activo no podrás saltarte tus propias reglas.", enabled, { enabled = it })

        AppCard {
            SectionTitle("Qué protege", FgIcons.Shield)
            ProtectionRow("No editar perfiles", "Ni apagarlos ni borrarlos mientras hay un bloqueo.", StrictProtection.EDIT_LOCK, protections) { protections = it }
            ProtectionRow("Cerrar los ajustes que desactivan la app", "Accesibilidad, administrador, información de la app y desinstalar.", StrictProtection.SETTINGS_GUARD, protections) { protections = it }
            ProtectionRow("Bloquear apps recientes", null, StrictProtection.RECENTS, protections) { protections = it }
            ProtectionRow("Impedir la pantalla dividida", null, StrictProtection.SPLIT_SCREEN, protections) { protections = it }
            ProtectionRow("Cerrar el menú de apagado", "Cierra el menú al pulsar largo el botón de encendido. No impide reiniciar con los botones.", StrictProtection.POWER_MENU, protections) { protections = it }
            ProtectionRow("Cerrar los diálogos de compra", "Las compras dentro de apps (Google Play). Se comprueba en cada teléfono.", StrictProtection.PURCHASE_DIALOGS, protections) { protections = it }
            ProtectionRow("Cerrar el administrador de tareas y el cambio de usuario", "Servicios en ejecución y añadir un usuario o invitado, que se saltarían el bloqueo.", StrictProtection.TASK_MANAGER, protections) { protections = it }
            ProtectionRow("Cerrar el ahorro extremo de batería", "El modo que apaga las apps en segundo plano, también FocusGuard.", StrictProtection.BATTERY_SAVER, protections) { protections = it }
            ProtectionRow("Bloquear el panel de notificaciones", "Cierra también los ajustes rápidos. Solo para quien lo necesite.", StrictProtection.NOTIFICATION_SHADE, protections) { protections = it }
        }

        AppCard {
            SectionTitle("Cuándo está activo", FgIcons.Timer)
            Hint(if (intervals.isEmpty()) "Siempre, mientras esté activado." else "Solo en estos horarios.")
            IntervalsEditor(
                intervals = intervals,
                onAdd = { days, start, end -> intervals = (intervals + days.sortedBy { it.value }.map { ScheduleInterval(it, start, end) }).distinct() },
                onRemove = { intervals = intervals - it },
            )
        }

        AppCard {
            SectionTitle("Cómo se pausa o se desactiva")
            Hint("Tendrás que cumplir todos los que marques.")
            CheckRow("Esperar un tiempo", null, timerOn, { timerOn = it })
            if (timerOn) StepperField("Minutos de espera", timerMinutes, { timerMinutes = it }, Modifier.padding(start = 48.dp, top = 4.dp), step = 5)
            CheckRow("Pedirlo y esperar", "Se concede tras la espera y vale 10 minutos.", cooldownOn, { cooldownOn = it })
            if (cooldownOn) StepperField("Minutos hasta concederlo", cooldownMinutes, { cooldownMinutes = it }, Modifier.padding(start = 48.dp, top = 4.dp), step = 5)
            CheckRow("Seguir el horario", "Solo se puede fuera del horario.", followOn, { followOn = it })
            CheckRow("Cargador conectado", null, chargerOn, { chargerOn = it })
            CheckRow("Escribir un PIN", null, pinOn, { pinOn = it })
            CheckRow("Que mi compañero lo apruebe", "Le llega un correo con el botón de aprobar. Hasta que responda no podrás confirmar. Necesitas un compañero vinculado.", partnerOn, { partnerOn = it })
            if (pinOn) {
                if (viewModel.hasPin) PinField("PIN actual (solo si vas a cambiarlo)", currentPin, { currentPin = it }, Modifier.padding(top = 8.dp))
                PinField(
                    label = if (viewModel.hasPin) "Nuevo PIN (vacío = conservar)" else "PIN de 4 a 8 dígitos",
                    value = newPin,
                    onChange = { newPin = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        PrimaryButton(
            text = "Guardar",
            onClick = {
                val methods = buildList<ExitMethod> {
                    if (timerOn) add(ExitMethod.Timer((timerMinutes.toIntOrNull() ?: 0).coerceAtLeast(1)))
                    if (cooldownOn) add(ExitMethod.Cooldown((cooldownMinutes.toIntOrNull() ?: 0).coerceAtLeast(1)))
                    if (followOn) add(ExitMethod.FollowSchedule)
                    if (chargerOn) add(ExitMethod.Charger)
                    if (pinOn) add(ExitMethod.Pin)
                    if (partnerOn) add(ExitMethod.Partner)
                }
                error = viewModel.save(StrictConfig(enabled, intervals, protections, methods, config.pausedUntil), if (pinOn) newPin else "", currentPin)
                if (error == null) { newPin = ""; currentPin = "" }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ProtectionRow(title: String, hint: String?, protection: StrictProtection, selected: Set<StrictProtection>, onChange: (Set<StrictProtection>) -> Unit) =
    CheckRow(title, hint, protection in selected, { onChange(if (it) selected + protection else selected - protection) })
