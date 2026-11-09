package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.focusguard.decision.UrgeOutcome
import com.focusguard.decision.UrgeRow
import com.focusguard.decision.UrgeTrigger
import com.focusguard.decision.Urges
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Dao
interface UrgeDao {
    @Insert
    suspend fun insert(row: UrgeEntity): Long

    @Query("SELECT * FROM urges WHERE outcome = :active ORDER BY startedMs DESC LIMIT 1")
    suspend fun active(active: String): UrgeEntity?

    @Query("UPDATE urges SET outcome = :outcome, endedMs = :endedMs WHERE id = :id AND outcome = :active")
    suspend fun finish(id: Long, outcome: String, endedMs: Long, active: String)

    @Query("SELECT COUNT(*) FROM urges WHERE outcome = :outcome AND startedMs >= :sinceMs")
    suspend fun countSince(outcome: String, sinceMs: Long): Int

    @Query("SELECT * FROM urges WHERE outcome <> :active AND startedMs >= :sinceMs AND startedMs < :untilMs ORDER BY startedMs")
    suspend fun finishedBetween(sinceMs: Long, untilMs: Long, active: String): List<UrgeEntity>

    @Query("SELECT * FROM urges WHERE outcome <> :active AND startedMs >= :sinceMs ORDER BY startedMs")
    fun observeFinished(sinceMs: Long, active: String): Flow<List<UrgeEntity>>

    @Query("UPDATE urges SET trigger = :trigger WHERE id = :id")
    suspend fun setTrigger(id: Long, trigger: String?)
}

/** Un impulso en curso: lo que hace falta para seguir su cuenta atrás. */
data class Urge(val id: Long, val startedMs: Long)

/** Registro de impulsos. Un impulso sin contestar pasada una hora se da por abandonado: no se retoma una cuenta atrás de hace horas. */
class UrgeLog(private val dao: UrgeDao, private val clock: TrustedClock) {

    private val lock = Mutex()

    /** El impulso en curso, si lo hay y sigue vigente. */
    suspend fun current(): Urge? {
        val row = dao.active(ACTIVE) ?: return null
        if (Urges.isStale(row.startedMs, clock.nowMs())) {
            dao.finish(row.id, UrgeOutcome.ABANDONED.name, row.startedMs + Urges.STALE_AFTER_MS, ACTIVE)
            return null
        }
        return Urge(row.id, row.startedMs)
    }

    /** Empieza un impulso, o devuelve el que ya está en curso: dos toques seguidos no empiezan dos cuentas atrás. */
    suspend fun start(): Urge = lock.withLock {
        current() ?: clock.nowMs().let { now -> Urge(dao.insert(UrgeEntity(startedMs = now, outcome = ACTIVE)), now) }
    }

    suspend fun finish(id: Long, outcome: UrgeOutcome) {
        require(outcome != UrgeOutcome.ACTIVE) { "un impulso no acaba en curso" }
        dao.finish(id, outcome.name, clock.nowMs(), ACTIVE)
    }

    suspend fun setTrigger(id: Long, trigger: UrgeTrigger?) = dao.setTrigger(id, trigger?.name)

    /** El día de hoy en el calendario de [zone]: el informe agrupa los impulsos por día natural (a las 2 de la madrugada sigue siendo «hoy»), con la hora de confianza. */
    fun calendarToday(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): java.time.LocalDate = java.time.Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate()

    /** Los impulsos terminados desde [sinceMs], para el informe (L7). */
    fun finishedSince(sinceMs: Long): Flow<List<UrgeRow>> = dao.observeFinished(sinceMs, ACTIVE).map { rows -> rows.mapNotNull(::toRow) }

    /** Los terminados entre [sinceMs] (incluido) y [untilMs] (excluido). */
    suspend fun finishedBetween(sinceMs: Long, untilMs: Long): List<UrgeRow> = dao.finishedBetween(sinceMs, untilMs, ACTIVE).mapNotNull(::toRow)

    private fun toRow(row: UrgeEntity): UrgeRow? {
        val outcome = runCatching { UrgeOutcome.valueOf(row.outcome) }.getOrNull() ?: return null
        return UrgeRow(row.startedMs, outcome, row.trigger?.let { name -> UrgeTrigger.entries.firstOrNull { it.name == name } })
    }

    /** Cuántos impulsos acabaron así desde [sinceMs]. */
    suspend fun countSince(outcome: UrgeOutcome, sinceMs: Long): Int = dao.countSince(outcome.name, sinceMs)

    private companion object {
        val ACTIVE = UrgeOutcome.ACTIVE.name
    }
}
