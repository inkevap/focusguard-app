package com.focusguard.ui

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.permissions.Permission
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.StatusChip

internal data class PermissionInfo(val title: String, val reason: String, val action: String)

internal fun Permission.info() = when (this) {
    Permission.ACCESSIBILITY -> PermissionInfo(
        "Accesibilidad",
        "Para saber qué app o web abres y poder bloquearla. Busca FocusGuard en la lista y actívalo.",
        "Activar",
    )
    Permission.USAGE_STATS -> PermissionInfo(
        "Acceso de uso",
        "Para medir cuánto usas cada app y respaldar la detección.",
        "Dar acceso",
    )
    Permission.OVERLAY -> PermissionInfo(
        "Mostrar sobre otras apps",
        "Para poner la pantalla de bloqueo encima de lo bloqueado.",
        "Permitir",
    )
    Permission.BATTERY -> PermissionInfo(
        "Batería sin restricciones",
        "Evita que el sistema detenga FocusGuard en segundo plano.",
        "Quitar límite",
    )
    Permission.ADMIN -> PermissionInfo(
        "Administrador del dispositivo",
        "Impide desinstalar FocusGuard sin desactivarlo antes.",
        "Activar",
    )
    Permission.LOCATION -> PermissionInfo(
        "Ubicación",
        "Para perfiles que se activan con una red Wi-Fi o en un lugar. La posición no sale del teléfono.",
        "Permitir",
    )
    Permission.BLUETOOTH -> PermissionInfo(
        "Bluetooth",
        "Para perfiles que se activan con unos auriculares, el coche u otro dispositivo Bluetooth conectado. Solo se lee el nombre de lo conectado y no sale del teléfono.",
        "Permitir",
    )
    Permission.NOTIFICATION_ACCESS -> PermissionInfo(
        "Acceso a las notificaciones",
        "Opcional. Para quitar las notificaciones de las apps bloqueadas y guardarlas en el teléfono, y esconder el aviso de segundo plano.",
        "Dar acceso",
    )
    Permission.BACKGROUND_LOCATION -> PermissionInfo(
        "Ubicación siempre",
        "Para que un perfil por lugar funcione con la app cerrada. En Permisos > Ubicación, elige \"Permitir todo el tiempo\".",
        "Abrir ajustes",
    )
}

@Composable
fun PermissionsScreen(
    granted: Map<Permission, Boolean>,
    onGrant: (Permission) -> Unit,
    onOpenAppDetails: () -> Unit,
    onBack: () -> Unit,
) {
    val allRequiredGranted = Permission.entries.filter { it.required }.all { granted[it] == true }
    FgScreen("Permisos", onBack = onBack) {
        item {
            FgNote(
                if (allRequiredGranted) "Todo listo: FocusGuard ya puede bloquear."
                else "Android pide que los actives tú. Cada uno abre su pantalla de ajustes y, al volver, se comprueba solo.",
            )
        }
        item { PermissionGroup("Necesarios", Permission.entries.filter { it.required }, granted, onGrant) }
        item { PermissionGroup("Recomendados", Permission.entries.filterNot { it.required }, granted, onGrant) }
        item {
            FgGroup(
                header = "Inicio automático",
                footer = "Xiaomi, Huawei, Oppo y otras marcas lo exigen para que FocusGuard vuelva a funcionar tras reiniciar. Android no deja comprobarlo: ábrelo y actívalo si existe.",
            ) {
                FgActionRow("Abrir ajustes de la app", onOpenAppDetails)
            }
        }
    }
}

/** Cada permiso es una fila con su estado; si falta, lleva el botón para concederlo. */
@Composable
private fun PermissionGroup(header: String, permissions: List<Permission>, granted: Map<Permission, Boolean>, onGrant: (Permission) -> Unit) {
    FgGroup(header = header) {
        permissions.forEach { permission ->
            val info = permission.info()
            val ok = granted[permission] == true
            FgRow(
                info.title, subtitle = info.reason,
                icon = if (ok) FgIcons.CheckCircle else FgIcons.Warning,
                iconColor = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                value = if (ok) "Listo" else null,
                trailing = if (ok) null else {
                    { TextButton(onClick = { onGrant(permission) }) { Text(info.action) } }
                },
            )
        }
    }
}

internal fun LazyListScope.permissionCards(
    permissions: List<Permission>,
    granted: Map<Permission, Boolean>,
    onGrant: (Permission) -> Unit,
) {
    permissions.forEach { permission ->
        item(key = permission.name) {
            val info = permission.info()
            val ok = granted[permission] == true
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        imageVector = if (ok) FgIcons.CheckCircle else FgIcons.Warning,
                        contentDescription = if (ok) "Concedido" else "Falta",
                        tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(info.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (ok) StatusChip("Listo")
                }
                Text(
                    info.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (!ok) PrimaryButton(info.action, { onGrant(permission) }, Modifier.padding(top = 12.dp))
            }
        }
    }
}
