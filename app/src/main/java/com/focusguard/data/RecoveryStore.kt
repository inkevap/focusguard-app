package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.focusguard.decision.Recovery
import com.focusguard.decision.StepRecord
import com.focusguard.decision.StepStatus
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
abstract class RecoveryDao {
    @Query("SELECT * FROM recovery_steps")
    abstract fun observeAll(): Flow<List<RecoveryStepEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(row: RecoveryStepEntity)

    @Query("SELECT * FROM recovery_steps")
    abstract suspend fun all(): List<RecoveryStepEntity>

    /**
     * Lee lo hecho y guarda el paso en una sola transacción, con las reglas de `Recovery`: solo el paso que toca y solo con una respuesta válida.
     * Dos toques seguidos o una pantalla con datos viejos no se saltan el orden.
     */
    @Transaction
    open suspend fun completeIfOpen(step: Int, answer: String, today: Long): Boolean {
        val definition = Recovery.STEPS.firstOrNull { it.id == step } ?: return false
        val current = all().associate { it.step to StepRecord(it.doneDay, it.answer) }
        if (Recovery.statuses(current, today).getOrNull(step - 1) != StepStatus.Open) return false
        val clean = Recovery.normalize(definition, answer)
        if (!Recovery.canComplete(definition, clean)) return false
        upsert(RecoveryStepEntity(step, today, clean))
        return true
    }

    @Query("DELETE FROM recovery_steps")
    abstract suspend fun clear()
}

/**
 * Programa de recuperación (L5). Las reglas (orden, un paso por día, qué respuesta vale) están en `Recovery` y se hacen cumplir aquí, al
 * guardar, no solo en la pantalla. Lo contestado no sale del teléfono: ni a la copia de seguridad ni al servidor ni al compañero.
 */
class RecoveryStore(private val dao: RecoveryDao, private val streaks: StreakStore) {

    val progress: Flow<Map<Int, StepRecord>> = dao.observeAll().map { rows -> rows.associate { it.step to StepRecord(it.doneDay, it.answer) } }

    fun today(): LocalDate = streaks.today()

    /** Da por hecho el paso [step] con su respuesta si es el que toca y la respuesta vale; si no, no hace nada y devuelve false. */
    suspend fun complete(step: Int, answer: String): Boolean = dao.completeIfOpen(step, answer, today().toEpochDay())

    /** Empieza de nuevo: borra lo hecho. */
    suspend fun restart() = dao.clear()
}
