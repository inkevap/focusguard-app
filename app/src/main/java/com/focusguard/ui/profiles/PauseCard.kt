package com.focusguard.ui.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.focusguard.decision.Pauses
import com.focusguard.decision.ProfileStatus
import com.focusguard.ui.components.ChipChoices
import com.focusguard.ui.components.FgActionRow
import com.focusguard.ui.components.FgCell
import com.focusguard.ui.components.FgGroup
import com.focusguard.ui.components.FgRow
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
    GroupStack(modifier) {
        if (status is ProfileStatus.Paused) {
            FgGroup(header = "Pausa", footer = "En pausa hasta las ${formatLocal(status.until)}.") {
                FgActionRow("Volver a bloquear ya", onResume)
            }
        } else {
            FgGroup(
                header = "Pausa",
                footer = "Deja de bloquear un rato y vuelve solo. Si tienes aprobaciones, pedirá una; con el modo estricto no se puede.",
            ) {
                FgCell {
                    ChipChoices(
                        options = Pauses.PRESET_MINUTES.map { it to formatAmountOfTime(it.toLong()) },
                        selected = minutes,
                        onSelect = { minutes = it },
                    )
                }
                FgActionRow("Pausar", { onPause(minutes) }, enabled = enabled && status != ProfileStatus.Off)
            }
        }
        if (history.isNotEmpty()) {
            FgGroup(header = "Pausas anteriores") {
                history.forEach { FgRow(formatLocal(it.startedAt, withDay = true), value = formatAmountOfTime(it.minutes.toLong())) }
            }
        }
    }
}
