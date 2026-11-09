package com.focusguard.ui.urge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.MiniBars
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.StatusChip
import com.focusguard.ui.components.SwitchRow
import com.focusguard.ui.components.ValueRow
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.plural
import com.focusguard.ui.streak.StreakText

@Composable
fun UrgeReportScreen(viewModel: UrgeReportViewModel, onBack: () -> Unit) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val sharing by viewModel.sharing.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(viewModel.message, viewModel::clearMessage)
    Scaffold(topBar = { FgTopBar("Informe de impulsos", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        report?.let { UrgeReportContent(it, sharing, viewModel::setSharing, Modifier.padding(padding)) }
    }
}

@Composable
internal fun UrgeReportContent(report: UrgeReport, sharing: Boolean, onSharing: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (report.total == 0) {
            item {
                AppCard {
                    SectionTitle("Todavía no hay impulsos", FgIcons.Wind)
                    Hint("Cuando uses «Tengo un impulso» y digas cómo acabó, aquí verás cuándo suelen llegar y qué los desencadena. Solo se guarda en este teléfono.")
                }
            }
        } else {
            item { SummaryCard(report) }
            item { WhenCard(report) }
            if (report.triggers.isNotEmpty()) item { TriggersCard(report) }
            item { WeeksCard(report) }
        }
        item {
            AppCard {
                SectionTitle("Tu compañero", FgIcons.Users)
                SwitchRow(
                    "Enseñarle un resumen semanal",
                    "Cada semana recibe en su informe diario cuántos impulsos tuviste, cuántos pasaron, la hora más frecuente y lo que más los desencadena. Nunca la hora de cada uno ni lo que escribes. Dejar de enseñárselo lo tiene que aprobar él.",
                    sharing,
                    onSharing,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(report: UrgeReport) {
    AppCard {
        SectionTitle("Últimas ${UrgeReports.WEEKS_SHOWN} semanas", FgIcons.Chart)
        Text(plural(report.total, "impulso", "impulsos"), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        report.passRate?.let { rate ->
            Hint("${report.passed} de ${report.total} pasaron (${(rate * 100).toInt()} %). Cada impulso que pasa es uno que ya no te manda.")
        }
        if (report.gaveIn > 0) Hint("${plural(report.gaveIn, "vez cediste", "veces cediste")}. Sin juicio: sirve para entender cuándo pasa.")
    }
}

@Composable
private fun WhenCard(report: UrgeReport) {
    AppCard {
        SectionTitle("Cuándo llegan", FgIcons.Timer)
        val hour = report.peakHour
        val day = report.peakWeekday
        if (hour != null && day != null) {
            Hint("Lo más frecuente es sobre las ${hour.toString().padStart(2, '0')}:00, y el día que más: ${Days.word(day)}.")
        } else {
            Hint("Con unos cuantos impulsos más se verá un patrón.")
        }
        MiniBars(
            values = report.byHour,
            labels = mapOf(0 to "0", 6 to "6", 12 to "12", 18 to "18", 23 to "23"),
            description = "Impulsos por hora del día",
            modifier = Modifier.padding(top = 8.dp),
        )
        MiniBars(
            values = report.byWeekday,
            labels = (0..6).associateWith { Days.letter(java.time.DayOfWeek.of(it + 1)) },
            description = "Impulsos por día de la semana",
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun TriggersCard(report: UrgeReport) {
    AppCard {
        SectionTitle("Qué los desencadena", FgIcons.Flame)
        Hint("Lo que tú dijiste después de cada impulso.")
        val top = report.triggers.first().second.coerceAtLeast(1)
        report.triggers.forEach { (trigger, count) ->
            // La barra es visual: el lector de pantalla lee solo la fila.
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                ValueRow(trigger.label, plural(count, "vez", "veces"))
                FgProgress({ count.toFloat() / top }, Modifier.fillMaxWidth().padding(bottom = 4.dp).clearAndSetSemantics { })
            }
        }
    }
}

@Composable
private fun WeeksCard(report: UrgeReport) {
    AppCard {
        SectionTitle("Semana a semana", FgIcons.Leaf)
        val max = report.weeks.maxOf { it.total }.coerceAtLeast(1)
        report.weeks.reversed().forEach { week ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                ValueRow("Semana del ${StreakText.date(week.start)}", if (week.total == 0) "ninguno" else "${week.total} (${if (week.passed == 1) "1 pasó" else "${week.passed} pasaron"})")
                FgProgress({ week.total.toFloat() / max }, Modifier.fillMaxWidth().padding(bottom = 4.dp).clearAndSetSemantics { })
            }
        }
        // La semana en curso no se compara: aún no ha terminado y siempre parecería «menos».
        report.weeks.dropLast(1).takeLast(2).takeIf { weeks -> weeks.sumOf { it.total } > 0 }?.let { StatusChip(trendText(it)) }
    }
}

/** Cómo fue la última semana completa respecto a la anterior (las dos terminadas). */
private fun trendText(lastTwo: List<UrgeWeek>): String = when {
    lastTwo[1].total < lastTwo[0].total -> "La última semana completa, menos que la anterior"
    lastTwo[1].total > lastTwo[0].total -> "La última semana completa, más que la anterior"
    else -> "La última semana completa, igual que la anterior"
}
