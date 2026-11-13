package com.focusguard.ui.profiles

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.decision.LimitProgress
import com.focusguard.decision.ProfileStatus
import com.focusguard.model.BlockMode
import com.focusguard.model.LimitKind
import com.focusguard.model.Profile
import com.focusguard.model.ProfileUsage
import com.focusguard.ui.components.AppRow
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgHero
import com.focusguard.ui.components.FgNote
import com.focusguard.ui.components.FgProgress
import com.focusguard.ui.components.FgRow
import com.focusguard.ui.components.FgScreen
import com.focusguard.ui.components.rememberAppLabels
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
    val pauseHistory by remember(profileId) { viewModel.pauseHistory(profileId) }.collectAsStateWithLifecycle(emptyList())
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = rememberMessageSnackbar(message, viewModel::clearMessage)
    val state = remember(profile, usage, nowMs, pauseHistory) {
        val loaded = usage
        if (loaded == null) null else profile?.let { p -> viewModel.state(p, loaded[p.id], nowMs).copy(pauses = pauseHistory) }
    }
    if (state == null) {
        // Mientras carga no hay nada que decir; solo si el perfil no existe se avisa.
        FgScreen("Vista previa", onBack = onBack, snackbar = snackbar) {
            if (profile == null && usage != null) item { FgNote("Este perfil ya no existe.", Modifier.padding(horizontal = 16.dp)) }
        }
    } else {
        ProfilePreviewContent(state, onEdit, { viewModel.pause(profileId, it) }, { viewModel.resume(profileId) }, onBack = onBack, snackbar = snackbar)
    }
}

@Composable
internal fun ProfilePreviewContent(
    state: ProfilePreviewState,
    onEdit: () -> Unit,
    onPause: (Int) -> Unit = {},
    onResume: () -> Unit = {},
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
) {
    val profile = state.profile
    FgScreen("Vista previa", modifier, onBack = onBack, snackbar = snackbar) {
        item {
            FgHero(
                title = "${profile.emoji} ${profile.title}".trim(),
                body = statusDetail(state.status),
                pill = statusLabel(state.status),
            )
        }
        item {
            FgGroup(header = "Cómo funciona") {
                FgRow("Horario", subtitle = scheduleSummary(profile.intervals))
                conditionsSummary(profile)?.let { FgRow("Condiciones", subtitle = it) }
                limitSummary(profile.limit)?.let { FgRow("Límite de uso", subtitle = it.removePrefix("Límite: ")) }
            }
        }
        item {
            FgGroup(header = if (profile.mode == BlockMode.BLOCK) "Qué bloquea" else "Qué permite") {
                FgRow(contentSummary(profile))
                val appLabels = rememberAppLabels(profile.apps.take(MAX_NAMES))
                appLabels.forEach { (pkg, label) -> FgCell { AppRow(pkg, label) } }
                if (profile.apps.size > MAX_NAMES) FgRow("y ${profile.apps.size - MAX_NAMES} más")
                if (profile.webs.isNotEmpty()) FgRow(profile.webs.take(MAX_NAMES).joinToString(", ") { it.value }, subtitle = "Webs")
            }
        }
        state.limit?.let { limit -> item { LimitCard(limit) } }
        item { PauseCard(state.status, state.pauses, onPause, onResume, enabled = !state.editLocked) }
        item {
            FgGroup(footer = if (state.editLocked) "El modo estricto está activo: no se puede editar ahora." else null) {
                FgActionRow("Editar perfil", onEdit, enabled = !state.editLocked)
            }
        }
    }
}

@Composable
private fun LimitCard(limit: LimitProgress) {
    val unit = if (limit.kind == LimitKind.OPENS) "aperturas" else "min"
    FgGroup(
        header = "Límite de uso",
        footer = if (limit.exhausted) "Agotado: ${limit.used} de ${limit.allowed} $unit." else "Llevas ${limit.used} de ${limit.allowed} $unit.",
    ) {
        FgCell {
            FgProgress(
                progress = { (limit.used.toFloat() / limit.allowed).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
