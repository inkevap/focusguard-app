package com.focusguard.data

import android.content.Context
import com.focusguard.server.ApiResult
import com.focusguard.server.PartnerInfo
import com.focusguard.server.PartnerRemoval
import com.focusguard.server.map
import com.focusguard.server.ServerApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

sealed interface PartnerState {
    data object None : PartnerState

    /** Invitado, pero aún no aceptó desde su correo. */
    data class Invited(val id: String, val email: String) : PartnerState

    data class Active(val id: String, val email: String) : PartnerState
}

/** Almacén mínimo del compañero: lo que hace falta para decidir sin conexión (por ejemplo, si cerrar sesión pide su permiso). */
interface PartnerStore {
    fun read(): PartnerState

    fun write(state: PartnerState)

    /** Todos los compañeros que dijo el servidor (hasta [PartnerRepository.MAX_PARTNERS]); un almacén que solo guarda el principal devuelve vacío. */
    fun readAll(): List<PartnerInfo> = emptyList()

    fun writeAll(list: List<PartnerInfo>) = Unit
}

class PrefsPartnerStore(context: Context) : PartnerStore {
    private val prefs = context.getSharedPreferences("partner", Context.MODE_PRIVATE)

    override fun read(): PartnerState = runCatching {
        val json = JSONObject(prefs.getString(KEY, null) ?: return PartnerState.None)
        val id = json.getString("id")
        val email = json.getString("email")
        if (json.getBoolean("accepted")) PartnerState.Active(id, email) else PartnerState.Invited(id, email)
    }.getOrDefault(PartnerState.None)

    override fun write(state: PartnerState) {
        val json = when (state) {
            PartnerState.None -> null
            is PartnerState.Invited -> JSONObject().put("id", state.id).put("email", state.email).put("accepted", false)
            is PartnerState.Active -> JSONObject().put("id", state.id).put("email", state.email).put("accepted", true)
        }
        prefs.edit().apply { if (json == null) remove(KEY) else putString(KEY, json.toString()) }.apply()
    }

    override fun readAll(): List<PartnerInfo> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ALL, null) ?: return emptyList())
        (0 until array.length()).map { array.getJSONObject(it).let { o -> PartnerInfo(o.getString("id"), o.getString("email"), o.getBoolean("accepted")) } }
    }.getOrDefault(emptyList())

    override fun writeAll(list: List<PartnerInfo>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("id", it.id).put("email", it.email).put("accepted", it.accepted)) }
        prefs.edit().apply { if (list.isEmpty()) remove(KEY_ALL) else putString(KEY_ALL, array.toString()) }.apply()
    }

    private companion object {
        const val KEY = "state"
        const val KEY_ALL = "all"
    }
}

/**
 * Compañero de responsabilidad (I1, I4, I5). Se guarda en el teléfono lo último que dijo el servidor, porque hay decisiones
 * que no pueden esperar a la red (cerrar la sesión con un compañero activo pide su permiso aunque no haya conexión).
 */
class PartnerRepository(private val api: ServerApi, private val store: PartnerStore, private val onNoActivePartner: () -> Unit = {},
    /** El compañero acaba de aceptar: a partir de ahí se le cuenta lo que pasa. */
    private val onActivated: () -> Unit = {},
) {
    private val _state = MutableStateFlow(store.read())

    /** El compañero que cuenta para decidir (el primero que aceptó; si nadie aceptó, el primero invitado). Con varios, basta con que uno apruebe. */
    val state: StateFlow<PartnerState> = _state.asStateFlow()

    private val _partners = MutableStateFlow(store.readAll().ifEmpty { listOfNotNull(store.read().toInfo()) })

    /** Todos mis compañeros, aceptados o con la invitación pendiente. */
    val partners: StateFlow<List<PartnerInfo>> = _partners.asStateFlow()

    val isActive: Boolean get() = _state.value is PartnerState.Active

    private fun set(value: PartnerState) {
        if (value == _state.value) return
        store.write(value)
        _state.value = value
    }

    /** Guarda la lista y deja como principal al primero que aceptó (o, si ninguno, al primero invitado). */
    private fun setAll(list: List<PartnerInfo>) {
        val kept = list.take(MAX_PARTNERS)
        if (kept != _partners.value) {
            store.writeAll(kept)
            _partners.value = kept
        }
        set((kept.firstOrNull { it.accepted } ?: kept.firstOrNull())?.toState() ?: PartnerState.None)
    }

    /** Pregunta al servidor quién es el compañero. Sin conexión se conserva lo último que se supo. */
    suspend fun refresh(): ApiResult<Unit> = when (val result = api.partners()) {
        is ApiResult.Ok -> {
            val wasActive = isActive
            setAll(result.value)
            val state = _state.value
            if (state !is PartnerState.Active) onNoActivePartner() else if (!wasActive) onActivated()
            ApiResult.Ok(Unit)
        }
        // Con cualquier fallo (red, sesión caducada) se conserva lo último que se supo: es lo más estricto, y entrar de nuevo lo corrige.
        is ApiResult.Failure -> result
    }

    /** Como [refresh], y dice si el compañero acaba de pasar a activo con esta consulta (para avisar al usuario, que no mira la app). */
    suspend fun refreshReportingActivation(): Boolean {
        val wasActive = isActive
        refresh()
        return !wasActive && isActive
    }

    /** Invitar a otro compañero (hasta [MAX_PARTNERS]); no cambia al principal si ya hay uno que aceptó. */
    suspend fun invite(email: String): ApiResult<Unit> = api.invitePartner(email).map { setAll(_partners.value.filterNot { p -> p.id == it.id } + it) }

    /** Cancelar una invitación es libre; quitar a quien ya aceptó pide su aprobación (devuelve el id de esa solicitud). */
    suspend fun remove(id: String? = null): ApiResult<PartnerRemoval> {
        val target = id ?: when (val s = _state.value) {
            PartnerState.None -> return ApiResult.Ok(PartnerRemoval.Done)
            is PartnerState.Invited -> s.id
            is PartnerState.Active -> s.id
        }
        val result = api.removePartner(target)
        if (result is ApiResult.Ok && result.value == PartnerRemoval.Done) setAll(_partners.value.filterNot { it.id == target })
        return result
    }

    /** Al cerrar la sesión los compañeros dejan de contar en este teléfono. */
    fun clear() {
        setAll(emptyList())
        set(PartnerState.None)
    }

    private fun PartnerInfo.toState(): PartnerState = if (accepted) PartnerState.Active(id, email) else PartnerState.Invited(id, email)

    companion object {
        /** Cuántos compañeros (aceptados o invitados) puede tener una cuenta: lo mismo que el servidor. */
        const val MAX_PARTNERS = 3
    }
}

private fun PartnerState.toInfo(): PartnerInfo? = when (this) {
    PartnerState.None -> null
    is PartnerState.Invited -> PartnerInfo(id, email, accepted = false)
    is PartnerState.Active -> PartnerInfo(id, email, accepted = true)
}
