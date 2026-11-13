package com.focusguard.ui.account

import androidx.compose.runtime.Composable
import com.focusguard.decision.PartnerArea
import com.focusguard.decision.PartnerChoices
import com.focusguard.decision.PartnerPanel
import com.focusguard.model.BlockReason
import com.focusguard.model.PartnerEventType
import com.focusguard.model.StrictProtection
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgCheckRow
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.profiles.GroupStack

/** Las listas de casillas del panel del compañero: cada una es una subpantalla. */
internal enum class PanelList(val title: String) {
    NOTICES("Qué se le avisa"),
    BLOCKED("De lo bloqueado, de qué"),
    AREAS("Qué cambios aprueba él"),
    SCREENS("Qué pantallas se cierran"),
}

private fun PartnerPanel.choices(parental: Boolean) = if (parental) this.parental else watch

/**
 * Qué se le avisa a tu compañero y qué se protege con él, sin necesitar el modo estricto. Se elige aparte para «Avisarle lo que pasa» y para el
 * modo parental; vale el del modo que esté en uso. Añadir es libre; quitar un aviso o una protección lo tiene que aprobar el compañero.
 * Esta es la raíz: elige el modo y abre cada lista.
 */
@Composable
internal fun PartnerPanelRoot(panel: PartnerPanel, parentalOn: Boolean, parentalTab: Boolean, onTab: (Boolean) -> Unit, onOpen: (PanelList) -> Unit) {
    val shown = panel.choices(parentalTab)
    val blockedOn = PartnerPanel.BLOCK_REASONS.count { it !in shown.blockedReasonsOff }
    GroupStack {
        FgGroup(
            footer = "Elige qué le llega a tu compañero y qué protecciones valen mientras tengas uno, sin activar el modo estricto. " +
                "Poner más es libre; quitar algo lo tiene que aprobar él. " +
                if (parentalTab == parentalOn) "Es el modo que está en uso ahora." else "No es el modo en uso: se aplicará cuando lo uses.",
        ) {
            FgCell {
                ChipChoices(options = listOf(false to "Avisarle lo que pasa", true to "Modo parental"), selected = parentalTab, onSelect = onTab)
            }
        }
        FgGroup {
            FgRow(PanelList.NOTICES.title, value = "${PartnerPanel.NOTICE_TYPES.count { it !in shown.noticesOff }} de ${PartnerPanel.NOTICE_TYPES.size}", onClick = { onOpen(PanelList.NOTICES) })
            FgRow(PanelList.BLOCKED.title, value = "$blockedOn de ${PartnerPanel.BLOCK_REASONS.size}", onClick = { onOpen(PanelList.BLOCKED) })
            FgRow(PanelList.AREAS.title, value = "${shown.areas.size} de ${PartnerArea.entries.size}", onClick = { onOpen(PanelList.AREAS) })
            FgRow(PanelList.SCREENS.title, value = "${shown.protections.size} de ${PartnerPanel.PROTECTIONS.size}", onClick = { onOpen(PanelList.SCREENS) })
        }
    }
}

/** Una de las listas de casillas del panel, para el modo [parentalTab]. */
@Composable
internal fun PartnerPanelList(list: PanelList, panel: PartnerPanel, parentalTab: Boolean, profiles: List<Pair<Long, String>>, onChange: (PartnerPanel) -> Unit) {
    val shown = panel.choices(parentalTab)
    fun update(choices: PartnerChoices) = onChange(if (parentalTab) panel.copy(parental = choices) else panel.copy(watch = choices))

    GroupStack {
        when (list) {
            PanelList.NOTICES -> FgGroup {
                PartnerPanel.NOTICE_TYPES.forEach { type ->
                    FgCheckRow(noticeLabel(type), type !in shown.noticesOff, { on -> update(shown.copy(noticesOff = if (on) shown.noticesOff - type else shown.noticesOff + type)) })
                }
            }
            PanelList.BLOCKED -> {
                FgGroup(footer = "Solo cuenta si está encendido «Cada vez que intento abrir algo bloqueado». Elige por clase de cosa y por perfil.") {
                    PartnerPanel.BLOCK_REASONS.forEach { reason ->
                        FgCheckRow(reasonLabel(reason), reason !in shown.blockedReasonsOff, { on -> update(shown.copy(blockedReasonsOff = if (on) shown.blockedReasonsOff - reason else shown.blockedReasonsOff + reason)) })
                    }
                }
                if (profiles.isNotEmpty()) {
                    FgGroup(header = "Por perfil") {
                        profiles.forEach { (id, name) ->
                            FgCheckRow(name, id !in shown.blockedProfilesOff, { on -> update(shown.copy(blockedProfilesOff = if (on) shown.blockedProfilesOff - id else shown.blockedProfilesOff + id)) })
                        }
                    }
                }
            }
            PanelList.AREAS -> FgGroup(footer = "Cambiar o aflojar cada una de estas funciones pasa siempre por tu compañero, aunque no esté el modo estricto.") {
                PartnerArea.entries.forEach { area ->
                    val (title, hint) = areaText(area)
                    FgCheckRow(title, area in shown.areas, { on -> update(shown.copy(areas = if (on) shown.areas + area else shown.areas - area)) }, subtitle = hint)
                }
            }
            PanelList.SCREENS -> FgGroup {
                PartnerPanel.PROTECTIONS.forEach { protection ->
                    val (title, hint) = protectionText(protection)
                    FgCheckRow(title, protection in shown.protections, { on -> update(shown.copy(protections = if (on) shown.protections + protection else shown.protections - protection)) }, subtitle = hint)
                }
            }
        }
    }
}

