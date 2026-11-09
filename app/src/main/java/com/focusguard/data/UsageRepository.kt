package com.focusguard.data

import com.focusguard.model.ProfileUsage
import com.focusguard.model.UsageKey

class UsageRepository(private val dao: UsageDao) {

    suspend fun loadAll(): Map<Long, ProfileUsage> = dao.getAll().associate {
        it.profileId to ProfileUsage(
            periodStartMs = it.periodStartMs,
            timeMs = it.timeMs,
            opens = it.opens,
            continuousMs = it.continuousMs,
            lastActiveMs = it.lastActiveMs,
        )
    }

    suspend fun saveAll(usage: Map<Long, ProfileUsage>) {
        dao.upsertAll(
            usage.map { (id, u) ->
                ProfileUsageEntity(id, u.periodStartMs, u.timeMs, u.opens, u.continuousMs, u.lastActiveMs)
            },
        )
    }

    suspend fun loadTargets(): Map<UsageKey, ProfileUsage> = dao.getAllTargets().associate {
        UsageKey(it.profileId, it.target) to ProfileUsage(
            periodStartMs = it.periodStartMs,
            timeMs = it.timeMs,
            opens = it.opens,
            continuousMs = it.continuousMs,
            lastActiveMs = it.lastActiveMs,
        )
    }

    suspend fun saveTargets(usage: Map<UsageKey, ProfileUsage>) {
        dao.upsertTargets(
            usage.map { (key, u) ->
                TargetUsageEntity(key.profileId, key.target, u.periodStartMs, u.timeMs, u.opens, u.continuousMs, u.lastActiveMs)
            },
        )
    }

    suspend fun delete(profileId: Long) {
        dao.delete(profileId)
        dao.deleteTargets(profileId)
    }
}
