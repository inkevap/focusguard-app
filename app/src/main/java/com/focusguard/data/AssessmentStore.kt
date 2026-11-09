package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.focusguard.decision.Assessment
import com.focusguard.server.ApiResult
import com.focusguard.server.ServerApi
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AssessmentDao {
    @Insert
    abstract suspend fun insert(row: AssessmentResultEntity): Long

    @Query("SELECT * FROM assessment_results ORDER BY takenMs DESC, id DESC LIMIT :limit")
    abstract fun observeLatest(limit: Int): Flow<List<AssessmentResultEntity>>

    @Query("SELECT * FROM assessment_results ORDER BY takenMs DESC, id DESC LIMIT 1")
    abstract suspend fun latest(): AssessmentResultEntity?

    /** Guarda el resultado y devuelve su id y la puntuación de la vez anterior, en una sola transacción. */
    @Transaction
    open suspend fun insertWithPrevious(row: AssessmentResultEntity): Pair<Long, Int?> {
        val previous = latest()?.score
        return insert(row) to previous
    }

    @Query("UPDATE assessment_results SET uploaded = 1 WHERE id = :id")
    abstract suspend fun markUploaded(id: Long)

    @Query("DELETE FROM assessment_results")
    abstract suspend fun clear()
}

/**
 * Test de adicción (L6). Los resultados (solo la puntuación y cuándo) se guardan en el teléfono; a la cuenta del servidor solo van si el
 * usuario lo pide. Las respuestas no se guardan en ningún sitio, y el compañero no ve nada de esto.
 */
class AssessmentStore(
    private val dao: AssessmentDao,
    private val api: ServerApi,
    private val clock: TrustedClock,
    private val signedIn: () -> Boolean,
) {
    val results: Flow<List<AssessmentResultEntity>> = dao.observeLatest(HISTORY)

    /** Guarda el resultado de un test acabado y devuelve la puntuación anterior (la de la vez de antes), si la había. */
    suspend fun save(score: Int): SavedResult {
        require(score in 0..Assessment.MAX_SCORE) { "puntuación fuera de rango" }
        val (id, previous) = dao.insertWithPrevious(AssessmentResultEntity(takenMs = clock.nowMs(), score = score))
        return SavedResult(id, score, previous)
    }

    /** Guarda el resultado en la cuenta del servidor. Solo cuando el usuario lo pide. */
    suspend fun upload(result: AssessmentResultEntity): ApiResult<Unit> =
        api.saveAssessment(result.score, Instant.ofEpochMilli(result.takenMs)).also { if (it is ApiResult.Ok) dao.markUploaded(result.id) }

    /**
     * Borra los resultados de este teléfono y, con la sesión iniciada, también los de la cuenta. Devuelve el resultado de borrar en la
     * cuenta (`null` si no había sesión): lo de aquí se borra siempre.
     */
    suspend fun clear(): ApiResult<Unit>? {
        dao.clear()
        return if (signedIn()) api.deleteAssessments() else null
    }

    private companion object {
        const val HISTORY = 20
    }
}

/** Lo que devuelve guardar un test: el resultado y la puntuación de la vez anterior. */
class SavedResult(val id: Long, val score: Int, val previous: Int?)
