package com.focusguard.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.Pomodoro
import com.focusguard.decision.PomodoroPhase
import com.focusguard.decision.ProfileActivation
import com.focusguard.decision.Streaks
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.FgTile
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.icons.FgIcons
import com.focusguard.ui.plural
import com.focusguard.ui.profiles.ProfilesViewModel
import com.focusguard.ui.profiles.endSummary
import com.focusguard.ui.rememberTrustedNowMs
import com.focusguard.ui.sleep.SleepViewModel
import com.focusguard.ui.streak.StreakViewModel
import java.time.Duration
import java.time.Instant

/** Todo lo que Inicio necesita para dibujarse; la pantalla real lo saca de los ViewModels. */
data class HomeUiState(
    val profiles: List<Profile>,
    val sessions: List<Profile>,
    val nowMs: Long,
    val editLocked: Boolean,
    val activeNow: Int,
    val missingPermissions: Int,
    /** Días seguidos de la racha de hoy. */
    val streak: Int = 0,
)

/** Inicio: el estado de ahora y las cuatro cosas que se hacen con prisa. Los perfiles viven en su pestaña. */
@Composable
fun HomeScreen(
    viewModel: ProfilesViewModel,
    streakViewModel: StreakViewModel,
    missingPermissions: Int,
    onOpenPermissions: () -> Unit,
    onOpenProfiles: () -> Unit,
    onNewSession: (pomodoro: Boolean) -> Unit,
    onPanic: () -> Unit,
    onSleep: () -> Unit,
    onStreak: () -> Unit,
    onUrge: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    // Solo hace falta repintar cada segundo si hay una cuenta atrás a la vista.
    val nowMs = rememberTrustedNowMs(if (sessions.isEmpty()) 5_000L else 1_000L)
    val locked = remember(nowMs) { viewModel.isEditLocked(nowMs) }
    val activeNow = remember(profiles, sessions, nowMs) { viewModel.activeCount(profiles, sessions, nowMs) }
    val records by streakViewModel.records.collectAsStateWithLifecycle()
    val streakConfig by streakViewModel.config.collectAsStateWithLifecycle()
    // El día solo cambia a medianoche: la racha no se recalcula con cada segundo de la cuenta atrás.
    val today = remember(nowMs / 60_000) { streakViewModel.today() }
    val streak = remember(records, streakConfig, today) { Streaks.current(records, today, streakConfig) }

    HomeContent(
        state = HomeUiState(profiles, sessions, nowMs, locked, activeNow, missingPermissions, streak),
        snackbar = rememberMessageSnackbar(message, viewModel::clearMessage),
        onOpenPermissions = onOpenPermissions,
        onOpenProfiles = onOpenProfiles,
        onNewSession = onNewSession,
        onPanic = onPanic,
        onSleep = onSleep,
        onStreak = onStreak,
        onUrge = onUrge,
        onEndSession = viewModel::endSession,
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    snackbar: SnackbarHostState,
    onOpenPermissions: () -> Unit,
    onOpenProfiles: () -> Unit,
    onNewSession: (pomodoro: Boolean) -> Unit,
    onPanic: () -> Unit,
    onSleep: () -> Unit,
    onStreak: () -> Unit,
    onUrge: () -> Unit,
    onEndSession: (Long) -> Unit,
) {
    val now = Instant.ofEpochMilli(state.nowMs)
    FgScreen("Inicio", large = true, snackbar = snackbar) {
        item { StatusHero(state, onOpenPermissions, onOpenProfiles) }
        item {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FgTile("Bloquear ya", "Elige qué y cuánto", FgIcons.Block, { onNewSession(false) }, Modifier.weight(1f).fillMaxHeight())
                FgTile("Enfoque", "Foco con descansos", FgIcons.Target, { onNewSession(true) }, Modifier.weight(1f).fillMaxHeight(), iconColor = MaterialTheme.colorScheme.secondary)
            }
        }
        item {
            Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FgTile("Dormir", "Hasta que despiertes", FgIcons.Timer, onSleep, Modifier.weight(1f).fillMaxHeight(), iconColor = MaterialTheme.colorScheme.outline)
                FgTile("Tengo un impulso", "Cuenta atrás y respirar", FgIcons.Wind, onUrge, Modifier.weight(1f).fillMaxHeight(), iconColor = MaterialTheme.colorScheme.tertiary)
            }
        }
        if (state.sessions.isNotEmpty()) {
            item {
                FgGroup(header = "En curso") {
                    state.sessions.forEach { session ->
                        // Ayúdame en curso abre su pantalla (cuenta atrás y respiración); Dormir, la suya.
                        val open = if (session.kind == ProfileKind.PANIC) { if (session.title == SleepViewModel.TITLE) onSleep else onPanic } else null
                        FgRow(
                            session.title,
                            subtitle = listOfNotNull(sessionStatus(session, now).ifEmpty { null }, endSummary(session, now)).joinToString(" · ").ifEmpty { null },
                            onClick = open,
                            trailing = if (session.canEndEarly) {
                                { TextButton(onClick = { onEndSession(session.id) }, enabled = !state.editLocked && session.canEndNow(now)) { Text("Terminar") } }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        item {
            FgGroup {
                FgRow(
                    "Tu racha", icon = FgIcons.Flame, iconColor = MaterialTheme.colorScheme.tertiary,
                    value = plural(state.streak, "día", "días"), onClick = onStreak,
                )
                // Con Ayúdame ya en curso, la fila sobra: se ve arriba, en «En curso».
                if (!ProfileActivation.isPanicActive(state.sessions, now)) {
                    FgRow(
                        "Ayúdame", subtitle = "Bloquea todo ahora. No se puede deshacer.",
                        icon = FgIcons.Lifebuoy, iconColor = MaterialTheme.colorScheme.error, onClick = onPanic,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusHero(state: HomeUiState, onOpenPermissions: () -> Unit, onOpenProfiles: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val strict = if (state.editLocked) "Modo estricto activo" else null
    when {
        state.missingPermissions > 0 -> FgHero(
            if (state.missingPermissions == 1) "Falta 1 permiso" else "Faltan ${state.missingPermissions} permisos",
            body = "Sin ellos FocusGuard no puede bloquear nada.", pill = "Dar permisos",
            container = scheme.tertiaryContainer, content = scheme.onTertiaryContainer, onClick = onOpenPermissions,
        )
        state.activeNow > 0 -> FgHero(
            "Protección activa", body = "${plural(state.activeNow, "perfil", "perfiles")} bloqueando ahora",
            pill = strict, accent = true, onClick = onOpenProfiles,
        )
        else -> FgHero(
            "Nada bloqueado ahora",
            body = if (state.profiles.isEmpty() && state.sessions.isEmpty()) "Crea un perfil para empezar." else "Tus perfiles se activan según su horario.",
            pill = strict, onClick = onOpenProfiles,
        )
    }
}

private fun sessionStatus(session: Profile, now: Instant): String {
    session.activeUntil?.let { return "Quedan ${formatRemaining(Duration.between(now, it))}" }
    val plan = session.pomodoro ?: return ""
    return when (val phase = Pomodoro.phaseAt(plan, now)) {
        is PomodoroPhase.Focus -> "Foco ${phase.session} de ${plan.sessions}: ${formatRemaining(Duration.between(now, phase.endsAt))}"
        is PomodoroPhase.Break -> "Descanso ${phase.session} de ${plan.sessions}: ${formatRemaining(Duration.between(now, phase.endsAt))}"
        PomodoroPhase.NotStarted -> "Por empezar"
        PomodoroPhase.Finished -> "Terminado"
    }
}
