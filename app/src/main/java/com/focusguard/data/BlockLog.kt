package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.focusguard.model.BlockEvent
import com.focusguard.model.BlockReason
import java.time.Instant

@Dao
interface BlockEventDao {
    @Insert
    suspend fun insert(event: BlockEventEntity)

    @Query("SELECT * FROM block_events WHERE atMs >= :sinceMs ORDER BY atMs")
    suspend fun since(sinceMs: Long): List<BlockEventEntity>

    @Query("SELECT COUNT(*) FROM block_events WHERE atMs >= :sinceMs")
    suspend fun countSince(sinceMs: Long): Int

    @Query("DELETE FROM block_events WHERE atMs < :beforeMs")
    suspend fun deleteBefore(beforeMs: Long)
}

/** Historial de bloqueos del teléfono. Solo guarda qué se bloqueó y cuándo, nunca texto de pantalla. */
class BlockLog(private val dao: BlockEventDao) {

    suspend fun record(event: BlockEvent) =
        dao.insert(BlockEventEntity(atMs = event.at.toEpochMilli(), profileId = event.profileId, reason = event.reason.name, target = event.target))

    suspend fun since(since: Instant): List<BlockEvent> = dao.since(since.toEpochMilli()).map {
        BlockEvent(Instant.ofEpochMilli(it.atMs), it.profileId, BlockReason.valueOf(it.reason), it.target)
    }

    suspend fun countSince(since: Instant): Int = dao.countSince(since.toEpochMilli())

    suspend fun prune(before: Instant) = dao.deleteBefore(before.toEpochMilli())
}
