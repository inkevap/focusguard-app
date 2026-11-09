package com.focusguard.decision

import com.focusguard.model.BlockEvent
import java.time.LocalDate
import java.time.ZoneId

object BlockStats {

    /** Bloqueos por día en los últimos [days] días, incluido [today], del más antiguo al más reciente. */
    fun perDay(events: List<BlockEvent>, today: LocalDate, days: Int, zone: ZoneId): List<Pair<LocalDate, Int>> {
        require(days > 0) { "days debe ser positivo" }
        val counts = events.groupingBy { it.at.atZone(zone).toLocalDate() }.eachCount()
        return (days - 1 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            day to (counts[day] ?: 0)
        }
    }

    /** Lo más bloqueado, de más a menos; a igualdad, por nombre. */
    fun topTargets(events: List<BlockEvent>, limit: Int): List<Pair<String, Int>> =
        events.groupingBy { it.target }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key to it.value }
}
