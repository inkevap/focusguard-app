package com.focusguard.ui.profiles

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.appLabel
import com.focusguard.decision.LimitProgress
import com.focusguard.decision.ProfileStatus
import com.focusguard.model.BlockMode
import com.focusguard.model.LimitKind
import com.focusguard.model.Profile
import com.focusguard.model.ProfileUsage
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgTopBar
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.AppRow
import com.focusguard.ui.components.rememberAppLabels
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.components.StatusChip
import com.focusguard.ui.components.rememberMessageSnackbar
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.formatLocal
import com.focusguard.ui.rememberTrustedNowMs

/** Lo que enseña la vista previa de un perfil (E11). */
internal data class ProfilePreviewState(
    val profile: Profile,
    val status: ProfileStatus,
    val limit: LimitProgress?,
    /** Con el modo estricto activo no se puede editar: se ve el perfil, no se abre el editor. */
    val editLocked: Boolean = false,
    val pauses: List<PauseRecord> = emptyList(),
)

@Composable
fun ProfilePreviewScreen(viewModel: ProfilePreviewViewModel, profileId: Long, onEdit: () -> Unit, onBack: () -> Unit) {
    val profile by remember(profileId) { viewModel.profile(profileId) }.collectAsStateWithLifecycle(null)
    val nowMs = rememberTrustedNowMs(30_000L)
    val hasLimit = profile?.limit != null
    // Sin el uso cargado no se pinta el límite: enseñaría cero un instante aunque esté agotado.
    val usage by produceState<Map<Long, ProfileUsage>?>(null, nowMs, hasLimit) { value = if (hasLimit) viewModel.usage() else emptyMap() }
    val context = LocalContext.current
    val pauseHistory by remember(profileId) { viewModel.pauseHistory(profileId) }.collectAsStateWithLifecycle(emptyList())
    val message by viewModel.message.collectAsStateWithLifecycle()
    val state = remember(profile, usage, nowMs, pauseHistory) {
        val loaded = usage
        if (loaded == null) null else profile?.let { p -> viewModel.state(p, loaded[p.id], nowMs).copy(pauses = pauseHistory) }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(rememberMessageSnackbar(message, viewModel::clearMessage)) },
        topBar = {
            FgTopBar("Vista previa", onBack = onBack)
        },
    ) { padding ->
        if (state == null) {
            // Mientras carga no hay nada que decir; solo si el perfil no existe se avisa.
            if (profile == null && usage != null) Text("Este perfil ya no existe.", Modifier.padding(padding).padding(16.dp))
        }
        else ProfilePreviewContent(state, onEdit, { viewModel.pause(profileId, it) }, { viewModel.resume(profileId) }, Modifier.padding(padding))
    }
}

@Composable
internal fun ProfilePreviewContent(
    state: ProfilePreviewState,
    onEdit: () -> Unit,
    onPause: (Int) -> Unit = {},
    onResume: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val profile = state.profile
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            AppCard {
                Text("${profile.emoji} ${profile.title}".trim(), style = MaterialTheme.typography.headlineSmall)
                StatusChip(statusLabel(state.status), Modifier.padding(top = 8.dp))
                Hint(statusDetail(state.status))
            }
        }
        item {
            AppCard {
                SectionTitle("Cómo funciona")
                Hint(
                    buildList {
                        add(scheduleSummary(profile.intervals))
                        conditionsSummary(profile)?.let(::add)
                        limitSummary(profile.limit)?.let(::add)
                    }.joinToString("\n"),
                )
            }
        }
        item {
            AppCard {
                SectionTitle(if (profile.mode == BlockMode.BLOCK) "Qué bloquea" else "Qué permite")
                Hint(contentSummary(profile))
                val appLabels = rememberAppLabels(profile.apps.take(MAX_NAMES))
                appLabels.forEach { (pkg, label) -> AppRow(pkg, label) }
                if (profile.apps.size > MAX_NAMES) Text("y ${profile.apps.size - MAX_NAMES} más", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (profile.webs.isNotEmpty()) Text(profile.webs.take(MAX_NAMES).joinToString(", ") { it.value }, Modifier.padding(top = 4.dp))
            }
        }
        state.limit?.let { limit -> item { LimitCard(limit) } }
        item { PauseCard(state.status, state.pauses, onPause, onResume, enabled = !state.editLocked) }
        item {
            SecondaryButton("Editar perfil", onEdit, Modifier.fillMaxWidth(), enabled = !state.editLocked)
            if (state.editLocked) Hint("El modo estricto está activo: no se puede editar ahora.")
        }
    }
}

@Composable
private fun LimitCard(limit: LimitProgress) {
    AppCard {
        SectionTitle("Límite de uso")
        val unit = if (limit.kind == LimitKind.OPENS) "aperturas" else "min"
        Hint(
            if (limit.exhausted) "Agotado: ${limit.used} de ${limit.allowed} $unit." else "Llevas ${limit.used} de ${limit.allowed} $unit.",
        )
        FgProgress(
            progress = { (limit.used.toFloat() / limit.allowed).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun statusLabel(status: ProfileStatus) = when (status) {
    ProfileStatus.Off -> "Apagado"
    is ProfileStatus.Paused -> "En pausa"
    ProfileStatus.Always -> "Activo siempre"
    is ProfileStatus.ActiveNow -> "Activo ahora"
    is ProfileStatus.StartsIn -> "Empieza pronto"
    ProfileStatus.WaitingConditions -> "Esperando condiciones"
    ProfileStatus.ActiveByConditions -> "Activo ahora"
}

private fun statusDetail(status: ProfileStatus) = when (status) {
    ProfileStatus.Off -> "No bloquea nada hasta que lo enciendas."
    is ProfileStatus.Paused -> "Vuelve a bloquear a las ${formatLocal(status.until)}."
    ProfileStatus.Always -> "Sin horario: bloquea siempre que se cumplan sus condiciones."
    is ProfileStatus.ActiveNow -> "Termina en ${formatAmountOfTime(status.minutesLeft.toLong())}."
    is ProfileStatus.StartsIn -> "Empieza en ${formatAmountOfTime(status.minutes.toLong())}."
    ProfileStatus.WaitingConditions -> "Está en su horario, pero aún no se cumple la red, el cargador o el lugar que pide."
    ProfileStatus.ActiveByConditions -> "Fuera de su horario, pero se cumple otra de sus condiciones."
}

private const val MAX_NAMES = 8
