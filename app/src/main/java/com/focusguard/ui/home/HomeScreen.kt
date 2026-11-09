package com.focusguard.ui.home

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import com.focusguard.ui.components.ActionRow
import com.focusguard.ui.components.ActionTile
import com.focusguard.ui.components.BadgeRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.Pomodoro
import com.focusguard.decision.ProfileActivation
import com.focusguard.decision.PomodoroPhase
import com.focusguard.decision.Streaks
import com.focusguard.model.Profile
import com.focusguard.model.ProfileKind
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.EmptyState
import com.focusguard.ui.components.LargeSwitch
import com.focusguard.ui.components.MissingPermissionsCard
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.components.ScreenHeader
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionLabel
import com.focusguard.ui.components.StatusCard
import com.focusguard.ui.components.StatusKind
import com.focusguard.ui.profiles.ProfilesViewModel
import com.focusguard.ui.profiles.conditionsSummary
import com.focusguard.ui.profiles.contentSummary
import com.focusguard.ui.profiles.limitSummary
import com.focusguard.ui.profiles.scheduleSummary
import com.focusguard.ui.formatRemaining
import com.focusguard.ui.plural
import com.focusguard.ui.streak.StreakText
import com.focusguard.ui.sleep.SleepViewModel
import com.focusguard.ui.streak.StreakViewModel
import com.focusguard.ui.rememberTrustedNowMs
import com.focusguard.ui.profiles.endSummary
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

@Composable
fun HomeScreen(
    viewModel: ProfilesViewModel,
    streakViewModel: StreakViewModel,
    missingPermissions: Int,
    onOpenPermissions: () -> Unit,
    onEdit: (Long) -> Unit,
    onPreview: (Long) -> Unit,
    onNewSession: (pomodoro: Boolean) -> Unit,
    onPanic: () -> Unit,
    onSleep: () -> Unit,
    onStreak: () -> Unit,
    onUrge: () -> Unit,
    onJournal: () -> Unit,
    onRecovery: () -> Unit,
    onAssessment: () -> Unit,
    onTemplates: () -> Unit = {},
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
        // Con el modo estricto activo no se edita, pero sí se puede ver cómo está configurado cada perfil.
        onEdit = { id -> if (locked && id != 0L) onPreview(id) else onEdit(id) },
        onNewSession = onNewSession,
        onPanic = onPanic,
        onSleep = onSleep,
        onStreak = onStreak,
        onUrge = onUrge,
        onJournal = onJournal,
        onRecovery = onRecovery,
        onAssessment = onAssessment,
        onTemplates = onTemplates,
        onToggle = viewModel::setEnabled,
        onEndSession = viewModel::endSession,
    )
}

@Composable
internal fun HomeContent(
    state: HomeUiState,
    snackbar: SnackbarHostState,
    onOpenPermissions: () -> Unit,
    onEdit: (Long) -> Unit,
    onNewSession: (pomodoro: Boolean) -> Unit,
    onPanic: () -> Unit,
    onSleep: () -> Unit,
    onStreak: () -> Unit,
    onUrge: () -> Unit,
    onJournal: () -> Unit,
    onRecovery: () -> Unit,
    onAssessment: () -> Unit,
    onToggle: (Long, Boolean) -> Unit,
    onEndSession: (Long) -> Unit,
    onTemplates: () -> Unit = {},
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0L) },
                icon = { Icon(FgIcons.Add, contentDescription = null) },
                text = { Text("Nuevo perfil") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            // Espacio final para que el botón flotante no tape la última tarjeta.
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader("Inicio", "Lo que tienes bloqueado ahora mismo", icon = FgIcons.Home) }
            item { StatusBanner(state.missingPermissions, state.activeNow, state.profiles.isEmpty() && state.sessions.isEmpty(), onOpenPermissions) }
            // Ayúdame va arriba: es lo que se busca con prisa, en un impulso, y no debe obligar a bajar. En curso se ve en su tarjeta de sesión.
            if (!ProfileActivation.isPanicActive(state.sessions, Instant.ofEpochMilli(state.nowMs))) {
                item { PanicCard(onPanic) }
            }
            item {
                Row(Modifier.height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionTile("Bloquear ya", "Elige qué y cuánto tiempo", FgIcons.Block, { onNewSession(false) }, Modifier.weight(1f).fillMaxHeight())
                    ActionTile("Enfoque", "Foco y descansos con temporizador", FgIcons.Target, { onNewSession(true) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
            item { StreakCard(state.streak, onStreak) }
            item {
                AppCard(contentPadding = 0.dp) {
                    ActionRow("Tengo un impulso", "Cuenta atrás, respiración y cosas que hacer hasta que pase.", FgIcons.Wind, onUrge)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ActionRow("Dormir", "Bloquea el teléfono hasta que despiertes: solo la alarma y lo que elijas. No se puede deshacer.", FgIcons.Timer, onSleep)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ActionRow("Diario", "Qué podrías hacer mejor y qué vas a hacer en vez de usar el teléfono.", FgIcons.Book, onJournal)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ActionRow("Programa de recuperación", "Siete pasos, uno por día: qué te lleva a abrir el teléfono y qué haces entonces.", FgIcons.Leaf, onRecovery)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ActionRow("Test de adicción", "Diez preguntas para ver cómo vas y compararte contigo mismo con el tiempo.", FgIcons.Chart, onAssessment)
                }
            }
            items(state.sessions, key = { "s${it.id}" }) { session ->
                SessionCard(
                    session, state.nowMs, state.editLocked,
                    // Ayúdame en curso abre su pantalla (cuenta atrás y respiración); Dormir, la suya.
                    onOpen = if (session.kind == ProfileKind.PANIC) { if (session.title == SleepViewModel.TITLE) onSleep else onPanic } else null,
                ) { onEndSession(session.id) }
            }
            item {
                AppCard(contentPadding = 0.dp) {
                    ActionRow("Plantillas de perfil", "Redes, vídeo, juegos, descanso nocturno… listos para crear con un toque.", FgIcons.Apps, onTemplates)
                }
            }
            item { SectionLabel("Tus perfiles") }
            if (state.profiles.isEmpty()) {
                item {
                    EmptyState(
                        title = "Aún no tienes perfiles",
                        body = "Un perfil dice qué apps y webs bloquear y cuándo. Crea el primero en menos de un minuto.",
                        actionLabel = "Crear mi primer perfil",
                        onAction = { onEdit(0L) },
                    )
                }
            }
            items(state.profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    locked = state.editLocked,
                    onToggle = { onToggle(profile.id, it) },
                    onEdit = { onEdit(profile.id) },
                )
            }
        }
    }
}

