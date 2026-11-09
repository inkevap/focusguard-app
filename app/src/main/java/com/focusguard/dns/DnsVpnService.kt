package com.focusguard.dns

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.focusguard.data.DnsQueryLog
import com.focusguard.data.PartnerEventOutbox
import com.focusguard.data.SettingsRepository
import com.focusguard.data.TrustedClock
import com.focusguard.model.PartnerEventType
import com.focusguard.server.DnsConfig
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * VPN local que solo captura el tráfico DNS (D5): la ruta de la VPN es una única dirección (la del «servidor DNS» que se anuncia al
 * sistema), así que nada más pasa por aquí. Este servicio solo mueve bytes entre el túnel y [DnsProxy], que decide la respuesta
 * (resolvedor del servidor, DNS del sistema o fallo; ver [DnsResolver]).
 *
 * La propia app queda fuera de la VPN: así llega al resolvedor sin pasar por su propio filtro.
 */
class DnsVpnService : VpnService(), KoinComponent {
    private val filter: DnsFilter by inject()
    private val settings: SettingsRepository by inject()
    private val doh: DohClient by inject()
    private val clock: TrustedClock by inject()
    private val partnerEvents: PartnerEventOutbox by inject()
    private val queryLog: DnsQueryLog by inject()

    // Una consulta que falla de forma inesperada no puede tirar la app: se pierde esa consulta y la aplicación reintenta.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, _ -> })

    /** La sesión en marcha: montar el túnel y servirlo. Cancelarla lo cierra todo. */
    private var session: Job? = null

