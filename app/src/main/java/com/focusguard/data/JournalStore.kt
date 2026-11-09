package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.focusguard.decision.Journal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
abstract class JournalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(row: JournalEntryEntity)

    @Query("SELECT * FROM journal_entries ORDER BY epochDay DESC LIMIT :limit")
    abstract fun observeEntries(limit: Int): Flow<List<JournalEntryEntity>>

    @Insert
    abstract suspend fun insertActivity(row: PlannedActivityEntity)

    @Query("SELECT COUNT(*) FROM planned_activities WHERE epochDay = :epochDay")
    abstract suspend fun countActivities(epochDay: Long): Int

    /** Cuenta e inserta en una sola transacción con la regla de `Journal.canPlan`: dos planificaciones a la vez no pasan del tope. */
    @Transaction
    open suspend fun planIfAllowed(row: PlannedActivityEntity, today: LocalDate): Boolean {
        val day = LocalDate.ofEpochDay(row.epochDay)
        if (!Journal.canPlan(day, today, countActivities(row.epochDay), row.text)) return false
        insertActivity(row)
        return true
    }

    @Query("UPDATE planned_activities SET doneMs = :doneMs WHERE id = :id AND epochDay = :epochDay AND doneMs IS NULL")
    abstract suspend fun markDone(id: Long, epochDay: Long, doneMs: Long)

    @Query("SELECT * FROM planned_activities WHERE epochDay >= :fromEpochDay ORDER BY epochDay, id")
    abstract fun observeActivitiesFrom(fromEpochDay: Long): Flow<List<PlannedActivityEntity>>
}

/**
 * Diario y planificador (L4). Las reglas están en `Journal`: aquí se hacen cumplir al escribir, no en la pantalla. Lo escrito no sale
 * del teléfono: ni a la copia de seguridad ni al servidor ni al compañero.
 */
class JournalStore(private val dao: JournalDao, private val streaks: StreakStore, private val clock: TrustedClock) {

    val entries: Flow<List<JournalEntryEntity>> = dao.observeEntries(ENTRIES_SHOWN)

    /** Las actividades de hoy en adelante: las de días pasados ya no se enseñan. */
    fun activities(today: LocalDate): Flow<List<PlannedActivityEntity>> = dao.observeActivitiesFrom(today.toEpochDay())

    fun today(): LocalDate = streaks.today()

    /** Guarda la entrada de hoy (la de días pasados no se reescribe). Un texto vacío no crea ni borra nada. */
    suspend fun saveToday(text: String): Boolean {
        val clean = Journal.cleanEntry(text)
        if (clean.isEmpty()) return false
        dao.upsert(JournalEntryEntity(today().toEpochDay(), clean, clock.nowMs()))
        return true
    }

    /** Planifica una actividad para [day] (hoy o más adelante) si no hay ya tres. No se podrá editar después. */
    suspend fun plan(day: LocalDate, text: String): Boolean {
        return dao.planIfAllowed(PlannedActivityEntity(epochDay = day.toEpochDay(), text = Journal.cleanActivity(text)), today())
    }

    /** Marca una actividad como hecha, solo el mismo día. */
    suspend fun complete(id: Long, day: LocalDate): Boolean {
        if (!Journal.canComplete(day, today())) return false
        dao.markDone(id, day.toEpochDay(), clock.nowMs())
        return true
    }

    private companion object {
        const val ENTRIES_SHOWN = 90
    }
}
