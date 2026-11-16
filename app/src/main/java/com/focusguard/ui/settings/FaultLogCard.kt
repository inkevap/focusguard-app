package com.focusguard.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.focusguard.BuildConfig
import com.focusguard.decision.Fault
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.profiles.GroupStack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WHEN = DateTimeFormatter.ofPattern("d MMM HH:mm:ss", Locale.forLanguageTag("es"))

private fun at(ms: Long): String = WHEN.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

private fun Fault.times(): String = if (count == 1) at(lastMs) else "$count veces · de ${at(firstMs)} a ${at(lastMs)}"

/** El registro como texto plano, el más reciente primero: para pegarlo donde haga falta. */
internal fun faultText(log: List<Fault>): String = buildString {
    append("FocusGuard ${BuildConfig.VERSION_NAME} · registro de fallos\n")
    log.asReversed().forEach { append("\n[${it.source}] ${it.times()}\n${it.message}\n") }
}

/**
 * El registro de fallos de la app (el más reciente arriba). Sirve para saber qué falló aunque haya pasado tiempo: se guarda solo en el teléfono y solo sale
 * de él si se copia.
 */
@Composable
internal fun FaultLogContent(log: List<Fault>, onClear: () -> Unit) {
    val context = LocalContext.current
    GroupStack {
        FgNote("Lo que ha fallado dentro de la app, aunque se haya recuperado sola. Se guarda solo en este teléfono (los ${com.focusguard.decision.FaultLog.MAX_ENTRIES} más recientes) y no se envía a ningún sitio.")
        if (log.isEmpty()) {
            FgGroup { FgCell { Text("No hay ningún fallo apuntado.", style = MaterialTheme.typography.bodyMedium) } }
        } else {
            FgGroup {
                FgActionRow("Copiar el registro", { copy(context, faultText(log)) }, subtitle = "Para pegarlo en un mensaje o en una conversación.")
                FgActionRow("Borrar el registro", onClear, danger = true)
            }
            FgGroup {
                log.asReversed().forEach { fault ->
                    FgCell {
                        Text(fault.source, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        Text(fault.times(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(fault.message, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Registro de fallos", text))
    Toast.makeText(context, "Registro copiado", Toast.LENGTH_SHORT).show()
}
