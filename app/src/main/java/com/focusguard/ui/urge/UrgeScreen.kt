package com.focusguard.ui.urge

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameMillis
import com.focusguard.decision.Breathing
import com.focusguard.decision.UrgeOutcome
import com.focusguard.decision.UrgeTrigger
import com.focusguard.decision.Urges
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.BreathingPanel
import com.focusguard.ui.components.BrandHero
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.rememberTrustedNowMs
import java.time.Duration

@Composable
fun UrgeScreen(viewModel: UrgeViewModel, onBack: () -> Unit, onJournal: () -> Unit, onReport: () -> Unit) {
    Scaffold(
        topBar = {
            FgTopBar("Tengo un impulso", onBack = onBack)
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (val stage = viewModel.stage) {
            UrgeStage.Loading -> Box(modifier.fillMaxSize())
            UrgeStage.Intro -> UrgeIntro(viewModel::start, onReport, modifier)
            is UrgeStage.Running -> {
                val nowMs = rememberTrustedNowMs(1_000L)
                UrgeRunning(
                    remainingMs = Urges.remainingMs(stage.urge.startedMs, nowMs),
                    elapsedMs = nowMs - stage.urge.startedMs,
                    breathing = { BreathingPanel(startRunning = true) },
                    onPassed = { viewModel.finish(UrgeOutcome.PASSED) },
                    onGaveIn = { viewModel.finish(UrgeOutcome.GAVE_IN) },
                    modifier = modifier,
                )
            }
            is UrgeStage.Finished -> UrgeFinished(stage.outcome, stage.trigger, viewModel::setTrigger, onBack, onJournal, modifier)
        }
    }
}

/** Milisegundos desde que se abrió el ejercicio, al ritmo de la pantalla. Se lee solo al dibujar el círculo: no recompone la pantalla en cada fotograma. */
@Composable
internal fun UrgeIntro(onStart: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BrandHero(
                "Un impulso dura unos 20 minutos",
                "Sube, llega a su punto más alto y baja. No tienes que hacer nada con él: solo dejarlo pasar. Aquí tienes una cuenta atrás, un ejercicio de respiración y cosas que hacer mientras tanto.",
                icon = FgIcons.Wind,
            )
        }
        item { PrimaryButton("Empezar la cuenta atrás", onStart, Modifier.fillMaxWidth()) }
        item { SecondaryButton("Ver mi informe de impulsos", onReport, Modifier.fillMaxWidth()) }
        item { Hint("Solo se guarda en este teléfono cuándo empieza y cómo acaba, para que veas cuándo suelen llegar.") }
    }
}

@Composable
internal fun UrgeRunning(
    remainingMs: Long,
    elapsedMs: Long,
    /** El ejercicio de respiración (en Impulso arranca solo); un hueco para poder pintar la pantalla sin el reloj ni los ajustes. */
    breathing: @Composable () -> Unit,
    onPassed: () -> Unit,
    onGaveIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val over = remainingMs <= 0
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AppCard {
                Text(
                    if (over) "Ya pasaron los 20 minutos" else formatRemaining(Duration.ofMillis(remainingMs)),
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    if (over) "El impulso ya debería haber bajado. ¿Cómo vas?" else Urges.messageAt(elapsedMs),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { breathing() }
        item { SectionTitle("Mientras tanto", FgIcons.Leaf) }
        items(Urges.DISTRACTIONS, key = { it.title }) { activity ->
            AppCard {
                Text(activity.title, style = MaterialTheme.typography.titleMedium)
                Hint(activity.hint)
            }
        }
        item { PrimaryButton("Se me pasó", onPassed, Modifier.fillMaxWidth()) }
        item { SecondaryButton("Cedí al impulso", onGaveIn, Modifier.fillMaxWidth()) }
        item { Hint("Si cedes, no pasa nada: se apunta sin juicio y mañana es otro día.") }
    }
}

@Composable
internal fun UrgeFinished(
    outcome: UrgeOutcome,
    trigger: UrgeTrigger?,
    onTrigger: (UrgeTrigger) -> Unit,
    onDone: () -> Unit,
    onJournal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            AppCard {
                SectionTitle(if (outcome == UrgeOutcome.PASSED) "Lo has dejado pasar" else "Se ha apuntado")
                Text(
                    if (outcome == UrgeOutcome.PASSED) "Cada impulso que pasa te hace más fuerte. Bien hecho." else "Pasa. Lo importante es seguir: el próximo impulso es otra oportunidad.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item {
            AppCard {
                SectionTitle("¿Qué lo desencadenó? (opcional)")
                ChipChoices<UrgeTrigger?>(
                    options = UrgeTrigger.entries.map { it to it.label },
                    selected = trigger,
                    onSelect = { it?.let(onTrigger) },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item { SecondaryButton("Escribir en el diario", onJournal, Modifier.fillMaxWidth()) }
        item { PrimaryButton("Listo", onDone, Modifier.fillMaxWidth()) }
    }
}