    /** El túnel de la sesión: cancelar la corrutina no interrumpe la lectura bloqueada, hay que cerrarlo. */
    @Volatile
    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> stopSession()
        }
        if (session?.isActive != true) session = scope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val config = filter.config()
        if (config == null) {
            filter.setStatus(DnsStatus.Error("No hay resolvedor DNS: entra en tu cuenta para configurarlo."))
            stopSelf()
            return
        }
        val established = try {
            build().establish()
        } catch (e: IllegalStateException) {
            null
        } catch (e: SecurityException) {
            null
        }
        if (established == null) {
            filter.setStatus(DnsStatus.Error("Falta el permiso de VPN."))
            stopSelf()
            return
        }
        tun = established
        filter.setStatus(DnsStatus.Running)
        val mine = currentCoroutineContext()[Job]
        try {
            established.use { serve(it, config) }
        } finally {
            // Si ya arrancó otra sesión (un reinicio), el estado es el suyo: este final no lo pisa.
            if (session == null || session === mine) filter.setStatus(DnsStatus.Stopped)
        }
    }

    private fun build(): Builder {
        val builder = Builder()
            .setSession("FocusGuard")
            .setMtu(MTU)
            .addAddress(CLIENT_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            // Solo esta dirección entra en la VPN: el resto del tráfico sigue su camino normal.
            .addRoute(DNS_ADDRESS, 32)
            .setBlocking(true)
        runCatching { builder.addDisallowedApplication(packageName) }
        for (app in settings.dnsExcludedApps.value) runCatching { builder.addDisallowedApplication(app) }
        return builder
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun serve(tun: ParcelFileDescriptor, config: DnsConfig) {
        val input = FileInputStream(tun.fileDescriptor)
        val output = FileOutputStream(tun.fileDescriptor)
        val proxy = DnsProxy(
            DnsResolver(
                primary = { query -> doh.resolve(config.dohUrl, query) },
                fallback = if (config.failOpen) UpstreamResolver(::systemResolve) else null,
                nowMs = clock::nowMs,
            ),
            onAnswer = { source ->
                filter.report(source)
                watchFailOpen(source)
            },
            onQuery = queryLog::record,
        )
        // Las consultas esperan red: con un tope propio no le quitan hilos al resto de la app.
        val workers = Dispatchers.IO.limitedParallelism(MAX_IN_FLIGHT)
        val inFlight = Semaphore(MAX_IN_FLIGHT)
        val buffer = ByteArray(MTU)
        // El historial de consultas se vuelca al disco cada poco, no en cada consulta.
        val flusher = scope.launch {
            while (true) {
                delay(LOG_FLUSH_MS)
                queryLog.flush()
            }
        }
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                val request = proxy.accept(buffer, read) ?: continue
                // Más consultas a la vez de las que caben se descartan: la aplicación que preguntó reintenta.
                if (!inFlight.tryAcquire()) continue
                scope.launch(workers) {
                    try {
                        val reply = proxy.respond(request)
                        synchronized(output) { reply.forEach(output::write) }
                    } catch (e: IOException) {
                        // El túnel se cerró mientras se contestaba.
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Algo inesperado en esta consulta: se pierde ella sola.
                    } finally {
                        inFlight.release()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // El sistema cerró el túnel (otra VPN, o el usuario la apagó).
        } finally {
            flusher.cancel()
            withContext(NonCancellable) { queryLog.flush() }
        }
    }

    /** La consulta por UDP al DNS de la red de verdad (no la VPN), con el socket fuera de la VPN. */
    private suspend fun systemResolve(query: ByteArray): ByteArray? {
        val server = underlyingDns() ?: return null
        return withTimeoutOrNull(SYSTEM_TIMEOUT_MS) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = SYSTEM_TIMEOUT_MS.toInt()
                    socket.connect(InetSocketAddress(server, UdpPackets.DNS_PORT))
                    socket.send(DatagramPacket(query, query.size))
                    val packet = DatagramPacket(ByteArray(MTU), MTU)
                    socket.receive(packet)
                    packet.data.copyOf(packet.length).takeIf { DnsMessages.answers(query, it) }
                }
            } catch (e: IOException) {
                null
            }
        }
    }

    @Volatile
    private var dnsCache: Pair<Long, InetAddress>? = null

    /**
     * El DNS de la red de verdad: la que tiene internet y funciona, y entre varias la que Android prefiere (Wi-Fi o cable antes que móvil).
     * Se recuerda unos segundos —pedirlo son varias llamadas al sistema y durante una caída se necesita en cada consulta—, pero solo si se
     * encontró: sin red no se guarda un «no hay».
     */
    private fun underlyingDns(): InetAddress? {
        val nowMs = clock.nowMs()
        dnsCache?.takeIf { nowMs - it.first < DNS_CACHE_MS }?.let { return it.second }
        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val server = connectivity.allNetworks.mapNotNull { network ->
            val caps = connectivity.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            val dns = connectivity.getLinkProperties(network)?.dnsServers?.firstOrNull() ?: return@mapNotNull null
            val rank = (if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) 0 else 2) + (if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) 1 else 0)
            rank to dns
        }.minByOrNull { it.first }?.second
        if (server != null) dnsCache = nowMs to server
        return server
    }

    @Volatile
    private var failOpenSinceMs: Long? = null

    @Volatile
    private var failOpenReported = false

    /** Si el filtro lleva mucho rato sin filtrar (el resolvedor no contesta), el compañero lo sabe, una vez por episodio. */
    private fun watchFailOpen(source: DnsAnswer.Source) {
        if (source == DnsAnswer.Source.FILTERED) {
            failOpenSinceMs = null
            failOpenReported = false
            return
        }
        val nowMs = clock.nowMs()
        val since = failOpenSinceMs ?: nowMs.also { failOpenSinceMs = it }
        if (!failOpenReported && nowMs - since >= FAIL_OPEN_ALERT_MS) {
            failOpenReported = true
            partnerEvents.record(PartnerEventType.PROTECTION_OFF, "filtro DNS sin resolvedor")
        }
    }

    private fun stopSession() {
        session?.cancel()
        session = null
        runCatching { tun?.close() }
        tun = null
    }

    /** El usuario (u otra VPN) quitó esta VPN: sin ella el filtro no funciona, y el compañero lo sabe. */
    override fun onRevoke() {
        stopSession()
        partnerEvents.record(PartnerEventType.PROTECTION_OFF, "filtro DNS")
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Cada cuánto se vuelca el historial de consultas al disco. */
        private const val LOG_FLUSH_MS = 30_000L

        const val ACTION_STOP = "com.focusguard.dns.STOP"
        const val ACTION_RESTART = "com.focusguard.dns.RESTART"
        private const val MTU = 1500
        private const val CLIENT_ADDRESS = "10.111.222.1"
        private const val DNS_ADDRESS = "10.111.222.2"
        private const val MAX_IN_FLIGHT = 16
        private const val SYSTEM_TIMEOUT_MS = 3_000L
        private const val DNS_CACHE_MS = 30_000L
        private const val FAIL_OPEN_ALERT_MS = 15 * 60_000L
    }
}
