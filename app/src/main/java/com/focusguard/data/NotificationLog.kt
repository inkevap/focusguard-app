package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.focusguard.decision.NotificationRules
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CapturedNotificationDao {
    @Query("SELECT * FROM captured_notifications ORDER BY atMs DESC")
    abstract fun observeAll(): Flow<List<CapturedNotificationEntity>>

    /** Guarda una y aplica la retención en una sola transacción: solo las [keep] más recientes y las posteriores a [oldestMs]. */
    @Transaction
    open suspend fun add(row: CapturedNotificationEntity, keep: Int, oldestMs: Long) {
        insert(row)
        trim(keep)
        deleteBefore(oldestMs)
    }

    @Insert
    protected abstract suspend fun insert(row: CapturedNotificationEntity)

    @Query("DELETE FROM captured_notifications WHERE id NOT IN (SELECT id FROM captured_notifications ORDER BY atMs DESC LIMIT :keep)")
    protected abstract suspend fun trim(keep: Int)

    @Query("DELETE FROM captured_notifications WHERE atMs < :beforeMs")
    abstract suspend fun deleteBefore(beforeMs: Long)

    @Query("DELETE FROM captured_notifications")
    abstract suspend fun clear()
}

/** Notificaciones de apps bloqueadas que se quitaron de la barra. Se quedan en el teléfono, recortadas y por poco tiempo. */
class NotificationLog(private val dao: CapturedNotificationDao) {

    val all: Flow<List<CapturedNotificationEntity>> = dao.observeAll()

    /** Guarda la notificación recortada y descarta lo que pase del tope o de la retención. */
    suspend fun record(atMs: Long, packageName: String, title: CharSequence?, text: CharSequence?) {
        val row = CapturedNotificationEntity(atMs = atMs, packageName = packageName, title = NotificationRules.clip(title), text = NotificationRules.clip(text))
        dao.add(row, NotificationRules.MAX_KEPT, oldestAllowed(atMs))
    }

    /** Quita lo que ya pasó de la retención, aunque no llegue ninguna notificación nueva. */
    suspend fun prune(nowMs: Long) = dao.deleteBefore(oldestAllowed(nowMs))

    private fun oldestAllowed(nowMs: Long) = nowMs - TimeUnit.DAYS.toMillis(NotificationRules.RETENTION_DAYS)

    suspend fun clear() = dao.clear()
}
