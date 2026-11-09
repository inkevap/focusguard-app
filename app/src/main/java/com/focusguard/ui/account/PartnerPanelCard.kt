package com.focusguard.ui.account

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.decision.PartnerChoices
import com.focusguard.decision.PartnerPanel
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
internal fun PartnerPanelCard(panel: PartnerPanel, parentalOn: Boolean, onChange: (PartnerPanel) -> Unit) {
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

        SectionTitle("Qué se protege con tu compañero")
        PartnerPanel.PROTECTIONS.forEach { protection ->
            val (title, hint) = protectionText(protection)
            CheckRow(title, hint, protection in shown.protections, { on -> update(shown.copy(protections = if (on) shown.protections + protection else shown.protections - protection)) })
        }
    }
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
