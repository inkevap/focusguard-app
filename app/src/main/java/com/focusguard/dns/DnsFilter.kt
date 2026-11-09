package com.focusguard.dns

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.focusguard.data.SettingsRepository
import com.focusguard.server.AccountStore
import com.focusguard.server.ApiResult
import com.focusguard.server.DnsConfig
import com.focusguard.server.ServerApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cómo va el filtro DNS. */
sealed interface DnsStatus {
    data object Stopped : DnsStatus

    data object Running : DnsStatus

    /** El resolvedor no contesta: las consultas se responden con el DNS del sistema (sin filtrar) o, si tampoco hay, con un fallo. */
    data object FailOpen : DnsStatus

    data class Error(val message: String) : DnsStatus
}

/** Dónde se guarda el último resolvedor conocido, para arrancar sin conexión. */
interface DnsConfigStore {
    fun read(): DnsConfig?

    fun write(config: DnsConfig)
}

class PrefsDnsConfigStore(context: Context) : DnsConfigStore {
    private val prefs = context.getSharedPreferences("dns", Context.MODE_PRIVATE)

    override fun read(): DnsConfig? = prefs.getString(KEY_URL, null)?.let { DnsConfig(it, prefs.getBoolean(KEY_FAIL_OPEN, true)) }

    override fun write(config: DnsConfig) {
        prefs.edit().putString(KEY_URL, config.dohUrl).putBoolean(KEY_FAIL_OPEN, config.failOpen).apply()
    }

    private companion object {
        const val KEY_URL = "doh_url"
        const val KEY_FAIL_OPEN = "fail_open"
    }
}

/**
 * Filtro DNS propio (D5): el estado y la configuración que usa la VPN local. La VPN solo captura las consultas DNS y las manda cifradas
 * al resolvedor del servidor, que rechaza los dominios de las listas y fuerza la búsqueda segura (B9).
 */
class DnsFilter(
    private val api: ServerApi,
    private val account: AccountStore,
    private val store: DnsConfigStore,
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val _status = MutableStateFlow<DnsStatus>(DnsStatus.Stopped)
    val status: StateFlow<DnsStatus> = _status.asStateFlow()

    fun setStatus(status: DnsStatus) {
        _status.value = status
    }

    /** De dónde vino la última respuesta: así se sabe si el teléfono está filtrando. Una VPN ya parada no cambia de estado. */
    fun report(source: DnsAnswer.Source) {
        if (_status.value is DnsStatus.Stopped) return
        _status.value = if (source == DnsAnswer.Source.FILTERED) DnsStatus.Running else DnsStatus.FailOpen
    }

    /** El resolvedor de este dispositivo: se pregunta al servidor y, sin conexión, vale el último que se supo. Sin cuenta no hay. */
    suspend fun config(): DnsConfig? {
        val deviceId = account.deviceId
        if (account.hasSession && deviceId != null) {
            val result = api.dnsConfig(deviceId)
            if (result is ApiResult.Ok && result.value.dohUrl.startsWith("https://")) {
                store.write(result.value)
                return result.value
            }
        }
        return store.read()
    }

    /** El permiso de VPN: `null` si ya está concedido; si no, la pantalla del sistema que lo pide. */
    fun permissionIntent(): Intent? = VpnService.prepare(context)

    /** Arranca la VPN si el usuario la tiene encendida y el permiso ya está dado. Puede llamarse desde cualquier sitio. */
    fun ensureRunning() {
        if (!settings.dnsFilterEnabled.value || permissionIntent() != null) return
        runCatching { context.startService(Intent(context, DnsVpnService::class.java)) }
    }

    /** Guarda el ajuste y deja la VPN como pide: encendida (si el permiso ya está) o parada. */
    fun setEnabled(enabled: Boolean) {
        settings.setDnsFilterEnabled(enabled)
        if (enabled) ensureRunning() else send(DnsVpnService.ACTION_STOP)
    }

    /** Vuelve a montar la VPN con la configuración nueva (las apps que quedan fuera), si está encendida. */
    fun restart() {
        if (settings.dnsFilterEnabled.value && permissionIntent() == null) send(DnsVpnService.ACTION_RESTART)
    }

    private fun send(action: String) {
        runCatching { context.startService(Intent(context, DnsVpnService::class.java).setAction(action)) }
    }
}
