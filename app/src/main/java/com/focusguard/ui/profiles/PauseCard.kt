package com.focusguard.ui.profiles

import com.focusguard.ui.icons.FgIcons
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.focusguard.decision.Pauses
import com.focusguard.decision.ProfileStatus
import com.focusguard.ui.components.AppCard
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.Hint
import com.focusguard.ui.components.PrimaryButton
import com.focusguard.ui.components.SecondaryButton
import com.focusguard.ui.components.SectionTitle
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.formatLocal
import java.time.Instant

/** Una pausa pasada, para el historial. */
internal data class PauseRecord(val startedAt: Instant, val minutes: Int)

/** Pausar un perfil un rato (E9) y ver las pausas anteriores. Pausar pasa por las aprobaciones; volver a bloquear es libre. */
@Composable
internal fun PauseCard(
    status: ProfileStatus,
    history: List<PauseRecord>,
    onPause: (Int) -> Unit,
    onResume: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var minutes by rememberSaveable { mutableIntStateOf(Pauses.PRESET_MINUTES[1]) }
    AppCard(modifier) {
        SectionTitle("Pausa", FgIcons.Timer)
        if (status is ProfileStatus.Paused) {
            Hint("En pausa hasta las ${formatLocal(status.until)}.")
            SecondaryButton("Volver a bloquear ya", onResume)
        } else {
            Hint("Deja de bloquear un rato y vuelve solo. Si tienes aprobaciones, pedirá una; con el modo estricto no se puede.")
            ChipChoices(
                options = Pauses.PRESET_MINUTES.map { it to formatAmountOfTime(it.toLong()) },
                selected = minutes,
                onSelect = { minutes = it },
            )
            PrimaryButton("Pausar", { onPause(minutes) }, Modifier.padding(top = 12.dp), enabled = enabled && status != ProfileStatus.Off)
        }
        if (history.isNotEmpty()) {
            Text("Pausas anteriores", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            history.forEach { Hint("${formatLocal(it.startedAt, withDay = true)} · ${formatAmountOfTime(it.minutes.toLong())}") }
        }
    }
}
