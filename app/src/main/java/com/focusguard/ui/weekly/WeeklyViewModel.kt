package com.focusguard.ui.weekly

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.data.StreakStore
import com.focusguard.data.TrustedClock
import com.focusguard.data.UrgeLog
import com.focusguard.data.UsageHistory
import com.focusguard.decision.Streaks
import com.focusguard.decision.UrgeOutcome
import com.focusguard.decision.WeeklyReport
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Todo lo del informe semanal. [report] es null si falta el acceso de uso. */
data class WeeklyUi(
    val report: WeeklyReport? = null,
    /** Días de los últimos 7 en que se logró la racha. */
    val streakDays: Int = 0,
    val urgesPassed: Int = 0,
    val urgesGaveIn: Int = 0,
    val loaded: Boolean = false,
)

/** Informe semanal (K4): los últimos siete días frente a los anteriores, con lo que pasó con la racha y los impulsos. */
class WeeklyViewModel(
    private val history: UsageHistory,
    private val streaks: StreakStore,
    private val urges: UrgeLog,
    private val clock: TrustedClock,
) : ViewModel() {

    var ui by mutableStateOf(WeeklyUi())
        private set

    /** Cada vez que se abre la pantalla (también al volver de dar el acceso de uso). */
    fun load() {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            // Los días del informe son naturales (como en Estadísticas); los de la racha, los de su ajuste de inicio del día.
            val streakToday = streaks.today()
            val records = streaks.records.first()
            val config = Streaks.resolve(records, streaks.config.value)
            val since = clock.now().atZone(zone).toLocalDate().minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
            ui = WeeklyUi(
                report = history.overview(zone)?.report,
                streakDays = (0..6).count { Streaks.outcome(streakToday.minusDays(it.toLong()), streakToday, records, config).counts },
                urgesPassed = urges.countSince(UrgeOutcome.PASSED, since),
                urgesGaveIn = urges.countSince(UrgeOutcome.GAVE_IN, since),
                loaded = true,
            )
        }
    }
}
