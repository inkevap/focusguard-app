package com.focusguard.ui.urge

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.focusguard.decision.UrgeOutcome
import com.focusguard.decision.UrgeTrigger
import com.focusguard.decision.Urges
import com.focusguard.ui.components.BreathingPanel
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgRadioGroup
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.profiles.BottomAction
import com.focusguard.ui.rememberTrustedNowMs
import java.time.Duration

private const val TITLE = "Tengo un impulso"

@Composable
fun UrgeScreen(viewModel: UrgeViewModel, onBack: () -> Unit, onJournal: () -> Unit, onReport: () -> Unit) {
    when (val stage = viewModel.stage) {
        UrgeStage.Loading -> FgScreen(TITLE, onBack = onBack) {}
        UrgeStage.Intro -> UrgeIntro(viewModel::start, onReport, onBack = onBack)
        is UrgeStage.Running -> {
            val nowMs = rememberTrustedNowMs(1_000L)
            UrgeRunning(
                remainingMs = Urges.remainingMs(stage.urge.startedMs, nowMs),
                elapsedMs = nowMs - stage.urge.startedMs,
                breathing = { BreathingPanel(startRunning = true) },
                onPassed = { viewModel.finish(UrgeOutcome.PASSED) },
                onGaveIn = { viewModel.finish(UrgeOutcome.GAVE_IN) },
                onBack = onBack,
            )
        }
        is UrgeStage.Finished -> UrgeFinished(stage.outcome, stage.trigger, viewModel::setTrigger, onBack, onJournal, onBack = onBack)
    }
}

@Composable
internal fun UrgeIntro(onStart: () -> Unit, onReport: () -> Unit, modifier: Modifier = Modifier, onBack: () -> Unit = {}) {
    FgScreen(
        TITLE, modifier, onBack = onBack,
        bottomBar = { BottomAction { PrimaryButton("Empezar la cuenta atrás", onStart, Modifier.fillMaxWidth()) } },
    ) {
        item {
            FgHero(
                "Un impulso dura unos 20 minutos",
                body = "Sube, llega a su punto más alto y baja. No tienes que hacer nada con él: solo dejarlo pasar. Aquí tienes una cuenta atrás, un ejercicio de respiración y cosas que hacer mientras tanto.",
            )
        }
        item {
            FgGroup(footer = "Solo se guarda en este teléfono cuándo empieza y cómo acaba, para que veas cuándo suelen llegar.") {
                FgRow("Ver mi informe de impulsos", icon = FgIcons.Chart, onClick = onReport)
            }
        }
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
    onBack: () -> Unit = {},
) {
    val over = remainingMs <= 0
    FgScreen(
        "Aguanta", modifier, onBack = onBack,
        bottomBar = {
            BottomAction {
                FgNote("Si cedes, no pasa nada: se apunta sin juicio y mañana es otro día.")
                PrimaryButton("Se me pasó", onPassed, Modifier.fillMaxWidth())
                SecondaryButton("Cedí al impulso", onGaveIn, Modifier.fillMaxWidth())
            }
        },
    ) {
        item {
            FgHero(
                title = if (over) "El impulso ya debería haber bajado. ¿Cómo vas?" else Urges.messageAt(elapsedMs),
                number = if (over) "Ya pasaron los 20 minutos" else formatRemaining(Duration.ofMillis(remainingMs)),
            )
        }
        item { breathing() }
        item {
            FgGroup(header = "Mientras tanto") {
                Urges.DISTRACTIONS.forEach { activity -> FgRow(activity.title, subtitle = activity.hint) }
            }
        }
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
    onBack: () -> Unit = {},
) {
    FgScreen(
        "¿Qué lo desencadenó?", modifier, onBack = onBack,
        bottomBar = { BottomAction { PrimaryButton("Listo", onDone, Modifier.fillMaxWidth()) } },
    ) {
        item {
            FgHero(
                title = if (outcome == UrgeOutcome.PASSED) "Lo has dejado pasar" else "Se ha apuntado",
                body = if (outcome == UrgeOutcome.PASSED) "Cada impulso que pasa te hace más fuerte. Bien hecho." else "Pasa. Lo importante es seguir: el próximo impulso es otra oportunidad.",
            )
        }
        item {
            FgRadioGroup(
                options = UrgeTrigger.entries.map { Triple<UrgeTrigger?, String, String?>(it, it.label, null) },
                selected = trigger,
                onSelect = { it?.let(onTrigger) },
                header = "¿Qué lo desencadenó? (opcional)",
            )
        }
        item { FgGroup { FgRow("Escribir en el diario", onClick = onJournal) } }
    }
}