@Composable
private fun StatusBanner(missingPermissions: Int, activeNow: Int, noProfiles: Boolean, onOpenPermissions: () -> Unit) {
    when {
        missingPermissions > 0 -> MissingPermissionsCard(missingPermissions, onOpenPermissions)
        activeNow > 0 -> StatusCard(
            title = "Protección activa",
            body = "${plural(activeNow, "perfil", "perfiles")} bloqueando ahora",
            icon = FgIcons.CheckCircle,
            kind = StatusKind.Active,
        )
        else -> StatusCard(
            title = "Nada bloqueado ahora",
            body = if (noProfiles) "Crea un perfil para empezar." else "Tus perfiles se activan según su horario.",
            icon = FgIcons.Info,
            kind = StatusKind.Neutral,
        )
    }
}

@Composable
private fun StreakCard(days: Int, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // La racha lleva el color de la brasa (ámbar): se ve de lejos y no compite con la protección.
    AppCard(onClick = onClick, containerColor = scheme.tertiaryContainer) {
        BadgeRow(
            FgIcons.Flame, "Tu racha",
            if (days == 0) StreakText.achieveRule() else "${plural(days, "día seguido", "días seguidos")}. Toca para ver el calendario.",
            scheme.onTertiaryContainer, badgeSize = 48.dp, badgeContainer = scheme.tertiary, badgeContent = scheme.onTertiary,
            trailing = { Text(days.toString(), style = MaterialTheme.typography.displaySmall, color = scheme.onTertiaryContainer) },
        )
    }
}

@Composable
private fun PanicCard(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AppCard(onClick = onClick, containerColor = scheme.errorContainer) {
        BadgeRow(
            FgIcons.Lifebuoy, "Ayúdame", "Bloquea todo ahora durante el tiempo que elijas. No se puede deshacer.", scheme.onErrorContainer,
            badgeContainer = scheme.error, badgeContent = scheme.onError,
        )
    }
}

@Composable
private fun ProfileCard(profile: Profile, locked: Boolean, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    AppCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                // Apagado se distingue por el color del título, sin bajar el contraste de los detalles.
                Text(
                    "${profile.emoji} ${profile.title}".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (profile.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Detail(contentSummary(profile))
                val conditions = conditionsSummary(profile)
                // "Siempre activo" engañaría si el perfil depende de una red, del cargador o de un lugar.
                if (profile.intervals.isNotEmpty() || conditions == null) Detail(scheduleSummary(profile.intervals))
                conditions?.let { Detail(it) }
                limitSummary(profile.limit)?.let { Detail(it) }
            }
            LargeSwitch(
                checked = profile.enabled,
                onCheckedChange = onToggle,
                enabled = !(locked && profile.enabled),
                modifier = Modifier.semantics { contentDescription = "Activar ${profile.title}" },
            )
        }
        if (locked) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(FgIcons.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Detail("Bloqueado por el modo estricto")
            }
        }
    }
}

@Composable
private fun Detail(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SessionCard(session: Profile, nowMs: Long, locked: Boolean, onOpen: (() -> Unit)? = null, onEnd: () -> Unit) {
    val now = Instant.ofEpochMilli(nowMs)
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleMedium)
                Detail(sessionStatus(session, now))
                endSummary(session, now)?.let { Detail(it) }
            }
            if (session.canEndEarly) SecondaryButton("Terminar", onEnd, enabled = !locked && session.canEndNow(now))
        }
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