private fun reasonLabel(reason: BlockReason) = when (reason) {
    BlockReason.PROFILE_APP -> "Apps bloqueadas por un perfil"
    BlockReason.PROFILE_WEB -> "Webs bloqueadas por un perfil"
    BlockReason.NOT_ALLOWED -> "Lo que no está permitido (perfiles «permitir solo»)"
    BlockReason.LIMIT_REACHED -> "Límites de uso agotados"
    BlockReason.SUB_APP -> "Secciones dentro de apps (Shorts, Reels…)"
    BlockReason.NEW_APP -> "Apps recién instaladas"
    BlockReason.EMBEDDED_BROWSER -> "Navegadores dentro de apps"
    BlockReason.ADULT_CONTENT -> "Contenido adulto (webs y palabras)"
    BlockReason.IMAGE_CONTENT -> "Imágenes explícitas detectadas"
    else -> reason.name
}

private fun areaText(area: PartnerArea): Pair<String, String?> = when (area) {
    PartnerArea.PROFILES_EDIT -> "Editar, apagar o borrar perfiles" to "Incluye los límites y los horarios."
    PartnerArea.PROFILES_PAUSE -> "Pausar perfiles y terminar bloqueos antes de tiempo" to null
    PartnerArea.ADULT_FILTER -> "Filtro de contenido adulto" to "Apagarlo, quitar palabras o webs, dejar pasar algo o dejar de leer el texto de una app."
    PartnerArea.DNS_FILTER -> "Filtro DNS" to "Apagarlo o dejar una app fuera."
    PartnerArea.IMAGES -> "Detección de imágenes" to "Apagarla, bajar la sensibilidad, vigilar menos o reducir el modo dinámico."
    PartnerArea.NEW_APPS -> "Bloqueo de apps recién instaladas" to null
    PartnerArea.EMBEDDED_BROWSERS -> "Bloqueo de navegadores dentro de apps" to null
    PartnerArea.FLOATING_WINDOWS -> "Cerrar ventanas flotantes" to null
    PartnerArea.APPROVAL_MODE -> "Cómo se aprueban los cambios (Aprobaciones)" to null
    PartnerArea.DAY_START -> "Hora de inicio del día" to "Mover el reloj acorta esperas y reinicia límites."
    PartnerArea.SERVER -> "Servidor y sesión de la cuenta" to "Cambiar de servidor o cerrar la sesión."
    PartnerArea.PARTNER_SETTINGS -> "Ajustes del compañero" to "Apagar avisos, el modo parental, compartir impulsos o este mismo panel."
    PartnerArea.LAUNCHER_ICON -> "Icono y nombre de la app" to "Disimularla en la pantalla de inicio."
    PartnerArea.STREAK -> "Restaurar días de la racha" to null
}

private fun noticeLabel(type: PartnerEventType) = when (type) {
    PartnerEventType.BLOCKED -> "Cada vez que intento abrir algo bloqueado (resumen)"
    PartnerEventType.SETTINGS_ATTEMPT -> "Intento entrar en los ajustes que desactivan la protección"
    PartnerEventType.UNINSTALL_ATTEMPT -> "Intento desinstalar la app"
    PartnerEventType.PROTECTION_OFF -> "Se apaga una protección (accesibilidad, administrador…)"
    PartnerEventType.PANIC -> "Uso el botón de pánico"
    PartnerEventType.EMERGENCY_UNLOCK -> "Uso un desbloqueo de emergencia"
    PartnerEventType.ICON_CHANGED -> "Cambio el icono de la app"
    PartnerEventType.DNS_COUNTS -> "Total diario de consultas DNS (sin dominios)"
    PartnerEventType.URGE_REPORT -> "Resumen semanal de impulsos"
}

private fun protectionText(protection: StrictProtection): Pair<String, String?> = when (protection) {
    StrictProtection.EDIT_LOCK -> "Los cambios de perfiles los aprueba él" to "Editar, apagar o borrar perfiles y aflojar listas pasa por su aprobación."
    StrictProtection.SETTINGS_GUARD -> "Cerrar los ajustes que desactivan la app" to "Accesibilidad, administrador, información de la app y desinstalar."
    StrictProtection.RECENTS -> "Bloquear apps recientes" to null
    StrictProtection.SPLIT_SCREEN -> "Impedir la pantalla dividida" to null
    StrictProtection.POWER_MENU -> "Cerrar el menú de apagado" to null
    StrictProtection.NOTIFICATION_SHADE -> "Bloquear el panel de notificaciones" to "Cierra también los ajustes rápidos."
    StrictProtection.PURCHASE_DIALOGS -> "Cerrar los diálogos de compra" to null
    StrictProtection.TASK_MANAGER -> "Cerrar el administrador de tareas y el cambio de usuario" to null
    StrictProtection.BATTERY_SAVER -> "Cerrar el ahorro extremo de batería" to null
}
