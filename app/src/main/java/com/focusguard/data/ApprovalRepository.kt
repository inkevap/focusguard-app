package com.focusguard.data

import android.content.Context
import com.focusguard.decision.Approvals
import com.focusguard.model.ApprovalMode
import com.focusguard.model.ApprovalRequest
import com.focusguard.model.RemoteStatus
import java.security.SecureRandom
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Modo de aprobación elegido y solicitudes pendientes, en preferencias privadas. */
class ApprovalRepository(context: Context, private val clock: TrustedClock) : PartnerRequestStore {

    private val prefs = context.getSharedPreferences("approvals", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    private val _mode = MutableStateFlow(
        runCatching { ApprovalMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }.getOrDefault(ApprovalMode.NONE),
    )
    val mode: StateFlow<ApprovalMode> = _mode.asStateFlow()

    private val _pending = MutableStateFlow(readPending())
    override val pending: StateFlow<List<ApprovalRequest>> = _pending.asStateFlow()

    /**
     * Sin compañero activo el modo «compañero» no se puede cumplir y bloquearía todo cambio para siempre: se pasa a la espera
     * más larga. Quitar al compañero ya exigió su aprobación; cambiar de cuenta o perder la invitación no.
     */
    fun partnerGone() {
        if (_mode.value == ApprovalMode.PARTNER) setMode(ApprovalMode.DELAY_72H)
    }

    fun setMode(mode: ApprovalMode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _mode.value = mode
    }

    /** Pasa al compañero las solicitudes pendientes que se aprobaban por otra vía (nueva espera, como toda solicitud distinta). */
    fun moveAllToPartner() {
        _pending.value.filter { it.mode != ApprovalMode.PARTNER }.forEach { create(it.actionKey, it.actionId, it.payload, ApprovalMode.PARTNER) }
    }

    /**
     * Crea la solicitud, o actualiza la que ya exista para la misma acción. Con [mode] se fuerza cómo se aprueba (el
     * compañero, por ejemplo) aunque el modo elegido por el usuario sea otro.
     */
    fun create(actionKey: String, actionId: Int, payload: String? = null, mode: ApprovalMode? = null, holdByMode: Boolean = false): ApprovalRequest {
        // Solo se reutiliza una solicitud idéntica; si cambia el contenido empieza de cero (nueva espera).
        val request = Approvals.reusable(_pending.value, actionKey, payload)?.takeIf { mode == null || it.mode == mode } ?: ApprovalRequest(
            id = prefs.getLong(KEY_NEXT_ID, 1),
            actionKey = actionKey,
            actionId = actionId,
            mode = mode ?: _mode.value,
            createdAt = clock.now(),
            phraseIndex = random.nextInt(1_000),
            payload = payload,
            // La espera elegida (24 h, 72 h) corre antes de mandarle el correo al compañero, no después.
            sendAfter = if (holdByMode) clock.now().let { Approvals.partnerSendAfter(_mode.value, it) } else null,
        ).also { prefs.edit().putLong(KEY_NEXT_ID, it.id + 1).apply() }
        write(_pending.value.filterNot { it.actionKey == actionKey } + request)
        return request
    }

    fun setBaseline(id: Long, baseline: Long) =
        write(_pending.value.map { if (it.id == id) it.copy(stepsBaseline = baseline) else it })

    /** Anota lo que el servidor dice de la solicitud que se mandó al compañero. */
    override fun setRemote(id: Long, remoteId: String?, status: RemoteStatus) =
        write(_pending.value.map { if (it.id == id) it.copy(remoteId = remoteId ?: it.remoteId, remote = status) else it })

    override fun remove(id: Long) = write(_pending.value.filterNot { it.id == id })

    private fun write(list: List<ApprovalRequest>) {
        prefs.edit().putString(KEY_PENDING, JSONArray().also { array -> list.forEach { array.put(it.toJson()) } }.toString()).apply()
        _pending.value = list
    }

    private fun readPending(): List<ApprovalRequest> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PENDING, "[]"))
        (0 until array.length()).map { requestFromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    private fun ApprovalRequest.toJson() = JSONObject().apply {
        put("id", id); put("key", actionKey); put("actionId", actionId); put("mode", mode.name)
        put("createdAtMs", createdAt.toEpochMilli()); put("phraseIndex", phraseIndex); put("stepsNeeded", stepsNeeded)
        payload?.let { put("payload", it) }
        stepsBaseline?.let { put("baseline", it) }
        remoteId?.let { put("remoteId", it) }
        put("remote", remote.name)
        sendAfter?.let { put("sendAfterMs", it.toEpochMilli()) }
    }

    private fun requestFromJson(json: JSONObject) = ApprovalRequest(
        id = json.getLong("id"),
        actionKey = json.getString("key"),
        actionId = json.getInt("actionId"),
        mode = ApprovalMode.valueOf(json.getString("mode")),
        createdAt = Instant.ofEpochMilli(json.getLong("createdAtMs")),
        phraseIndex = json.getInt("phraseIndex"),
        stepsNeeded = json.getInt("stepsNeeded"),
        payload = json.optString("payload").takeIf { json.has("payload") },
        stepsBaseline = if (json.has("baseline")) json.getLong("baseline") else null,
        remoteId = json.optString("remoteId").takeIf { json.has("remoteId") },
        remote = runCatching { RemoteStatus.valueOf(json.optString("remote")) }.getOrDefault(RemoteStatus.NOT_SENT),
        sendAfter = if (json.has("sendAfterMs")) Instant.ofEpochMilli(json.getLong("sendAfterMs")) else null,
    )

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_PENDING = "pending"
        const val KEY_NEXT_ID = "next_id"
    }
}
