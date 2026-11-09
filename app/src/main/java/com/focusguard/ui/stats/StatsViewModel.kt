package com.focusguard.ui.stats

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusguard.appLabel
import com.focusguard.data.BlockLog
import com.focusguard.data.SettingsRepository
import com.focusguard.data.SystemUsageEvents
import com.focusguard.data.TrustedClock
import com.focusguard.data.UsageHistory
import com.focusguard.data.UsageOverview
import com.focusguard.data.WebStats
import com.focusguard.decision.BlockStats
import com.focusguard.decision.DayInsight
import com.focusguard.decision.DayUsageDetail
import com.focusguard.decision.Insights
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [packageName] para pintar el icono; null si no es una app (un dominio). */
data class AppUsage(val label: String, val minutes: Long, val opens: Int = 0, val packageName: String? = null)

data class BlockedTarget(val label: String, val count: Int, val packageName: String? = null)

data class WebRow(val host: String, val visits: Int, val minutes: Long)

/** Lo que cambia al elegir otro día. */
data class DayStats(
    val topApps: List<AppUsage> = emptyList(),
    /** Desbloqueos de pantalla; null si el sistema no los registra (Android 8 y 9 no). */
    val unlocks: Int? = null,
    val usageAvailable: Boolean = true,
    /** Webs del día; null si no se guardan. */
    val webs: List<WebRow>? = null,
    /** Minutos en incógnito del día; null si no se cuentan. */
    val incognitoMinutes: Long? = null,
    /** El rato de uso más largo, el descanso más largo y las sesiones de bloqueo del día (K3). */
    val insight: DayInsight? = null,
)

data class StatsState(
    val perDay: List<Pair<LocalDate, Int>> = emptyList(),
    val topBlocked: List<BlockedTarget> = emptyList(),
    /** Los últimos días, del más antiguo al de hoy, para elegir cuál ver. */
    val days: List<LocalDate> = emptyList(),
    val day: LocalDate? = null,
    val usage: DayStats = DayStats(),
    /** Cómo va hoy frente a otros días (K3, K6); null mientras carga o sin acceso de uso. */
    val overview: UsageOverview? = null,
)

class StatsViewModel(
    private val blockLog: BlockLog,
    private val context: Context,
    private val clock: TrustedClock,
    private val webStats: WebStats,
    private val settings: SettingsRepository,
    private val usageEvents: SystemUsageEvents,
    private val history: UsageHistory,
) : ViewModel() {

    var state by mutableStateOf(StatsState())
        private set

    private var loading: Job? = null
    private var loadingOverview: Job? = null

    /**
     * Visita nueva a la pestaña: bloqueos de la semana y el uso de hoy.
     * Lo barato (los bloqueos, que salen de la base de datos) se enseña ya y no espera a lo caro (el uso del día, que lee las
     * estadísticas del sistema y puede tardar): antes todo salía junto, y mientras tanto la pestaña decía «Todavía no hay bloqueos».
     */
    fun load() {
        // Una visita nueva descarta la carga anterior para que no pise el resultado.
        loading?.cancel()
        loading = viewModelScope.launch(Dispatchers.Default) {
            val zone = ZoneId.systemDefault()
            // Los bloqueos se guardan con la hora de confianza, así que los días se cuentan con ella.
            val today = clock.localAt(clock.now()).toLocalDate()
            val days = (DAYS - 1 downTo 0).map { today.minusDays(it.toLong()) }
            // Revisitar la pestaña conserva el día que se estaba mirando.
            val shown = state.day?.takeIf { it in days } ?: today
            val events = withContext(Dispatchers.IO) { blockLog.since(days.first().atStartOfDay(zone).toInstant()) }
            update {
                it.copy(
                    perDay = BlockStats.perDay(events, today, DAYS, zone),
                    topBlocked = BlockStats.topTargets(events, 5).map { (target, count) ->
                        val label = context.appLabel(target)
                        // Solo una app tiene icono: si el nombre no se resolvió, el objetivo es un dominio.
                        BlockedTarget(label, count, target.takeIf { label != target })
                    },
                    days = days,
                    day = shown,
                )
            }
            val usage = dayStats(shown, zone)
            update { it.copy(usage = usage) }
        }
        // Lo más pesado va aparte, para que lo de siempre salga ya y para que elegir un día no lo cancele.
        loadingOverview?.cancel()
        loadingOverview = viewModelScope.launch(Dispatchers.Default) {
            val overview = history.overview(ZoneId.systemDefault(), refresh = true)
            update { it.copy(overview = overview) }
        }
    }

    /** Cada carga escribe una parte del estado desde su propio hilo: leer y escribir juntos evita que una pise a otra. */
    private fun update(change: (StatsState) -> StatsState) = synchronized(this) { state = change(state) }

    /** Cambia el día que se ve sin tocar lo demás. */
    fun selectDay(day: LocalDate) {
        if (day !in state.days) return
        loading?.cancel()
        loading = viewModelScope.launch(Dispatchers.Default) {
            val usage = dayStats(day, ZoneId.systemDefault())
            update { it.copy(day = day, usage = usage) }
        }
    }

    private suspend fun dayStats(day: LocalDate, zone: ZoneId): DayStats = withContext(Dispatchers.IO) {
        val detail = history.dayDetail(day, zone)
        val usage = detail?.usage
        val stats = settings.stats.value
        val webs = if (stats.any) webStats.forDay(day) else emptyMap()
        DayStats(
            topApps = usage?.apps.orEmpty().take(TOP_APPS).map { AppUsage(context.appLabel(it.packageName), it.timeMs / 60_000, it.opens, it.packageName) },
            unlocks = usage?.unlocks?.takeIf { usageEvents.unlocksSupported },
            usageAvailable = usage != null,
            insight = detail?.let { insightOf(it, day, zone) },
            webs = if (stats.webs) webs.filterKeys { !it.incognito }
                .map { (key, use) -> WebRow(key.host, use.visits, use.millis / 60_000) }
                .sortedWith(compareByDescending<WebRow> { it.minutes }.thenByDescending { it.visits })
                .take(TOP_APPS) else null,
            incognitoMinutes = if (stats.incognito) webs.filterKeys { it.incognito }.values.sumOf { it.millis } / 60_000 else null,
        )
    }

    /** Lo que dicen del día los ratos de uso y los bloqueos (K3). */
    private suspend fun insightOf(detail: DayUsageDetail, day: LocalDate, zone: ZoneId): DayInsight {
        val start = day.atStartOfDay(zone).toInstant()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant()
        return Insights.day(detail.spans, blockLog.since(start), start.toEpochMilli(), end.toEpochMilli())
    }

    private companion object {
        const val DAYS = 7
        const val TOP_APPS = 8
    }
}
