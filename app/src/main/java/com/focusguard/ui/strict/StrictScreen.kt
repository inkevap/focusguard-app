package com.focusguard.ui.strict

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.StrictMode
import com.focusguard.model.ExitMethod
import com.focusguard.model.ExitRequirement
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.StrictAction
import com.focusguard.model.StrictConfig
import com.focusguard.model.StrictProtection
import com.focusguard.model.StrictState
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgCheckRow
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.HelpAction
import com.focusguard.ui.components.PinField
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.StepperField
import com.focusguard.ui.components.SubPageHost
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.plural
import com.focusguard.ui.profiles.BottomAction
import com.focusguard.ui.profiles.GroupStack
import com.focusguard.ui.profiles.IntervalsEditor
import com.focusguard.ui.rememberTrustedNowMs
import java.time.Duration
import java.time.Instant

/** Las subpantallas del modo estricto: la configuración que se edita antes de guardar. */
private enum class StrictPage(val title: String) {
    PROTECTS("Qué protege"),
    SCHEDULE("Cuándo está activo"),
    EXIT("Cómo se pausa o se desactiva"),
}

/** El borrador de la configuración: vive en la pantalla para que las subpantallas lo compartan hasta pulsar «Guardar». */
private class StrictDraft(config: StrictConfig) {
    var enabled by mutableStateOf(config.enabled)
    var protections by mutableStateOf(config.protections)
    var intervals by mutableStateOf(config.intervals)
    var timerOn by mutableStateOf(config.exitMethods.any { it is ExitMethod.Timer })
    var timerMinutes by mutableStateOf((config.exitMethods.filterIsInstance<ExitMethod.Timer>().firstOrNull()?.minutes ?: 10).toString())
    var cooldownOn by mutableStateOf(config.exitMethods.any { it is ExitMethod.Cooldown })
    var cooldownMinutes by mutableStateOf((config.exitMethods.filterIsInstance<ExitMethod.Cooldown>().firstOrNull()?.minutes ?: 30).toString())
    var followOn by mutableStateOf(ExitMethod.FollowSchedule in config.exitMethods)
    var chargerOn by mutableStateOf(ExitMethod.Charger in config.exitMethods)
    var pinOn by mutableStateOf(ExitMethod.Pin in config.exitMethods)
    var partnerOn by mutableStateOf(ExitMethod.Partner in config.exitMethods)
    var newPin by mutableStateOf("")
    var currentPin by mutableStateOf("")
    var error by mutableStateOf<String?>(null)

    val methodCount get() = listOf(timerOn, cooldownOn, followOn, chargerOn, pinOn, partnerOn).count { it }
}

@Composable
fun StrictScreen(viewModel: StrictViewModel, onBack: () -> Unit, onHelp: () -> Unit = {}) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val request by viewModel.request.collectAsStateWithLifecycle()
    val nowMs = rememberTrustedNowMs()
    val now = Instant.ofEpochMilli(nowMs)
    val state = viewModel.state(now)
    // La configuración se bloquea mientras el modo estricto está activo; si se bloquea con una subpantalla abierta, se vuelve a la raíz.
    val editable = viewModel.canEditConfig(now)
    val draft = remember(config) { StrictDraft(config) }
    var page by rememberSaveable { mutableStateOf<StrictPage?>(null) }

    SubPageHost(
        page = if (editable) page else null,
        onBack = { page = null },
        root = {
            FgScreen(
                "Modo estricto", onBack = onBack,
                actions = { HelpAction(onHelp) },
                bottomBar = {
                    if (editable) {
                        BottomAction {
                            draft.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                            PrimaryButton("Guardar", { save(viewModel, config, draft) }, Modifier.fillMaxWidth())
                        }
                    }
                },
            ) {
                item { StateHero(state, config, now) }
                if (state == StrictState.PAUSED) item { PrimaryButton("Reanudar ahora", viewModel::resume, Modifier.fillMaxWidth()) }
                if (state != StrictState.INACTIVE || request != null) {
                    item { ExitGroups(viewModel, config, request?.action, now, state) }
                }
                if (editable) {
                    item { FgGroup(footer = "Mientras esté activo no podrás saltarte tus propias reglas.") { FgSwitchRow("Activar modo estricto", draft.enabled, { draft.enabled = it }) } }
                    item {
                        FgGroup {
                            FgRow("Qué protege", value = "${draft.protections.size} de ${StrictProtection.entries.size}", onClick = { page = StrictPage.PROTECTS })
                            FgRow("Cuándo está activo", value = if (draft.intervals.isEmpty()) "Siempre" else plural(draft.intervals.size, "horario", "horarios"), onClick = { page = StrictPage.SCHEDULE })
                            FgRow("Cómo se pausa o se desactiva", value = if (draft.methodCount == 0) "Ninguno" else plural(draft.methodCount, "método", "métodos"), onClick = { page = StrictPage.EXIT })
                        }
                    }
                } else {
                    item { FgNote("La configuración se bloquea mientras el modo estricto está activo. Para cambiarla, pide antes pausarlo o desactivarlo.") }
                }
            }
        },
        sub = { current ->
            FgScreen(current.title, onBack = { page = null }) {
                item {
                    when (current) {
                        StrictPage.PROTECTS -> ProtectsGroup(draft)
                        StrictPage.SCHEDULE -> ScheduleGroup(draft)
                        StrictPage.EXIT -> ExitMethodsGroups(draft, viewModel.hasPin)
                    }
                }
            }
        },
    )
}


