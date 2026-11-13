package com.focusguard.ui.dns

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.data.DnsDay
import com.focusguard.data.DnsQueryEntity
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.plural
import com.focusguard.ui.streak.StreakText
import java.time.LocalDate

class DnsLogActions(val selectDay: (Long) -> Unit, val showBlocked: (Boolean) -> Unit, val clear: () -> Unit)

@Composable
fun DnsLogScreen(viewModel: DnsLogViewModel, onBack: () -> Unit) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val selected by viewModel.selectedDay.collectAsStateWithLifecycle()
    val blocked by viewModel.showBlocked.collectAsStateWithLifecycle()
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    DnsLogContent(
        days, selected, blocked, hosts, remember(days) { viewModel.today },
        DnsLogActions(viewModel::selectDay, viewModel::showBlocked, viewModel::clear),
        onBack = onBack,
    )
}

@Composable
internal fun DnsLogContent(
    days: List<DnsDay>,
    selected: Long?,
    showBlocked: Boolean,
    hosts: List<DnsQueryEntity>,
    today: Long,
    actions: DnsLogActions,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
) {
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar el historial?") },
            text = { Text("Se borran las consultas de los últimos 30 días de este teléfono. No se puede deshacer.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; actions.clear() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
    FgScreen("Consultas DNS", modifier, onBack = onBack, snackbar = snackbar) {
        item {
            FgNote(
                "Solo para ti. Aquí ves qué dominios pidió el teléfono, bloqueados y permitidos, durante los últimos 30 días. Se guarda solo en este teléfono. " +
                    "Tu compañero únicamente recibe, en su informe diario, cuántas consultas se bloquearon y cuántas se permitieron: nunca los dominios.",
            )
        }
        if (days.isEmpty()) {
            item { FgNote("Todavía no hay consultas. Aparecen cuando el filtro DNS está encendido y el teléfono las hace.") }
            return@FgScreen
        }
        item {
            FgGroup(header = "Día") {
                FgCell {
                    ChipChoices(
                        options = days.map { it.day to dayLabel(it.day, today) },
                        selected = selected ?: days.first().day,
                        onSelect = actions.selectDay,
                    )
                }
            }
        }
        val day = days.firstOrNull { it.day == selected } ?: days.first()
        item {
            FgGroup {
                FgRow("Bloqueadas", value = plural(day.blocked, "consulta", "consultas"))
                FgRow("Permitidas", value = plural(day.allowed, "consulta", "consultas"))
            }
        }
        item {
            FgGroup(header = "Ver") {
                FgCell {
                    ChipChoices(
                        options = listOf(true to "Bloqueadas", false to "Permitidas"),
                        selected = showBlocked,
                        onSelect = actions.showBlocked,
                    )
                }
            }
        }
        if (hosts.isEmpty()) {
            item { FgNote(if (showBlocked) "No se bloqueó nada ese día." else "No hubo consultas permitidas ese día.") }
        } else {
            item {
                FgGroup { hosts.forEach { FgRow(it.host, value = plural(it.count, "vez", "veces")) } }
            }
        }
        item { FgGroup { FgActionRow("Borrar el historial de este teléfono", { confirmClear = true }, danger = true) } }
    }
}

private fun dayLabel(day: Long, today: Long) = when (day) {
    today -> "Hoy"
    today - 1 -> "Ayer"
    else -> StreakText.date(LocalDate.ofEpochDay(day))
}
