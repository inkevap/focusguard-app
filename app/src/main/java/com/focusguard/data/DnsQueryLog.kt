package com.focusguard.data

import com.focusguard.decision.Streaks
import com.focusguard.dns.DnsAnswer
import com.focusguard.dns.DnsLogPolicy
import com.focusguard.dns.DnsMessages
import com.focusguard.dns.DnsSummary
import com.focusguard.dns.DnsTally
import com.focusguard.model.PartnerEventType
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * El historial de consultas DNS del teléfono (D8): qué dominios se pidieron cada día y si se bloquearon, durante 30 días. Se apunta aquí,
 * en el teléfono, y solo lo ve el usuario; el compañero recibe únicamente cuántas se bloquearon y cuántas se permitieron cada día
 * ([report]). El servidor DNS no guarda historial. Las consultas se cuentan en memoria y se vuelcan al disco cada pocos segundos.
 */
class DnsQueryLog(
    private val dao: DnsQueryDao,
    private val clock: TrustedClock,
    private val settings: SettingsRepository,
    private val partnerEvents: PartnerEventOutbox,
) {
    private val tally = DnsTally()

    @Volatile private var purgedOn: Long? = null

    /** Apunta una consulta con su respuesta. Lo que no contestó nadie no cuenta, y un nombre que no se puede leer, tampoco. */
    fun record(query: ByteArray, answer: DnsAnswer) {
        if (answer.source == DnsAnswer.Source.FAILED) return
        val host = DnsMessages.questionName(query) ?: return
        // Solo la respuesta del resolvedor filtra: la del sistema (si el nuestro no contestó) cuenta como permitida.
        val blocked = answer.source == DnsAnswer.Source.FILTERED && DnsMessages.isBlocked(answer.bytes)
        tally.add(host, blocked, clock.nowMs())
    }

    /** Pasa lo contado al disco y borra lo que pasa de 30 días (una vez al día). */
    suspend fun flush() {
        val counts = tally.drain()
        try {
            if (counts.isNotEmpty()) dao.addAll(counts.map { DnsQueryEntity(dayOf(it.lastMs), it.host, it.blocked, it.count, it.lastMs) })
            val today = today()
            if (purgedOn != today) {
                dao.purgeBefore(DnsLogPolicy.firstDayToKeep(today))
                purgedOn = today
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un fallo del disco no tumba la app ni el filtro: lo contado vuelve a la memoria y entra en el siguiente volcado.
            tally.restore(counts)
        }
    }

    val days: Flow<List<DnsDay>> get() = dao.observeDays()

    fun hosts(day: Long, blocked: Boolean, limit: Int = MAX_HOSTS): Flow<List<DnsQueryEntity>> = dao.observeHosts(day, blocked, limit)

    suspend fun clear() {
        tally.drain()
        dao.clear()
    }

    fun today(): Long = dayOf(clock.nowMs())

    /** Cuenta al compañero cuántas consultas hubo ayer, si aún no se contó: solo los dos totales, nunca los dominios. */
    suspend fun report() {
        // Sin compañero activo y con los avisos encendidos no sale nada: el día no se da por contado, para que se cuente cuando lo haya.
        if (!partnerEvents.accepting) return
        try {
            // Lo de ayer ya está en el disco: la VPN vuelca cada 30 s y al parar. Volcar aquí competiría con ella.
            val day = today() - 1
            if (!DnsSummary.due(day, settings.lastDnsSummaryDay)) return
            val label = DnsSummary.label(day, settings.lastDnsSummaryDay, dao.total(day, blocked = true), dao.total(day, blocked = false)) ?: return
            // Se anota antes de encolar: si el proceso muere entre las dos cosas, un día sin contar es mejor que contarlo doble.
            settings.setLastDnsSummaryDay(day)
            partnerEvents.record(PartnerEventType.DNS_COUNTS, label)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Es un extra del informe diario: un fallo suyo no puede frenar los avisos al compañero.
        }
    }

    /** El día lógico (con el inicio del día de los ajustes): el mismo que usan las rachas, el diario y las estadísticas. */
    private fun dayOf(ms: Long): Long = Streaks.dayOf(clock.localAt(Instant.ofEpochMilli(ms)), settings.dayStartMinute.value).toEpochDay()

    private companion object {
        const val MAX_HOSTS = 200
    }
}
