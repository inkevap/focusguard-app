package com.focusguard.ui.protection

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.focusguard.decision.Fix
import com.focusguard.decision.Problem
import com.focusguard.decision.Severity
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionLabel
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.StatusChip

/** Solucionador de problemas (O2): lo que falta, de lo imprescindible a lo recomendable, con un botón que lleva a arreglarlo. */
@Composable
fun TroubleshooterScreen(viewModel: TroubleshooterViewModel, onFix: (Fix) -> Unit, onBack: () -> Unit) {
    // Se vuelve a comprobar al regresar de los ajustes del sistema.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    TroubleshooterContent(viewModel.problems, onFix, onBack)
}

@Composable
internal fun TroubleshooterContent(problems: List<Problem>?, onFix: (Fix) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            FgTopBar("Solucionador de problemas", onBack = onBack)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                problems == null -> item { Hint("Comprobando…") }
                problems.isEmpty() -> item { AllGood() }
                else -> {
                    item { Hint("Arregla de arriba abajo: lo primero es lo que impide que FocusGuard bloquee.") }
                    items(problems, key = { it.fix.name }) { ProblemCard(it, onFix) }
                }
            }
        }
    }
}

@Composable
private fun AllGood() {
    AppCard {
        Icon(FgIcons.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        SectionTitle("Todo en orden")
        Hint("FocusGuard tiene todo lo que necesita para bloquear. Si algo sigue sin cerrarse, anota qué app es y cuándo pasa.")
    }
}

@Composable
private fun ProblemCard(problem: Problem, onFix: (Fix) -> Unit) {
    val texts = problem.fix.texts()
    AppCard {
        StatusChip(if (problem.severity == Severity.REQUIRED) "Imprescindible" else "Recomendado")
        SectionTitle(texts.title)
        Hint(texts.reason)
        SecondaryButton(texts.action, { onFix(problem.fix) }, Modifier.padding(top = 12.dp))
    }
}
