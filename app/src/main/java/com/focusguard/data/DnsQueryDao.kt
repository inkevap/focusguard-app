package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Un día con sus consultas bloqueadas y permitidas. */
data class DnsDay(val day: Long, val blocked: Int, val allowed: Int)

@Dao
abstract class DnsQueryDao {
    /** Suma [count] consultas de [host]; SQLite de Android 8 no tiene «upsert», así que se asegura la fila y se incrementa. */
    @Transaction
    open suspend fun add(day: Long, host: String, blocked: Boolean, count: Int, lastMs: Long) {
        ensure(DnsQueryEntity(day, host, blocked, 0, 0))
        increment(day, host, blocked, count, lastMs)
    }

    /** Varias filas en una sola transacción: o entran todas o ninguna. */
    @Transaction
    open suspend fun addAll(rows: List<DnsQueryEntity>) = rows.forEach { add(it.day, it.host, it.blocked, it.count, it.lastMs) }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun ensure(row: DnsQueryEntity)

    @Query("UPDATE dns_queries SET count = count + :count, lastMs = MAX(lastMs, :lastMs) WHERE day = :day AND host = :host AND blocked = :blocked")
    protected abstract suspend fun increment(day: Long, host: String, blocked: Boolean, count: Int, lastMs: Long)

    @Query("SELECT day, SUM(CASE WHEN blocked THEN count ELSE 0 END) AS blocked, SUM(CASE WHEN blocked THEN 0 ELSE count END) AS allowed FROM dns_queries GROUP BY day ORDER BY day DESC")
    abstract fun observeDays(): Flow<List<DnsDay>>

    @Query("SELECT * FROM dns_queries WHERE day = :day AND blocked = :blocked ORDER BY count DESC, host LIMIT :limit")
    abstract fun observeHosts(day: Long, blocked: Boolean, limit: Int): Flow<List<DnsQueryEntity>>

    @Query("SELECT COALESCE(SUM(count), 0) FROM dns_queries WHERE day = :day AND blocked = :blocked")
    abstract suspend fun total(day: Long, blocked: Boolean): Int

    @Query("DELETE FROM dns_queries WHERE day < :firstDay")
    abstract suspend fun purgeBefore(firstDay: Long)

    @Query("DELETE FROM dns_queries")
    abstract suspend fun clear()
}
