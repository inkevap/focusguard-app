package com.focusguard.ui.protection

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.focusguard.decision.Fix
import com.focusguard.decision.Problem
import com.focusguard.decision.Severity
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen

/** Solucionador de problemas (O2): lo que falta, de lo imprescindible a lo recomendable, con un botón que lleva a arreglarlo. */
@Composable
fun TroubleshooterScreen(viewModel: TroubleshooterViewModel, onFix: (Fix) -> Unit, onBack: () -> Unit) {
    // Se vuelve a comprobar al regresar de los ajustes del sistema.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    TroubleshooterContent(viewModel.problems, onFix, onBack)
}

@Composable
internal fun TroubleshooterContent(problems: List<Problem>?, onFix: (Fix) -> Unit, onBack: () -> Unit) {
    FgScreen("¿Algo no bloquea?", onBack = onBack) {
        when {
            problems == null -> item { FgNote("Comprobando…") }
            problems.isEmpty() -> item {
                FgHero(
                    "Todo en orden",
                    body = "FocusGuard tiene todo lo que necesita para bloquear. Si algo sigue sin cerrarse, anota qué app es y cuándo pasa.",
                    accent = true,
                )
            }
            else -> {
                item { FgNote("Arregla de arriba abajo: lo primero es lo que impide que FocusGuard bloquee.") }
                val required = problems.filter { it.severity == Severity.REQUIRED }
                val recommended = problems.filter { it.severity != Severity.REQUIRED }
                if (required.isNotEmpty()) item(key = "required") { ProblemGroup("Imprescindible", required, onFix) }
                if (recommended.isNotEmpty()) item(key = "recommended") { ProblemGroup("Recomendado", recommended, onFix) }
            }
        }
    }
}

@Composable
private fun ProblemGroup(header: String, problems: List<Problem>, onFix: (Fix) -> Unit) {
    FgGroup(header = header) {
        problems.forEach { problem ->
            val texts = problem.fix.texts()
            FgRow(
                texts.title, subtitle = texts.reason,
                trailing = { TextButton(onClick = { onFix(problem.fix) }) { Text(texts.action) } },
            )
        }
    }
}
