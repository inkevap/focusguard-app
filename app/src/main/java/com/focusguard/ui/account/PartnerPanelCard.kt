package com.focusguard.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.decision.PartnerArea
import com.focusguard.decision.PartnerChoices
import com.focusguard.decision.PartnerPanel
import com.focusguard.model.BlockReason
import com.focusguard.model.PartnerEventType
import com.focusguard.model.StrictProtection
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.CheckRow
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SectionTitle

/**
 * Qué se le avisa a tu compañero y qué se protege con él, sin necesitar el modo estricto. Se elige aparte para «Avisarle lo que pasa» y para el
 * modo parental; vale el del modo que esté en uso. Añadir es libre; quitar un aviso o una protección lo tiene que aprobar el compañero.
 */
@Composable
internal fun PartnerPanelCard(panel: PartnerPanel, parentalOn: Boolean, profiles: List<Pair<Long, String>>, onChange: (PartnerPanel) -> Unit) {
    var parentalTab by rememberSaveable { mutableStateOf(parentalOn) }
    val shown = if (parentalTab) panel.parental else panel.watch
    fun update(choices: PartnerChoices) = onChange(if (parentalTab) panel.copy(parental = choices) else panel.copy(watch = choices))

    AppCard {
        SectionTitle("Qué se le avisa y qué se protege")
        Hint(
            "Elige qué le llega a tu compañero y qué protecciones valen mientras tengas uno, sin activar el modo estricto. " +
                "Poner más es libre; quitar algo lo tiene que aprobar él.",
        )
        ChipChoices(
            options = listOf(false to "Avisarle lo que pasa", true to "Modo parental"),
            selected = parentalTab,
            onSelect = { parentalTab = it },
            modifier = Modifier.padding(top = 8.dp),
        )
        Hint(if (parentalTab == parentalOn) "Es el modo que está en uso ahora." else "No es el modo en uso: se aplicará cuando lo uses.")

        SectionTitle("Qué se le avisa")
        PartnerPanel.NOTICE_TYPES.forEach { type ->
            CheckRow(noticeLabel(type), null, type !in shown.noticesOff, { on -> update(shown.copy(noticesOff = if (on) shown.noticesOff - type else shown.noticesOff + type)) })
        }

        SectionTitle("De lo bloqueado, de qué se le avisa")
        Hint("Solo cuenta si está encendido «Cada vez que intento abrir algo bloqueado». Elige por clase de cosa y por perfil.")
        PartnerPanel.BLOCK_REASONS.forEach { reason ->
            CheckRow(reasonLabel(reason), null, reason !in shown.blockedReasonsOff, { on -> update(shown.copy(blockedReasonsOff = if (on) shown.blockedReasonsOff - reason else shown.blockedReasonsOff + reason)) })
        }
        if (profiles.isNotEmpty()) {
            Hint("Por perfil:")
            profiles.forEach { (id, name) ->
                CheckRow(name, null, id !in shown.blockedProfilesOff, { on -> update(shown.copy(blockedProfilesOff = if (on) shown.blockedProfilesOff - id else shown.blockedProfilesOff + id)) })
            }
        }

        SectionTitle("Qué cambios aprueba él")
        Hint("Cambiar o aflojar cada una de estas funciones pasa siempre por tu compañero, aunque no esté el modo estricto.")
        PartnerArea.entries.forEach { area ->
            val (title, hint) = areaText(area)
            CheckRow(title, hint, area in shown.areas, { on -> update(shown.copy(areas = if (on) shown.areas + area else shown.areas - area)) })
        }

        SectionTitle("Qué pantallas se cierran con tu compañero")
        PartnerPanel.PROTECTIONS.forEach { protection ->
            val (title, hint) = protectionText(protection)
            CheckRow(title, hint, protection in shown.protections, { on -> update(shown.copy(protections = if (on) shown.protections + protection else shown.protections - protection)) })
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