private fun save(viewModel: StrictViewModel, config: StrictConfig, draft: StrictDraft) {
    val methods = buildList<ExitMethod> {
        if (draft.timerOn) add(ExitMethod.Timer((draft.timerMinutes.toIntOrNull() ?: 0).coerceAtLeast(1)))
        if (draft.cooldownOn) add(ExitMethod.Cooldown((draft.cooldownMinutes.toIntOrNull() ?: 0).coerceAtLeast(1)))
        if (draft.followOn) add(ExitMethod.FollowSchedule)
        if (draft.chargerOn) add(ExitMethod.Charger)
        if (draft.pinOn) add(ExitMethod.Pin)
        if (draft.partnerOn) add(ExitMethod.Partner)
    }
    draft.error = viewModel.save(StrictConfig(draft.enabled, draft.intervals, draft.protections, methods, config.pausedUntil), if (draft.pinOn) draft.newPin else "", draft.currentPin)
    if (draft.error == null) { draft.newPin = ""; draft.currentPin = "" }
}

@Composable
private fun StateHero(state: StrictState, config: StrictConfig, now: Instant) {
    when (state) {
        StrictState.ACTIVE -> FgHero("Activo ahora", body = "Tus perfiles y los ajustes protegidos no se pueden tocar.", accent = true)
        StrictState.PAUSED -> FgHero("En pausa", body = "Se reanuda en ${formatRemaining(Duration.between(now, config.pausedUntil ?: now))}.")
        StrictState.WAITING -> FgHero("En espera", body = "Se activará cuando llegue su horario.")
        StrictState.INACTIVE -> FgHero("Desactivado", body = "Actívalo abajo para proteger tus reglas.")
    }
}

