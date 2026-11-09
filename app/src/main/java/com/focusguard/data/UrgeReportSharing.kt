package com.focusguard.data

import com.focusguard.decision.UrgeReports
import com.focusguard.model.PartnerEventType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

/**
 * El resumen semanal de impulsos para el compañero (L7), solo si el usuario lo permite. Una vez por semana, con la semana anterior ya
 * completa, el teléfono le cuenta cuántos impulsos hubo, cuántos pasaron, la hora más frecuente y lo que más los desencadenó: nunca la
 * hora de cada uno ni nada escrito. El detalle solo lo ve el usuario.
 */
class UrgeReportSharing(
    private val log: UrgeLog,
    private val settings: SettingsRepository,
    private val partnerEvents: PartnerEventOutbox,
) {
    suspend fun report(zone: ZoneId = ZoneId.systemDefault()) {
        // Sin permiso, sin compañero activo o con los avisos apagados no sale nada; la semana no se da por contada (se cuenta si luego lo hay).
        if (!settings.shareUrgeReport.value || !partnerEvents.accepting) return
        try {
            val monday = UrgeReports.lastFullWeek(log.calendarToday(zone))
            if (!UrgeReports.due(settings.lastUrgeReportWeek, monday)) return
            val report = UrgeReports.build(log.finishedBetween(startMs(monday, zone), startMs(monday.plusWeeks(1), zone)), monday.plusDays(6), zone)
            // Se anota antes de encolar: si el proceso muere entre las dos cosas, una semana sin contar es mejor que contada doble.
            settings.setLastUrgeReportWeek(monday.toEpochDay())
            UrgeReports.weeklyLabel(report)?.let { partnerEvents.record(PartnerEventType.URGE_REPORT, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Es un extra del informe diario: un fallo suyo no puede frenar los avisos al compañero. Esa semana no sale (anotada antes de encolar).
        }
    }

    private fun startMs(day: LocalDate, zone: ZoneId) = day.atStartOfDay(zone).toInstant().toEpochMilli()
}
