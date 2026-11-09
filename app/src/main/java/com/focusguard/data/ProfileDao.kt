package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProfileDao {

    @Transaction
    @Query("SELECT * FROM profiles ORDER BY id")
    abstract fun observeAll(): Flow<List<ProfileWithDetails>>

    @Transaction
    @Query("SELECT * FROM profiles ORDER BY id")
    abstract suspend fun getAll(): List<ProfileWithDetails>

    @Transaction
    @Query("SELECT * FROM profiles WHERE id = :id")
    abstract suspend fun get(id: Long): ProfileWithDetails?

    /** Actualiza de verdad (no reemplaza): reemplazar borraría la fila y arrastraría a las tablas hijas con su historial. */
    @Upsert
    protected abstract suspend fun upsertProfile(profile: ProfileEntity): Long

    @Insert
    protected abstract suspend fun insertApps(apps: List<ProfileAppEntity>)

    @Insert
    protected abstract suspend fun insertWebs(webs: List<ProfileWebEntity>)

    @Insert
    protected abstract suspend fun insertIntervals(intervals: List<ProfileIntervalEntity>)

    @Insert
    protected abstract suspend fun insertWifis(wifis: List<ProfileWifiEntity>)

    @Query("DELETE FROM profile_wifis WHERE profileId = :profileId")
    protected abstract suspend fun clearWifis(profileId: Long)

    @Query("DELETE FROM profile_apps WHERE profileId = :profileId")
    protected abstract suspend fun clearApps(profileId: Long)

    @Query("DELETE FROM profile_webs WHERE profileId = :profileId")
    protected abstract suspend fun clearWebs(profileId: Long)

    @Query("DELETE FROM profile_intervals WHERE profileId = :profileId")
    protected abstract suspend fun clearIntervals(profileId: Long)

    @Query("DELETE FROM profiles WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Guarda el perfil y reemplaza sus listas hijas en una sola transacción. Devuelve el id. */
    @Transaction
    open suspend fun save(
        profile: ProfileEntity,
        apps: List<String>,
        webs: List<ProfileWebEntity>,
        intervals: List<ProfileIntervalEntity>,
        wifis: List<String>,
    ): Long {
        val id = if (profile.id == 0L) upsertProfile(profile) else profile.id.also { upsertProfile(profile) }
        clearApps(id)
        clearWebs(id)
        clearIntervals(id)
        clearWifis(id)
        insertApps(apps.distinct().map { ProfileAppEntity(id, it) })
        insertWebs(webs.map { it.copy(id = 0, profileId = id) })
        insertIntervals(intervals.map { it.copy(id = 0, profileId = id) })
        insertWifis(wifis.distinct().map { ProfileWifiEntity(id, it) })
        return id
    }
}
