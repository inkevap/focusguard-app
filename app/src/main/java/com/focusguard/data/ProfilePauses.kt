package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.focusguard.decision.Pauses
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProfilePauseDao {
    @Query("SELECT * FROM profile_pauses WHERE profileId = :profileId ORDER BY startedMs DESC")
    abstract fun observe(profileId: Long): Flow<List<ProfilePauseEntity>>

    /** Guarda la pausa y deja solo las [keep] más recientes de ese perfil. */
    @Transaction
    open suspend fun add(row: ProfilePauseEntity, keep: Int) {
        insert(row)
        trim(row.profileId, keep)
    }

    @Insert
    protected abstract suspend fun insert(row: ProfilePauseEntity)

    @Query("DELETE FROM profile_pauses WHERE startedMs < :beforeMs")
    abstract suspend fun deleteBefore(beforeMs: Long)

    @Query("DELETE FROM profile_pauses WHERE profileId = :profileId AND id NOT IN (SELECT id FROM profile_pauses WHERE profileId = :profileId ORDER BY startedMs DESC LIMIT :keep)")
    protected abstract suspend fun trim(profileId: Long, keep: Int)
}

/** Pausar y reanudar perfiles (E9), con su historial. Quien llama decide si hace falta aprobación antes. */
class ProfilePauses(
    private val profiles: ProfileRepository,
    private val dao: ProfilePauseDao,
    private val clock: TrustedClock,
    private val streaks: StreakStore,
) {

    fun history(profileId: Long): Flow<List<ProfilePauseEntity>> = dao.observe(profileId)

    /** false si la duración no es una de las ofrecidas o el perfil no existe. */
    suspend fun pause(profileId: Long, minutes: Int): Boolean {
        if (!Pauses.isValid(minutes)) return false
        val profile = profiles.get(profileId) ?: return false
        val now = clock.now()
        profiles.save(profile.copy(pausedUntil = Pauses.until(now, minutes)))
        // Si el perfil se borró entre una cosa y otra, el historial no tiene a quién colgarse: no es un fallo de la pausa.
        runCatching { dao.add(ProfilePauseEntity(profileId = profileId, startedMs = now.toEpochMilli(), minutes = minutes), Pauses.HISTORY_KEPT) }
        // Una pausa debilita el bloqueo del día: cuenta para las rachas aunque se reanude antes.
        streaks.recordPause(now, minutes)
        return true
    }

    /** Volver a bloquear antes de tiempo endurece: es libre. */
    suspend fun resume(profileId: Long) {
        val profile = profiles.get(profileId) ?: return
        if (profile.pausedUntil != null) profiles.save(profile.copy(pausedUntil = null))
    }

    /** Quita las pausas más viejas que la retención, como el resto del historial. */
    suspend fun prune(before: Instant) = dao.deleteBefore(before.toEpochMilli())
}
