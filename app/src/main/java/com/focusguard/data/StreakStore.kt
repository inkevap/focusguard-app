package com.focusguard.data

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.focusguard.decision.DayRecord
import com.focusguard.decision.StreakConfig
import com.focusguard.decision.Streaks
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Dao
abstract class DayRecordDao {
    @Query("SELECT * FROM day_records ORDER BY epochDay")
    abstract fun observeAll(): Flow<List<DayRecordEntity>>

    @Transaction
    open suspend fun addProtected(epochDay: Long, ms: Long) {
        ensure(DayRecordEntity(epochDay))
        increment(epochDay, ms)
    }

    @Transaction
    open suspend fun addPause(epochDay: Long, minutes: Int) {
        ensure(DayRecordEntity(epochDay))
        countPause(epochDay, minutes)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun ensure(row: DayRecordEntity)

    @Query("UPDATE day_records SET protectedMs = protectedMs + :ms WHERE epochDay = :epochDay")
    protected abstract suspend fun increment(epochDay: Long, ms: Long)

    @Query("UPDATE day_records SET pauses = pauses + 1, longestPauseMinutes = MAX(longestPauseMinutes, :minutes) WHERE epochDay = :epochDay")
    protected abstract suspend fun countPause(epochDay: Long, minutes: Int)
}

/**
 * Los datos de las rachas (L1, L2): un registro por día en Room y, en preferencias, lo que el usuario decide (meta, reinicio, días
 * restaurados). El resultado de cada día no se guarda: se calcula con `Streaks`.
 */
class StreakStore(context: Context, private val dao: DayRecordDao, private val settings: SettingsRepository, private val clock: TrustedClock) {

    private val prefs = context.getSharedPreferences("streaks", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<StreakConfig> = _config.asStateFlow()

    val records: Flow<Map<LocalDate, DayRecord>> = dao.observeAll().map(::toRecords)

    private fun toRecords(rows: List<DayRecordEntity>): Map<LocalDate, DayRecord> =
        rows.associate { LocalDate.ofEpochDay(it.epochDay).let { day -> day to DayRecord(day, it.protectedMs, it.pauses, it.longestPauseMinutes) } }

    /** El día lógico de [instant] (con el inicio del día de los ajustes). */
    fun dayOf(instant: Instant): LocalDate = Streaks.dayOf(clock.localAt(instant), settings.dayStartMinute.value)

    fun today(): LocalDate = dayOf(clock.now())

    /** `false` si no se pudo guardar (quien llama puede devolver el tiempo al acumulador). */
    suspend fun addProtected(byDay: Map<LocalDate, Long>): Boolean = safely { byDay.forEach { (day, ms) -> dao.addProtected(day.toEpochDay(), ms) } }

    /** Una pausa de [minutes] que empieza ahora (de un perfil o un desbloqueo de emergencia). Un fallo al guardarla no debe romper la pausa. */
    suspend fun recordPause(now: Instant, minutes: Int) {
        safely { dao.addPause(dayOf(now).toEpochDay(), minutes) }
    }

    /** Un fallo de Room se registra y se sigue, pero la cancelación de la corrutina se respeta. */
    private inline fun safely(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo guardar el dato de la racha", e)
        false
    }

    fun setGoal(days: Int) = update { copy(goalDays = days.coerceIn(0, Streaks.MAX_GOAL)) }

    /** Empieza la cuenta de nuevo desde hoy; la mejor racha y los días ya restaurados (que cuentan para la espera entre restauraciones) se conservan. */
    fun reset() = update { copy(startDay = today()) }

    /**
     * Da por logrado [day] solo si sigue siendo el día que se puede restaurar (las reglas de `Streaks.restorable`): una solicitud
     * aprobada días después, o repetida, no restaura nada fuera de ellas. Quien llama ya pasó por las aprobaciones.
     */
    suspend fun restore(day: LocalDate): Boolean {
        val all = records.first()
        // La comprobación y la escritura van juntas: dos aprobaciones a la vez no restauran dos veces.
        return synchronized(this) {
            if (Streaks.restorable(all, today(), _config.value)?.day != day) return@synchronized false
            update { copy(forgiven = forgiven + day) }
            true
        }
    }

    @Synchronized
    private fun update(change: StreakConfig.() -> StreakConfig) {
        val value = _config.value.change()
        prefs.edit()
            .putLong(KEY_START, value.startDay?.toEpochDay() ?: NO_DAY)
            .putInt(KEY_GOAL, value.goalDays)
            .putString(KEY_FORGIVEN, value.forgiven.joinToString(",") { it.toEpochDay().toString() })
            .apply()
        _config.value = value
    }

    private fun readConfig() = StreakConfig(
        startDay = prefs.getLong(KEY_START, NO_DAY).takeIf { it != NO_DAY }?.let(LocalDate::ofEpochDay),
        goalDays = prefs.getInt(KEY_GOAL, 0).coerceIn(0, Streaks.MAX_GOAL),
        forgiven = prefs.getString(KEY_FORGIVEN, "").orEmpty().split(',').mapNotNull { it.toLongOrNull()?.let(LocalDate::ofEpochDay) }.toSet(),
    )

    companion object {
        const val CANNOT_RESTORE = "Ese día ya no se puede restaurar."
        private const val TAG = "StreakStore"
        private const val KEY_START = "start"
        private const val KEY_GOAL = "goal"
        private const val KEY_FORGIVEN = "forgiven"
        private const val NO_DAY = Long.MIN_VALUE
    }
}
