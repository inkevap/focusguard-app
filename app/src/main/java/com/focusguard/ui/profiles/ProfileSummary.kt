package com.focusguard.ui.profiles

import com.focusguard.model.BlockMode
import com.focusguard.model.ConditionMode
import com.focusguard.model.LimitKind
import com.focusguard.model.LimitPeriod
import com.focusguard.model.Profile
import com.focusguard.model.ScheduleInterval
import com.focusguard.model.UsageLimit
import com.focusguard.ui.Days
import com.focusguard.ui.formatAmountOfTime
import com.focusguard.ui.plural
import java.time.DayOfWeek

/** "Lun a vie", "Sáb, Dom" o "Todos los días". Rachas de tres o más días seguidos se abrevian. */
internal fun dayRangeLabel(days: Set<DayOfWeek>): String {
    if (days.size == 7) return "Todos los días"
    val sorted = days.map { it.value }.sorted()
    val runs = mutableListOf<MutableList<Int>>()
    for (day in sorted) {
        if (runs.isNotEmpty() && runs.last().last() == day - 1) runs.last().add(day) else runs.add(mutableListOf(day))
    }
    return runs.joinToString(", ") { run ->
        if (run.size >= 3) "${Days.short(run.first())} a ${Days.short(run.last()).lowercase()}"
        else run.joinToString(", ") { Days.short(it) }
    }
}

private fun time(minutes: Int) = "%d:%02d".format(minutes / 60 % 24, minutes % 60)

private fun hours(interval: ScheduleInterval): String =
    if (interval.startMinute == 0 && interval.endMinute == 24 * 60) "Todo el día"
    else "${time(interval.startMinute)}–${time(interval.endMinute)}"

/** "Lun a vie · 9:00–17:00"; con muchos horarios distintos, solo cuántos hay. */
internal fun scheduleSummary(intervals: List<ScheduleInterval>): String {
    if (intervals.isEmpty()) return "Siempre activo"
    val groups = intervals.groupBy { it.startMinute to it.endMinute }
    if (groups.size > 2) return "${groups.size} horarios distintos"
    return groups.values.joinToString(" y ") { group ->
        "${dayRangeLabel(group.map { it.day }.toSet())} · ${hours(group.first())}"
    }
}

/** "Con la red Casa y cargando"; null si el perfil no usa Wi-Fi, Bluetooth, cargador ni lugar. */
internal fun conditionsSummary(profile: Profile): String? {
    val parts = buildList {
        if (profile.wifiNames.isNotEmpty()) {
            add(if (profile.wifiNames.size == 1) "con la red ${profile.wifiNames.first()}" else "con ${profile.wifiNames.size} redes Wi-Fi")
        }
        if (profile.bluetoothNames.isNotEmpty()) {
            add(if (profile.bluetoothNames.size == 1) "con ${profile.bluetoothNames.first()} conectado" else "con ${profile.bluetoothNames.size} dispositivos Bluetooth")
        }
        if (profile.requireCharging) add("cargando")
        profile.geofence?.let { add(if (it.inverted) "fuera de un lugar" else "en un lugar") }
    }
    if (parts.isEmpty()) return null
    val joiner = if (profile.conditionMode == ConditionMode.ANY) " o " else " y "
    return parts.joinToString(joiner).replaceFirstChar { it.uppercase() }
}

/** Qué hace el perfil, en una línea: "Bloquea 4 apps y 2 webs". */
internal fun contentSummary(profile: Profile): String {
    val verb = if (profile.mode == BlockMode.BLOCK) "Bloquea" else "Permite solo"
    val parts = buildList {
        if (profile.apps.isNotEmpty()) add(plural(profile.apps.size, "app", "apps"))
        if (profile.webs.isNotEmpty()) add(plural(profile.webs.size, "web", "webs"))
        if (profile.mode == BlockMode.BLOCK && profile.categories.isNotEmpty()) add(plural(profile.categories.size, "categoría", "categorías"))
        if (profile.subApps.isNotEmpty()) add(plural(profile.subApps.size, "sección", "secciones"))
    }
    return if (parts.isEmpty()) "$verb: aún nada elegido" else "$verb ${parts.joinToString(" y ")}"
}

/** "Límite: 1 h al día"; null si el perfil no tiene límite. */
internal fun limitSummary(limit: UsageLimit?): String? {
    limit ?: return null
    val period = when (limit.period) {
        LimitPeriod.HOURLY -> "por hora"
        LimitPeriod.DAILY -> "al día"
        LimitPeriod.WEEKLY -> "a la semana"
    }
    val each = if (limit.scope == com.focusguard.model.LimitScope.PER_TARGET) " por app" else ""
    return when (limit.kind) {
        LimitKind.TIME -> "Límite: ${formatAmountOfTime(limit.amount.toLong())} $period$each"
        LimitKind.OPENS -> "Límite: ${plural(limit.amount, "apertura", "aperturas")} $period$each"
        LimitKind.CONTINUOUS -> "Límite: ${formatAmountOfTime(limit.amount.toLong())} seguidos"
    }
}