@Composable
private fun ExitGroups(viewModel: StrictViewModel, config: StrictConfig, action: StrictAction?, now: Instant, state: StrictState) {
    var pin by remember { mutableStateOf("") }
    GroupStack {
        if (action == null) {
            FgGroup(header = "Pausar o desactivar", footer = "Para hacerlo tendrás que cumplir los métodos de salida que elegiste.") {
                if (state == StrictState.ACTIVE) {
                    StrictMode.PAUSE_MINUTES.forEach { minutes ->
                        FgActionRow("Pausar $minutes min", { viewModel.requestExit(StrictAction.Pause(minutes)) })
                    }
                }
                FgActionRow("Desactivar el modo estricto", { viewModel.requestExit(StrictAction.Disable) }, danger = true)
            }
            return@GroupStack
        }

        val status = viewModel.status(pin, now)
        val title = when (action) {
            is StrictAction.Pause -> "Pausar ${action.minutes} minutos"
            StrictAction.Disable -> "Desactivar el modo estricto"
        }
        FgGroup(header = title) {
            status?.pending?.forEach { FgRow(requirementText(it, now)) }
            if (status?.satisfied == true) FgRow("Todo cumplido. Ya puedes confirmar.")
            if (config.exitMethods.any { it is ExitMethod.Pin }) FgCell { PinField("PIN", pin, { pin = it }) }
        }
        FgGroup {
            FgActionRow("Confirmar", { viewModel.confirm(pin) }, enabled = status?.satisfied == true)
            FgActionRow("Cancelar", viewModel::cancelRequest, danger = true)
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
private fun ProtectsGroup(draft: StrictDraft) {
    FgGroup {
        ProtectionRow(draft, "No editar perfiles", "Ni apagarlos ni borrarlos mientras hay un bloqueo.", StrictProtection.EDIT_LOCK)
        ProtectionRow(draft, "Cerrar los ajustes que desactivan la app", "Accesibilidad, administrador, información de la app y desinstalar.", StrictProtection.SETTINGS_GUARD)
        ProtectionRow(draft, "Bloquear apps recientes", null, StrictProtection.RECENTS)
        ProtectionRow(draft, "Impedir la pantalla dividida", null, StrictProtection.SPLIT_SCREEN)
        ProtectionRow(draft, "Cerrar el menú de apagado", "Cierra el menú al pulsar largo el botón de encendido. No impide reiniciar con los botones.", StrictProtection.POWER_MENU)
        ProtectionRow(draft, "Cerrar los diálogos de compra", "Las compras dentro de apps (Google Play). Se comprueba en cada teléfono.", StrictProtection.PURCHASE_DIALOGS)
        ProtectionRow(draft, "Cerrar el administrador de tareas y el cambio de usuario", "Servicios en ejecución y añadir un usuario o invitado, que se saltarían el bloqueo.", StrictProtection.TASK_MANAGER)
        ProtectionRow(draft, "Cerrar el ahorro extremo de batería", "El modo que apaga las apps en segundo plano, también FocusGuard.", StrictProtection.BATTERY_SAVER)
        ProtectionRow(draft, "Bloquear el panel de notificaciones", "Cierra también los ajustes rápidos. Solo para quien lo necesite.", StrictProtection.NOTIFICATION_SHADE)
    }
}

@Composable
private fun ProtectionRow(draft: StrictDraft, title: String, hint: String?, protection: StrictProtection) =
    FgCheckRow(
        title, protection in draft.protections,
        { draft.protections = if (it) draft.protections + protection else draft.protections - protection },
        subtitle = hint,
    )

@Composable
private fun ScheduleGroup(draft: StrictDraft) {
    GroupStack {
        FgNote(if (draft.intervals.isEmpty()) "Siempre, mientras esté activado." else "Solo en estos horarios.")
        FgGroup {
            FgCell {
                IntervalsEditor(
                    intervals = draft.intervals,
                    onAdd = { days, start, end -> draft.intervals = (draft.intervals + days.sortedBy { it.value }.map { ScheduleInterval(it, start, end) }).distinct() },
                    onRemove = { draft.intervals = draft.intervals - it },
                )
            }
        }
    }
}

@Composable
private fun ExitMethodsGroups(draft: StrictDraft, hasPin: Boolean) {
    GroupStack {
        FgGroup(header = "Hará falta", footer = "Tendrás que cumplir todos los que marques.") {
            FgCheckRow("Esperar un tiempo", draft.timerOn, { draft.timerOn = it })
            if (draft.timerOn) FgCell { StepperField("Minutos de espera", draft.timerMinutes, { draft.timerMinutes = it }, step = 5) }
            FgCheckRow("Pedirlo y esperar", draft.cooldownOn, { draft.cooldownOn = it }, subtitle = "Se concede tras la espera y vale 10 minutos.")
            if (draft.cooldownOn) FgCell { StepperField("Minutos hasta concederlo", draft.cooldownMinutes, { draft.cooldownMinutes = it }, step = 5) }
            FgCheckRow("Seguir el horario", draft.followOn, { draft.followOn = it }, subtitle = "Solo se puede fuera del horario.")
            FgCheckRow("Cargador conectado", draft.chargerOn, { draft.chargerOn = it })
            FgCheckRow("Escribir un PIN", draft.pinOn, { draft.pinOn = it })
            FgCheckRow(
                "Que mi compañero lo apruebe", draft.partnerOn, { draft.partnerOn = it },
                subtitle = "Le llega un correo con el botón de aprobar. Hasta que responda no podrás confirmar. Necesitas un compañero vinculado.",
            )
        }
        if (draft.pinOn) {
            FgGroup(header = "PIN") {
                FgCell {
                    if (hasPin) PinField("PIN actual (solo si vas a cambiarlo)", draft.currentPin, { draft.currentPin = it })
                    PinField(
                        label = if (hasPin) "Nuevo PIN (vacío = conservar)" else "PIN de 4 a 8 dígitos",
                        value = draft.newPin,
                        onChange = { draft.newPin = it },
                    )
                }
            }
        }
    }
}
