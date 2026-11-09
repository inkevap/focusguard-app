package com.focusguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UsageDao {

    @Query("SELECT * FROM profile_usage")
    suspend fun getAll(): List<ProfileUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ProfileUsageEntity>)

    @Query("DELETE FROM profile_usage WHERE profileId = :profileId")
    suspend fun delete(profileId: Long)

    @Query("SELECT * FROM target_usage")
    suspend fun getAllTargets(): List<TargetUsageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTargets(rows: List<TargetUsageEntity>)

    @Query("DELETE FROM target_usage WHERE profileId = :profileId")
    suspend fun deleteTargets(profileId: Long)
}
