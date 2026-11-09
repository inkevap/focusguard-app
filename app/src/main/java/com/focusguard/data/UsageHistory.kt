package com.focusguard.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusguard.decision.Change
import com.focusguard.decision.DayFigures
import com.focusguard.decision.DayUsageCalculator
import com.focusguard.decision.DayUsageDetail
import com.focusguard.decision.Insights
import com.focusguard.decision.Trend
import com.focusguard.decision.WeeklyReport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Dao
interface DayUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<DayUsageEntity>)

    @Query("SELECT * FROM day_usage WHERE epochDay >= :fromEpochDay")
    suspend fun since(fromEpochDay: Long): List<DayUsageEntity>

    @Query("DELETE FROM day_usage WHERE epochDay < :beforeEpochDay")
    suspend fun deleteBefore(beforeEpochDay: Long)
}

/** Lo que Estadísticas enseña de un vistazo: cómo va hoy, frente a ayer y esta semana frente a la anterior. */
data class UsageOverview(val trend: Trend, val todayMs: Long, val dayChange: Change, val report: WeeklyReport)

/**
 * El uso de los últimos días para los insights (K3, K4, K6), con los eventos de uso del sistema y el historial de bloqueos. El sistema
 * solo guarda eventos de una semana más o menos: de cada día terminado se guarda aquí su total, para que el informe semanal pueda
 * comparar con la semana anterior. Todo sale y se queda en el teléfono.
 */
class UsageHistory(
    private val context: Context,
    private val usageEvents: SystemUsageEvents,
    private val blockLog: BlockLog,
    private val dao: DayUsageDao,
) {

    @Volatile
    private var cached: Pair<Long, UsageOverview>? = null

    /**
     * `null` si falta el acceso de uso. El resultado se reutiliza unos minutos (Estadísticas y el informe semanal lo piden seguidos);
     * [refresh] lo vuelve a calcular. Los eventos del sistema llevan la hora del dispositivo, así que aquí se usa esa y no la de confianza.
     */
    suspend fun overview(zone: ZoneId, refresh: Boolean = false): UsageOverview? = withContext(Dispatchers.IO) {
        val now = Instant.now()
        cached?.takeIf { !refresh && now.toEpochMilli() - it.first < CACHE_MS }?.let { return@withContext it.second }
        val today = now.atZone(zone).toLocalDate()
        val first = today.minusDays(WINDOW_DAYS - 1L)
        val events = usageEvents.between(first.atStartOfDay(zone).toInstant().toEpochMilli() - Insights.LOOKBACK_MS, now.toEpochMilli()) ?: return@withContext null
        val blocksPerDay = blockLog.since(first.atStartOfDay(zone).toInstant()).groupingBy { it.at.atZone(zone).toLocalDate() }.eachCount()
        val stored = dao.since(first.toEpochDay()).associate { row ->
            val day = LocalDate.ofEpochDay(row.epochDay)
            day to DayFigures(day, row.usageMs, row.unlocks, blocksPerDay[day] ?: 0)
        }
        val data = Insights.overview(events, blocksPerDay, stored, now, zone, setOf(context.packageName), usageEvents.unlocksSupported)
        // Solo los días terminados (el de hoy aún cambia) y solo si mejoran lo guardado: un día cortado por el límite de lo que recuerda el
        // sistema da un total parcial. Lo viejo se borra para no guardar más de lo que se lee.
        dao.upsert(data.computed.filter { (stored[it.day]?.usageMs ?: -1L) < it.usageMs }.map { DayUsageEntity(it.day.toEpochDay(), it.usageMs, it.unlocks) })
        dao.deleteBefore(first.toEpochDay())
        UsageOverview(data.trend, data.todayMs, data.dayChange, data.report).also { cached = now.toEpochMilli() to it }
    }

    /** El uso de [day] con los ratos en que hubo apps abiertas; `null` si falta el acceso de uso. */
    suspend fun dayDetail(day: LocalDate, zone: ZoneId): DayUsageDetail? = withContext(Dispatchers.IO) {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        val events = usageEvents.between(start - Insights.LOOKBACK_MS, minOf(end, nowMs)) ?: return@withContext null
        DayUsageCalculator.detail(events, start, end, nowMs, setOf(context.packageName))
    }

    private companion object {
        /** Hoy y los 14 anteriores: lo que pide el informe semanal. */
        const val WINDOW_DAYS = 15
        const val CACHE_MS = 2 * 60_000L
    }
}
