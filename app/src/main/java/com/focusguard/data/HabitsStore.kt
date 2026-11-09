package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.focusguard.decision.Mood
import com.focusguard.decision.Reminder
import com.focusguard.decision.Reminders
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class HabitsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertMood(row: MoodEntity)

    @Query("SELECT * FROM moods WHERE epochDay >= :sinceEpochDay ORDER BY atMs DESC")
    abstract fun moodsSince(sinceEpochDay: Long): Flow<List<MoodEntity>>

    @Query("SELECT MAX(epochDay) FROM moods")
    abstract fun lastMoodDay(): Flow<Long?>

    @Insert
    abstract suspend fun insertReminder(row: ReminderEntity)

    /** Cuenta e inserta en una sola transacción con la regla de `Reminders.canAdd`. */
    @Transaction
    open suspend fun addReminderIfAllowed(row: ReminderEntity): Boolean {
        if (!Reminders.canAdd(reminderCount(), row.minuteOfDay, row.text)) return false
        insertReminder(row)
        return true
    }

    @Query("SELECT * FROM reminders ORDER BY minuteOfDay, id")
    abstract fun reminders(): Flow<List<ReminderEntity>>

    @Query("SELECT COUNT(*) FROM reminders")
    abstract suspend fun reminderCount(): Int

    @Query("UPDATE reminders SET enabled = :enabled WHERE id = :id")
    abstract suspend fun setReminderEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM reminders WHERE id = :id")
    abstract suspend fun deleteReminder(id: Long)
}

/** Estados de ánimo (K5, uno por día: el último) y recordatorios propios (J4). Todo se queda en el teléfono. */
class HabitsStore(private val dao: HabitsDao, private val clock: TrustedClock, private val streaks: StreakStore) {

    val reminders: Flow<List<Reminder>> = dao.reminders().map { rows -> rows.map { Reminder(it.id, it.minuteOfDay, it.text, it.enabled) } }

    /** Los ánimos desde [since] (día lógico), del más reciente al más antiguo. */
    fun moodsSince(since: LocalDate): Flow<List<MoodEntity>> = dao.moodsSince(since.toEpochDay())

    /** El último día lógico en que se apuntó un ánimo; null si nunca. */
    val lastMoodDay: Flow<LocalDate?> = dao.lastMoodDay().map { day -> day?.let(LocalDate::ofEpochDay) }

    suspend fun logMood(mood: Mood) {
        val now = clock.now()
        dao.upsertMood(MoodEntity(epochDay = streaks.dayOf(now).toEpochDay(), atMs = now.toEpochMilli(), level = mood.level))
    }

    /** Añade un recordatorio si cabe (hasta [Reminders.MAX]) y el texto y la hora valen. */
    suspend fun addReminder(minuteOfDay: Int, text: String): Boolean =
        dao.addReminderIfAllowed(ReminderEntity(minuteOfDay = minuteOfDay, text = Reminders.clean(text)))

    suspend fun setReminderEnabled(id: Long, enabled: Boolean) = dao.setReminderEnabled(id, enabled)

    suspend fun deleteReminder(id: Long) = dao.deleteReminder(id)
}
