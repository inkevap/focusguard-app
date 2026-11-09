package com.focusguard.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.focusguard.decision.WebKey
import com.focusguard.decision.WebUsage
import java.time.LocalDate

@Dao
abstract class WebUsageDao {
    @Query("SELECT * FROM web_usage WHERE day = :day")
    abstract suspend fun forDay(day: String): List<WebUsageEntity>

    /** Suma a lo que ya hay. No usa UPSERT: SQLite lo trae desde Android 11 y la app admite Android 8. */
    @Transaction
    open suspend fun add(day: String, host: String, incognito: Boolean, visits: Int, millis: Long) {
        create(day, host, incognito)
        sum(day, host, incognito, visits, millis)
    }

    @Query("INSERT OR IGNORE INTO web_usage (day, host, incognito, visits, millis) VALUES (:day, :host, :incognito, 0, 0)")
    protected abstract suspend fun create(day: String, host: String, incognito: Boolean)

    @Query("UPDATE web_usage SET visits = visits + :visits, millis = millis + :millis WHERE day = :day AND host = :host AND incognito = :incognito")
    protected abstract suspend fun sum(day: String, host: String, incognito: Boolean, visits: Int, millis: Long)

    @Query("DELETE FROM web_usage WHERE day < :before")
    abstract suspend fun deleteBefore(before: String)

    @Query("DELETE FROM web_usage")
    abstract suspend fun clear()
}

/** Estadísticas de webs y de incógnito. Solo dominios, nunca la dirección completa, y no salen del teléfono. */
class WebStats(private val dao: WebUsageDao) {

    /** Suma lo acumulado a lo que ya había guardado ese día. */
    suspend fun add(day: LocalDate, usage: Map<WebKey, WebUsage>) {
        usage.forEach { (key, add) -> dao.add(day.toString(), key.host, key.incognito, add.visits, add.millis) }
    }

    suspend fun forDay(day: LocalDate): Map<WebKey, WebUsage> =
        dao.forDay(day.toString()).associate { WebKey(it.host, it.incognito) to WebUsage(it.visits, it.millis) }

    suspend fun prune(before: LocalDate) = dao.deleteBefore(before.toString())

    suspend fun clear() = dao.clear()
}
