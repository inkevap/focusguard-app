package com.focusguard.data

import android.content.Context
import com.focusguard.decision.PartnerEvents
import com.focusguard.model.PartnerEvent
import com.focusguard.model.PartnerEventType
import com.focusguard.server.AccountStore
import com.focusguard.server.ApiResult
import com.focusguard.server.ServerApi
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** Cola de sucesos pendientes de enviar; sobrevive a que se cierre la app. */
interface EventStore {
    fun read(): List<PartnerEvent>

    fun write(events: List<PartnerEvent>)
}

class PrefsEventStore(context: Context) : EventStore {
    private val prefs = context.getSharedPreferences("partner_events", Context.MODE_PRIVATE)

    override fun read(): List<PartnerEvent> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val json = array.getJSONObject(i)
            val type = PartnerEventType.entries.firstOrNull { it.name == json.getString("type") } ?: return@mapNotNull null
            PartnerEvent(type, Instant.ofEpochMilli(json.getLong("at")), json.optString("label").ifBlank { null })
        }
    }.getOrDefault(emptyList())

    override fun write(events: List<PartnerEvent>) {
        val array = JSONArray()
        events.forEach { array.put(JSONObject().put("type", it.type.name).put("at", it.at.toEpochMilli()).putOpt("label", it.label)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "queue"
    }
}

/**
 * Avisos al compañero (I4) y señal de vida (H11). Los sucesos se guardan en el teléfono y se envían en lotes; los graves piden un envío
 * inmediato. Solo se guardan si hay un compañero activo y el usuario no apagó los avisos: sin eso no sale nada del teléfono.
 */
class PartnerEventOutbox(
    private val api: ServerApi,
    private val store: EventStore,
    private val account: AccountStore,
    private val partner: PartnerRepository,
    /** Si el usuario tiene encendidos los avisos al compañero. */
    private val noticesOn: () -> Boolean,
    private val now: () -> Instant,
    /** Pide un envío cuanto antes (WorkManager); los bloqueos esperan al envío periódico. */
    private val requestFlush: () -> Unit,
    /** Qué avisos elige el usuario mandar (panel del compañero); el resto no sale del teléfono. */
    private val allows: (PartnerEventType) -> Boolean = { true },
) {
    private val flushLock = Mutex()
    private val queueLock = Any()

    /** La cola en memoria: se lee del disco una sola vez y solo se escribe si cambia. */
    private var queue: List<PartnerEvent>? = null

    private fun current(): List<PartnerEvent> = queue ?: store.read().also { queue = it }

    private fun save(events: List<PartnerEvent>) {
        if (events == queue) return
        queue = events
        store.write(events)
    }

    /** Hay compañero activo y los avisos están encendidos: solo entonces algo sale del teléfono. */
    val accepting: Boolean get() = noticesOn() && partner.isActive

    fun record(type: PartnerEventType, label: String? = null) {
        if (!accepting || !allows(type)) return
        synchronized(queueLock) { save(PartnerEvents.enqueue(current(), PartnerEvent(type, now(), label))) }
        if (PartnerEvents.urgent(type)) requestFlush()
    }

    /** Envía lo pendiente de 50 en 50. Lo que no se pudo enviar por red o sesión se queda; lo que el servidor rechaza por mal formado se descarta. */
    suspend fun flush(): ApiResult<Unit> = flushLock.withLock { flushBatches() }

    private suspend fun flushBatches(): ApiResult<Unit> {
        // Lo guardado antes de apagar los avisos o de quitar al compañero ya no sale.
        if (!accepting) {
            clear()
            return ApiResult.Ok(Unit)
        }
        val deviceId = account.deviceId ?: return ApiResult.Ok(Unit)
        var rejected: ApiResult.Failure? = null
        while (true) {
            val batch = synchronized(queueLock) { PartnerEvents.nextBatch(current()) }
            if (batch.isEmpty()) return rejected ?: ApiResult.Ok(Unit)
            val result = api.sendEvents(deviceId, batch)
            // Red, sesión, servidor caído...: se queda todo para el siguiente envío. Solo un lote mal formado se descarta, para que no bloquee los demás.
            if (result is ApiResult.Failure && !result.error.rejectedAsInvalid) return result
            if (result is ApiResult.Failure && batch.size > 1) {
                // Un suceso que el servidor no conoce (una app más nueva que él) no debe llevarse por delante a los demás del lote: se reenvían de uno en uno.
                val (outcome, failure) = sendOneByOne(deviceId, batch)
                if (failure != null) rejected = rejected ?: failure
                if (outcome != null) return outcome
                continue
            }
            if (result is ApiResult.Failure && rejected == null) rejected = result
            remove(batch)
        }
    }

    /** Envía [batch] de uno en uno. Devuelve el error de red si lo hubo (lo no enviado se queda) y el primer rechazo por contenido. */
    private suspend fun sendOneByOne(deviceId: String, batch: List<PartnerEvent>): Pair<ApiResult.Failure?, ApiResult.Failure?> {
        var firstRejection: ApiResult.Failure? = null
        for (event in batch) {
            val single = api.sendEvents(deviceId, listOf(event))
            if (single is ApiResult.Failure) {
                if (!single.error.rejectedAsInvalid) return single to firstRejection
                firstRejection = firstRejection ?: single
            }
            remove(listOf(event))
        }
        return null to firstRejection
    }

    /** Se quitan uno a uno, no los primeros N: mientras se enviaba pudo salir de la cola algún suceso viejo. */
    private fun remove(sent: List<PartnerEvent>) =
        synchronized(queueLock) { save(current().toMutableList().also { list -> sent.forEach { list.remove(it) } }) }

    /** Al cerrar la sesión lo pendiente no se envía con otra cuenta. */
    fun clear() = synchronized(queueLock) { save(emptyList()) }

    /** Solo con un compañero que espera señales; no lleva datos, y sigue aunque los avisos estén apagados para no dar falsas alarmas. */
    suspend fun heartbeat(): ApiResult<Unit> {
        val deviceId = account.deviceId ?: return ApiResult.Ok(Unit)
        if (!partner.isActive) return ApiResult.Ok(Unit)
        return api.heartbeat(deviceId)
    }
}
