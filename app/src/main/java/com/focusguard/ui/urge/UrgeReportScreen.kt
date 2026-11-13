package com.focusguard.ui.urge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.UrgeReport
import com.focusguard.decision.UrgeReports
import com.focusguard.decision.UrgeWeek
import com.focusguard.ui.Days
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgSwitchRow
import com.focusguard.ui.components.MiniBars
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.plural
import com.focusguard.ui.streak.StreakText

@Composable
fun UrgeReportScreen(viewModel: UrgeReportViewModel, onBack: () -> Unit) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val sharing by viewModel.sharing.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    val current = report
    if (current == null) {
        FgScreen("Informe de impulsos", onBack = onBack, snackbar = snackbar) {}
    } else {
        UrgeReportContent(current, sharing, viewModel::setSharing, onBack = onBack, snackbar = snackbar)
    }
}

@Composable
internal fun UrgeReportContent(
    report: UrgeReport,
    sharing: Boolean,
    onSharing: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    snackbar: androidx.compose.material3.SnackbarHostState? = null,
) {
    FgScreen("Informe de impulsos", modifier, onBack = onBack, snackbar = snackbar) {
        if (report.total == 0) {
            item {
                FgHero(
                    "Todavía no hay impulsos",
                    body = "Cuando uses «Tengo un impulso» y digas cómo acabó, aquí verás cuándo suelen llegar y qué los desencadena. Solo se guarda en este teléfono.",
                )
            }
        } else {
            item { SummaryHero(report) }
            item { WhenGroup(report) }
            if (report.triggers.isNotEmpty()) item { TriggersGroup(report) }
            item { WeeksGroup(report) }
        }
        item {
            FgGroup(
                header = "Tu compañero",
                footer = "Cada semana recibe en su informe diario cuántos impulsos tuviste, cuántos pasaron, la hora más frecuente y lo que más los desencadena. Nunca la hora de cada uno ni lo que escribes. Dejar de enseñárselo lo tiene que aprobar él.",
            ) {
                FgSwitchRow("Enseñarle un resumen semanal", sharing, onSharing)
            }
        }
    }
}

@Composable
private fun SummaryHero(report: UrgeReport) {
    val notes = buildList {
        report.passRate?.let { rate -> add("${report.passed} de ${report.total} pasaron (${(rate * 100).toInt()} %). Cada impulso que pasa es uno que ya no te manda.") }
        if (report.gaveIn > 0) add("${plural(report.gaveIn, "vez cediste", "veces cediste")}. Sin juicio: sirve para entender cuándo pasa.")
    }
    FgHero(
        title = "Últimas ${UrgeReports.WEEKS_SHOWN} semanas",
        number = plural(report.total, "impulso", "impulsos"),
        body = notes.joinToString(" ").ifEmpty { null },
    )
}

@Composable
private fun WhenGroup(report: UrgeReport) {
    val hour = report.peakHour
    val day = report.peakWeekday
    FgGroup(
        header = "Cuándo llegan",
        footer = if (hour != null && day != null) {
            "Lo más frecuente es sobre las ${hour.toString().padStart(2, '0')}:00, y el día que más: ${Days.word(day)}."
        } else {
            "Con unos cuantos impulsos más se verá un patrón."
        },
    ) {
        FgCell {
            MiniBars(
                values = report.byHour,
                labels = mapOf(0 to "0", 6 to "6", 12 to "12", 18 to "18", 23 to "23"),
                description = "Impulsos por hora del día",
            )
            MiniBars(
                values = report.byWeekday,
                labels = (0..6).associateWith { Days.letter(java.time.DayOfWeek.of(it + 1)) },
                description = "Impulsos por día de la semana",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun TriggersGroup(report: UrgeReport) {
    FgGroup(header = "Qué los desencadena", footer = "Lo que tú dijiste después de cada impulso.") {
        val top = report.triggers.first().second.coerceAtLeast(1)
        report.triggers.forEach { (trigger, count) ->
            // La barra es visual: el lector de pantalla lee solo la fila.
            FgCell(Modifier.semantics(mergeDescendants = true) {}) {
                RowWithBar(trigger.label, plural(count, "vez", "veces"), count.toFloat() / top)
            }
        }
    }
}

@Composable
private fun WeeksGroup(report: UrgeReport) {
    // La semana en curso no se compara: aún no ha terminado y siempre parecería «menos».
    val trend = report.weeks.dropLast(1).takeLast(2).takeIf { weeks -> weeks.sumOf { it.total } > 0 }?.let { trendText(it) }
    FgGroup(header = "Semana a semana", footer = trend) {
        val max = report.weeks.maxOf { it.total }.coerceAtLeast(1)
        report.weeks.reversed().forEach { week ->
            FgCell(Modifier.semantics(mergeDescendants = true) {}) {
                RowWithBar(
                    "Semana del ${StreakText.date(week.start)}",
                    if (week.total == 0) "ninguno" else "${week.total} (${if (week.passed == 1) "1 pasó" else "${week.passed} pasaron"})",
                    week.total.toFloat() / max,
                )
            }
        }
    }
}

/** Título y valor en una línea y la barra debajo. */
@Composable
private fun RowWithBar(title: String, value: String, progress: Float) {
    Column {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FgProgress({ progress }, Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics { })
    }
}

/** Cómo fue la última semana completa respecto a la anterior (las dos terminadas). */
private fun trendText(lastTwo: List<UrgeWeek>): String = when {
    lastTwo[1].total < lastTwo[0].total -> "La última semana completa, menos que la anterior"
    lastTwo[1].total > lastTwo[0].total -> "La última semana completa, más que la anterior"
    else -> "La última semana completa, igual que la anterior"
}
